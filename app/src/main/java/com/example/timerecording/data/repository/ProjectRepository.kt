package com.example.timerecording.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.timerecording.Project
import com.example.timerecording.Session
import com.example.timerecording.data.DataMigration
import com.example.timerecording.data.backup.BackupManager
import com.example.timerecording.data.backup.ExportPayload
import com.example.timerecording.data.backup.ImportPlan
import com.example.timerecording.data.backup.ImportSummary
import com.example.timerecording.data.db.AppDatabase
import com.example.timerecording.data.entity.ProjectEntity
import com.example.timerecording.data.entity.SessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 项目数据仓库，统一封装对数据库的访问，是 ViewModel 与数据层之间的唯一桥梁。
 *
 * 职责包括：
 * - 项目的增删改查
 * - 计时器状态管理（开始/暂停/恢复/结束）
 * - 跨天会话自动拆分
 * - 运行中计时器恢复（应用重启后）
 * - 数据导入导出
 *
 * 通过单例模式提供全局唯一实例。
 */
class ProjectRepository private constructor(
    private val context: Context,
    private val db: AppDatabase
) {
    private val projectDao = db.projectDao()  // 项目表 DAO
    private val sessionDao = db.sessionDao()  // 会话表 DAO
    private val crossDayMutex = Mutex()  // 跨天拆分互斥锁，防止多协程并发重复插入会话

    /** 响应式项目数据流，数据变化时自动推送，自动转换为 UI 模型 */
    val projectsFlow: Flow<List<Project>> = projectDao.observeProjectsWithSessions()
        .map { list -> list.map { it.toUiModel() } }

    /**
     * 初始化块：在 IO 协程中执行一次性启动任务。
     * 1. 迁移旧版 SharedPreferences 数据到 Room
     * 2. 拆分跨天的计时会话
     * 3. 恢复应用重启前处于运行状态的计时器
     */
    init {
        CoroutineScope(Dispatchers.IO).launch {
            DataMigration.migrateIfNeeded(context, projectDao, sessionDao)
            checkAndSplitCrossDaySessions()
            recoverRunningTimers()
        }
    }

    /**
     * 恢复运行中的计时器。
     * 应用被杀后重新启动时，将之前 running 状态的项目时间补上，
     * 把 currentStartTime 更新为当前时间，并把离线期间流逝的时间累加到 currentElapsedMillis。
     */
    private suspend fun recoverRunningTimers() {
        val running = projectDao.getRunningProjects()
        for (p in running) {
            val elapsed = System.currentTimeMillis() - p.currentStartTime
            if (elapsed > 0) {
                projectDao.updateTimerState(
                    projectId = p.id,
                    state = "running",
                    sessionStartTime = p.sessionStartTime,
                    currentStartTime = System.currentTimeMillis(),
                    currentElapsedMillis = p.currentElapsedMillis + elapsed
                )
            }
        }
    }

    /** 获取所有项目（含会话），转换为 UI 模型返回 */
    suspend fun getAllProjects(): List<Project> {
        return projectDao.getProjectsWithSessions().map { it.toUiModel() }
    }

    /** 根据 id 获取单个项目（含会话），不存在则返回 null */
    suspend fun getProject(projectId: Long): Project? {
        return projectDao.getProjectWithSessions(projectId)?.toUiModel()
    }

    /** 新增项目 */
    suspend fun addProject(name: String) {
        projectDao.insert(ProjectEntity(name = name))
    }

    /**
     * 开始计时：将项目状态设为 running，记录会话开始时间和当前计时起点，
     * 累计时长清零。
     */
    suspend fun startTimer(projectId: Long, sessionStartTime: Long) {
        projectDao.updateTimerState(
            projectId = projectId,
            state = "running",
            sessionStartTime = sessionStartTime,
            currentStartTime = System.currentTimeMillis(),
            currentElapsedMillis = 0
        )
    }

    /**
     * 暂停计时：将项目状态设为 paused，保存当前累计时长。
     * currentStartTime 记录暂停时刻，用于跨天拆分判断。
     */
    suspend fun pauseTimer(projectId: Long, sessionStartTime: Long, currentElapsedMillis: Long) {
        projectDao.updateTimerState(
            projectId = projectId,
            state = "paused",
            sessionStartTime = sessionStartTime,
            currentStartTime = System.currentTimeMillis(),
            currentElapsedMillis = currentElapsedMillis
        )
    }

    /**
     * 恢复计时：从暂停状态恢复为 running，以当前时间作为新的计时起点，
     * 保留之前已累计的时长。
     */
    suspend fun continueTimer(projectId: Long, sessionStartTime: Long, currentElapsedMillis: Long) {
        projectDao.updateTimerState(
            projectId = projectId,
            state = "running",
            sessionStartTime = sessionStartTime,
            currentStartTime = System.currentTimeMillis(),
            currentElapsedMillis = currentElapsedMillis
        )
    }

    /**
     * 结束计时：将当前会话保存为历史记录。
     *
     * 若累计时长不足 1 分钟，视为无效会话，直接重置不保存，返回 false。
     * 否则将会话写入 sessions 表并重置计时器状态，返回 true。
     */
    suspend fun endTimer(
        projectId: Long,
        sessionStartTime: Long,
        currentElapsedMillis: Long,
        endTime: Long
    ): Boolean {
        // 不足 1 分钟的会话不保存
        if (currentElapsedMillis < 60_000) {
            projectDao.resetTimer(projectId)
            return false
        }
        // 将会话记录写入数据库
        sessionDao.insert(
            SessionEntity(
                projectId = projectId,
                startTime = sessionStartTime,
                endTime = endTime,
                durationMillis = currentElapsedMillis
            )
        )
        // 重置项目计时状态为空闲
        projectDao.resetTimer(projectId)
        return true
    }

    /**
     * 检查并拆分跨天会话。
     *
     * 遍历所有非空闲状态的项目，若会话开始时间不在今天：
     * - paused 状态：将昨天的计时保存为历史会话（满 1 分钟才保存），然后重置计时器
     * - running 状态：将昨天到午夜的时长保存为历史会话，然后从午夜零点开始新的计时片段
     * 确保每条会话记录只属于同一天。
     */
    suspend fun checkAndSplitCrossDaySessions() = crossDayMutex.withLock {
        val nonIdle = projectDao.getNonIdleProjects()
        val todayStart = getStartOfToday()

        for (p in nonIdle) {
            // 二次检查：如果 sessionStartTime 已在今天，说明已被其他协程拆分过
            if (isSameDay(p.sessionStartTime, todayStart)) continue

            when (p.state) {
                "paused" -> {
                    val pauseTime = p.currentStartTime
                    val duration = p.currentElapsedMillis
                    // 暂停状态下累计满 1 分钟才保存为历史会话
                    if (duration >= 60_000) {
                        sessionDao.insert(
                            SessionEntity(
                                projectId = p.id,
                                startTime = p.sessionStartTime,
                                endTime = pauseTime,
                                durationMillis = duration
                            )
                        )
                    }
                    // 重置计时器为空闲
                    projectDao.resetTimer(p.id)
                }
                "running" -> {
                    val midnight = todayStart
                    // 计算昨天部分（从开始计时到午夜）的时长
                    val yesterdayDuration = if (p.currentStartTime < midnight) {
                        p.currentElapsedMillis + (midnight - p.currentStartTime)
                    } else {
                        p.currentElapsedMillis
                    }

                    // 昨天部分满 1 分钟才保存为历史会话
                    if (yesterdayDuration >= 60_000) {
                        sessionDao.insert(
                            SessionEntity(
                                projectId = p.id,
                                startTime = p.sessionStartTime,
                                endTime = midnight,
                                durationMillis = yesterdayDuration
                            )
                        )
                    }

                    // 从午夜零点开始新的计时片段，累计时长清零
                    val newCurrentStartTime = if (p.currentStartTime < midnight) midnight else p.currentStartTime
                    projectDao.updateTimerState(
                        projectId = p.id,
                        state = "running",
                        sessionStartTime = midnight,
                        currentStartTime = newCurrentStartTime,
                        currentElapsedMillis = 0
                    )
                }
            }
        }
    }

    /** 获取今天零点的时间戳（毫秒） */
    private fun getStartOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 判断两个时间戳是否属于同一天 */
    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /** 删除项目（会话因外键级联也会被删除） */
    suspend fun deleteProject(projectId: Long) {
        projectDao.deleteById(projectId)
    }

    /** 重命名项目 */
    suspend fun renameProject(projectId: Long, newName: String) {
        projectDao.renameProject(projectId, newName)
    }

    /** 获取所有正在计时的项目名称列表，无则返回空列表 */
    suspend fun getRunningProjectNames(): List<String> {
        val running = projectDao.getRunningProjects()
        if (running.isEmpty()) return emptyList()
        return running.map { it.name }
    }

    // ------------------------------------------------------------ 数据导出

    /** 生成 JSON 备份内容，同时返回数据量统计供界面提示 */
    suspend fun exportJson(): ExportPayload {
        val projects = getAllProjects()
        return ExportPayload(
            content = BackupManager.toJson(projects, System.currentTimeMillis()),
            projectCount = projects.size,
            sessionCount = projects.sumOf { it.sessions.size }
        )
    }

    /** 生成 CSV 表格内容，同时返回数据量统计供界面提示 */
    suspend fun exportCsv(): ExportPayload {
        val projects = getAllProjects()
        return ExportPayload(
            content = BackupManager.toCsv(projects),
            projectCount = projects.size,
            sessionCount = projects.sumOf { it.sessions.size }
        )
    }

    // ------------------------------------------------------------ 数据导入

    /**
     * 统计导入结果但不写入数据库，用于导入前的确认提示。
     *
     * @param overwrite true 表示覆盖模式，此时不与现有数据比对，全部视为新增
     */
    suspend fun previewImport(plan: ImportPlan, overwrite: Boolean): ImportSummary {
        val existingProjects = if (overwrite) emptyList() else projectDao.getProjectsWithSessions()

        // 统计同名项目和新项目的数量
        val existingNames = existingProjects.map { it.project.name }.toSet()
        val namesInPlan = plan.projects.map { it.name }.distinct()
        val existingProjectCount = namesInPlan.count { it in existingNames }
        val newProjectCount = namesInPlan.size - existingProjectCount

        // 先按项目名收集现有会话的去重键
        val knownKeys = mutableMapOf<String, MutableSet<String>>()
        for (projectWithSessions in existingProjects) {
            knownKeys.getOrPut(projectWithSessions.project.name) { mutableSetOf() }
                .addAll(projectWithSessions.sessions.map { sessionKey(it) })
        }

        // 遍历计划中的会话，区分待导入和重复跳过
        var sessionsToImport = 0
        var skippedDuplicates = 0
        for (backupProject in plan.projects) {
            val keys = knownKeys.getOrPut(backupProject.name) { mutableSetOf() }
            for (session in backupProject.sessions) {
                if (keys.add(sessionKey(session.startTime, session.endTime, session.durationMillis))) {
                    sessionsToImport++
                } else {
                    skippedDuplicates++
                }
            }
        }

        return ImportSummary(
            newProjects = newProjectCount,
            existingProjects = existingProjectCount,
            importedSessions = sessionsToImport,
            skippedDuplicates = skippedDuplicates,
            invalidEntries = plan.invalidEntries
        )
    }

    /**
     * 在单个事务内执行导入。任一步骤失败时整体回滚，现有数据不受影响。
     *
     * 合并模式下按项目名匹配已有项目，同名项目追加会话；
     * 会话以「开始时间 + 结束时间 + 时长」三要素严格判重，重复条目跳过。
     * 导入产生的项目一律置为 idle，不携带备份文件之外的计时状态。
     */
    suspend fun applyImport(plan: ImportPlan, overwrite: Boolean): ImportSummary = db.withTransaction {
        // 覆盖模式：先清空所有数据
        if (overwrite) {
            sessionDao.deleteAll()
            projectDao.deleteAll()
        }

        // 构建项目名 -> id 的映射，以及项目 id -> 已有会话去重键集合的映射
        val projectIdByName = mutableMapOf<String, Long>()
        val keysByProjectId = mutableMapOf<Long, MutableSet<String>>()

        for (projectWithSessions in projectDao.getProjectsWithSessions()) {
            val projectId = projectWithSessions.project.id
            projectIdByName[projectWithSessions.project.name] = projectId
            keysByProjectId[projectId] = projectWithSessions.sessions
                .map { sessionKey(it) }
                .toMutableSet()
        }

        // 记录导入前已有的项目名集合，用于统计
        val preExistingNames = projectIdByName.keys.toSet()

        var importedSessions = 0
        var skippedDuplicates = 0

        // 逐个处理备份中的项目及其会话
        for (backupProject in plan.projects) {
            // 同名项目复用已有 id，否则新建项目
            val projectId = projectIdByName[backupProject.name] ?: run {
                val newId = projectDao.insert(ProjectEntity(name = backupProject.name))
                projectIdByName[backupProject.name] = newId
                keysByProjectId[newId] = mutableSetOf()
                newId
            }

            val keys = keysByProjectId.getOrPut(projectId) { mutableSetOf() }

            // 逐条导入会话，去重跳过
            for (session in backupProject.sessions) {
                if (!keys.add(sessionKey(session.startTime, session.endTime, session.durationMillis))) {
                    skippedDuplicates++
                    continue
                }
                sessionDao.insert(
                    SessionEntity(
                        projectId = projectId,
                        startTime = session.startTime,
                        endTime = session.endTime,
                        durationMillis = session.durationMillis
                    )
                )
                importedSessions++
            }
        }

        // 统计新项目数与已有项目数
        val namesInPlan = plan.projects.map { it.name }.distinct()
        val existingProjectCount = if (overwrite) 0 else namesInPlan.count { it in preExistingNames }

        ImportSummary(
            newProjects = namesInPlan.size - existingProjectCount,
            existingProjects = existingProjectCount,
            importedSessions = importedSessions,
            skippedDuplicates = skippedDuplicates,
            invalidEntries = plan.invalidEntries
        )
    }

    /** 生成会话去重键（实体版），委托给三参数重载 */
    private fun sessionKey(session: SessionEntity): String =
        sessionKey(session.startTime, session.endTime, session.durationMillis)

    /** 生成会话去重键：开始时间 | 结束时间 | 时长，用于判断会话是否重复 */
    private fun sessionKey(startTime: Long, endTime: Long, durationMillis: Long): String =
        "$startTime|$endTime|$durationMillis"

    /**
     * 将 Room 关系模型 ProjectWithSessions 转换为 UI 模型 Project。
     * 同时把 SessionEntity 列表转换为 Session 列表。
     */
    private fun com.example.timerecording.data.entity.ProjectWithSessions.toUiModel(): Project {
        return Project(
            id = project.id,
            name = project.name,
            sessions = sessions.map {
                Session(
                    id = it.id,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    durationMillis = it.durationMillis
                )
            }.toMutableList(),
            state = project.state,
            sessionStartTime = project.sessionStartTime,
            currentStartTime = project.currentStartTime,
            currentElapsedMillis = project.currentElapsedMillis
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: ProjectRepository? = null  // 单例实例，@Volatile 保证多线程可见性

        /**
         * 获取仓库单例。
         * 使用双重检查锁确保线程安全地创建唯一实例。
         */
        fun get(context: Context): ProjectRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val db = AppDatabase.getDatabase(context)
                    ProjectRepository(context.applicationContext, db).also {
                        INSTANCE = it
                    }
                }
            }
        }
    }
}

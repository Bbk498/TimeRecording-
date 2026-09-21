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

class ProjectRepository private constructor(
    private val context: Context,
    private val db: AppDatabase
) {
    private val projectDao = db.projectDao()
    private val sessionDao = db.sessionDao()

    val projectsFlow: Flow<List<Project>> = projectDao.observeProjectsWithSessions()
        .map { list -> list.map { it.toUiModel() } }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            DataMigration.migrateIfNeeded(context, projectDao, sessionDao)
            checkAndSplitCrossDaySessions()
            recoverRunningTimers()
        }
    }

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

    suspend fun getAllProjects(): List<Project> {
        return projectDao.getProjectsWithSessions().map { it.toUiModel() }
    }

    suspend fun getProject(projectId: Long): Project? {
        return projectDao.getProjectWithSessions(projectId)?.toUiModel()
    }

    suspend fun addProject(name: String) {
        projectDao.insert(ProjectEntity(name = name))
    }

    suspend fun startTimer(projectId: Long, sessionStartTime: Long) {
        projectDao.updateTimerState(
            projectId = projectId,
            state = "running",
            sessionStartTime = sessionStartTime,
            currentStartTime = System.currentTimeMillis(),
            currentElapsedMillis = 0
        )
    }

    suspend fun pauseTimer(projectId: Long, sessionStartTime: Long, currentElapsedMillis: Long) {
        projectDao.updateTimerState(
            projectId = projectId,
            state = "paused",
            sessionStartTime = sessionStartTime,
            currentStartTime = System.currentTimeMillis(),
            currentElapsedMillis = currentElapsedMillis
        )
    }

    suspend fun continueTimer(projectId: Long, sessionStartTime: Long, currentElapsedMillis: Long) {
        projectDao.updateTimerState(
            projectId = projectId,
            state = "running",
            sessionStartTime = sessionStartTime,
            currentStartTime = System.currentTimeMillis(),
            currentElapsedMillis = currentElapsedMillis
        )
    }

    suspend fun endTimer(
        projectId: Long,
        sessionStartTime: Long,
        currentElapsedMillis: Long,
        endTime: Long
    ): Boolean {
        if (currentElapsedMillis < 60_000) {
            projectDao.resetTimer(projectId)
            return false
        }
        sessionDao.insert(
            SessionEntity(
                projectId = projectId,
                startTime = sessionStartTime,
                endTime = endTime,
                durationMillis = currentElapsedMillis
            )
        )
        projectDao.resetTimer(projectId)
        return true
    }

    suspend fun checkAndSplitCrossDaySessions() {
        val nonIdle = projectDao.getNonIdleProjects()
        val todayStart = getStartOfToday()

        for (p in nonIdle) {
            if (isSameDay(p.sessionStartTime, todayStart)) continue

            when (p.state) {
                "paused" -> {
                    val pauseTime = p.currentStartTime
                    val duration = p.currentElapsedMillis
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
                    projectDao.resetTimer(p.id)
                }
                "running" -> {
                    val midnight = todayStart
                    val yesterdayDuration = if (p.currentStartTime < midnight) {
                        p.currentElapsedMillis + (midnight - p.currentStartTime)
                    } else {
                        p.currentElapsedMillis
                    }

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

    private fun getStartOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    suspend fun deleteProject(projectId: Long) {
        projectDao.deleteById(projectId)
    }

    suspend fun renameProject(projectId: Long, newName: String) {
        projectDao.renameProject(projectId, newName)
    }

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
     * 会话以「开始时间 + 结束时间 + 时长」四要素严格判重，重复条目跳过。
     * 导入产生的项目一律置为 idle，不携带备份文件之外的计时状态。
     */
    suspend fun applyImport(plan: ImportPlan, overwrite: Boolean): ImportSummary = db.withTransaction {
        if (overwrite) {
            sessionDao.deleteAll()
            projectDao.deleteAll()
        }

        val projectIdByName = mutableMapOf<String, Long>()
        val keysByProjectId = mutableMapOf<Long, MutableSet<String>>()

        for (projectWithSessions in projectDao.getProjectsWithSessions()) {
            val projectId = projectWithSessions.project.id
            projectIdByName[projectWithSessions.project.name] = projectId
            keysByProjectId[projectId] = projectWithSessions.sessions
                .map { sessionKey(it) }
                .toMutableSet()
        }

        val preExistingNames = projectIdByName.keys.toSet()

        var importedSessions = 0
        var skippedDuplicates = 0

        for (backupProject in plan.projects) {
            val projectId = projectIdByName[backupProject.name] ?: run {
                val newId = projectDao.insert(ProjectEntity(name = backupProject.name))
                projectIdByName[backupProject.name] = newId
                keysByProjectId[newId] = mutableSetOf()
                newId
            }

            val keys = keysByProjectId.getOrPut(projectId) { mutableSetOf() }

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

    private fun sessionKey(session: SessionEntity): String =
        sessionKey(session.startTime, session.endTime, session.durationMillis)

    private fun sessionKey(startTime: Long, endTime: Long, durationMillis: Long): String =
        "$startTime|$endTime|$durationMillis"

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
        private var INSTANCE: ProjectRepository? = null

        fun get(context: Context): ProjectRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                ProjectRepository(context.applicationContext, db).also {
                    INSTANCE = it
                }
            }
        }
    }
}

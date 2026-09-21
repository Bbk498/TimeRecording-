package com.example.timerecording.data.repository

import android.content.Context
import com.example.timerecording.Project
import com.example.timerecording.Session
import com.example.timerecording.data.DataMigration
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

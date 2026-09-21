package com.example.timerecording.data

import android.content.Context
import com.example.timerecording.data.entity.ProjectEntity
import com.example.timerecording.data.entity.SessionEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DataMigration {

    private const val PREFS_NAME = "my_app_data"
    private const val PREFS_KEY = "projects_data"
    private const val MIGRATED_KEY = "migrated_to_room"

    suspend fun migrateIfNeeded(context: Context, projectDao: com.example.timerecording.data.db.ProjectDao, sessionDao: com.example.timerecording.data.db.SessionDao) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATED_KEY, false)) return

        val json = prefs.getString(PREFS_KEY, null) ?: run {
            prefs.edit().putBoolean(MIGRATED_KEY, true).apply()
            return
        }

        try {
            val type = object : TypeToken<List<LegacyProject>>() {}.type
            val legacyProjects: List<LegacyProject> = Gson().fromJson(json, type)

            for (legacy in legacyProjects) {
                val projectId = projectDao.insert(
                    ProjectEntity(
                        name = legacy.name,
                        state = legacy.state,
                        sessionStartTime = legacy.sessionStartTime,
                        currentStartTime = legacy.currentStartTime,
                        currentElapsedMillis = legacy.currentElapsedMillis
                    )
                )
                for (session in legacy.sessions) {
                    sessionDao.insert(
                        SessionEntity(
                            projectId = projectId,
                            startTime = session.startTime,
                            endTime = session.endTime,
                            durationMillis = session.durationMillis
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Migration failed — leave SharedPreferences intact for retry
            return
        }

        prefs.edit().putBoolean(MIGRATED_KEY, true).apply()
    }

    private data class LegacyProject(
        val name: String = "",
        val sessions: List<LegacySession> = emptyList(),
        val state: String = "idle",
        val sessionStartTime: Long = 0,
        val currentStartTime: Long = 0,
        val currentElapsedMillis: Long = 0
    )

    private data class LegacySession(
        val startTime: Long = 0,
        val endTime: Long = 0,
        val durationMillis: Long = 0
    )
}

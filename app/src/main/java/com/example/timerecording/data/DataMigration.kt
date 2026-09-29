package com.example.timerecording.data

import android.content.Context
import com.example.timerecording.data.entity.ProjectEntity
import com.example.timerecording.data.entity.SessionEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 数据迁移工具，将旧版 SharedPreferences 中存储的项目数据迁移到 Room 数据库。
 * 迁移完成后通过标记位防止重复执行；失败时保留原数据以便下次重试。
 */
object DataMigration {

    private const val PREFS_NAME = "my_app_data"          // 旧版 SharedPreferences 文件名
    private const val PREFS_KEY = "projects_data"          // 旧版项目数据的存储键
    private const val MIGRATED_KEY = "migrated_to_room"    // 迁移完成标记位

    /**
     * 如果尚未迁移，将 SharedPreferences 中的旧数据导入 Room 数据库。
     * 已迁移过则直接返回；迁移失败时保留原数据不标记完成，以便下次启动时重试。
     */
    suspend fun migrateIfNeeded(context: Context, projectDao: com.example.timerecording.data.db.ProjectDao, sessionDao: com.example.timerecording.data.db.SessionDao) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 已迁移过则直接返回
        if (prefs.getBoolean(MIGRATED_KEY, false)) return

        // 读取旧的 JSON 数据，若不存在则直接标记为已迁移
        val json = prefs.getString(PREFS_KEY, null) ?: run {
            prefs.edit().putBoolean(MIGRATED_KEY, true).apply()
            return
        }

        try {
            // 解析旧版 JSON 数据结构
            val type = object : TypeToken<List<LegacyProject>>() {}.type
            val legacyProjects: List<LegacyProject> = Gson().fromJson(json, type)

            // 逐个将旧项目及其会话写入 Room 数据库
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
                // 将该项目下的所有会话写入 sessions 表
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
            // 迁移失败 — 保留 SharedPreferences 原始数据，下次启动时重试
            return
        }

        // 迁移成功，标记完成
        prefs.edit().putBoolean(MIGRATED_KEY, true).apply()
    }

    /** 旧版数据中的项目结构（仅用于反序列化，不参与新逻辑） */
    private data class LegacyProject(
        val name: String = "",
        val sessions: List<LegacySession> = emptyList(),
        val state: String = "idle",
        val sessionStartTime: Long = 0,
        val currentStartTime: Long = 0,
        val currentElapsedMillis: Long = 0
    )

    /** 旧版数据中的会话结构（仅用于反序列化，不参与新逻辑） */
    private data class LegacySession(
        val startTime: Long = 0,
        val endTime: Long = 0,
        val durationMillis: Long = 0
    )
}

package com.example.timerecording.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timerecording.data.entity.SessionEntity

/**
 * 会话表数据访问对象（DAO），提供对 sessions 表的增删查操作。
 * 所有方法均为 suspend，在协程中异步执行。
 */
@Dao
interface SessionDao {

    /** 插入一条计时会话记录，冲突时替换，返回新插入行的 id */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    /** 查询指定项目的所有会话，按开始时间降序排列（最新的在前） */
    @Query("SELECT * FROM sessions WHERE projectId = :projectId ORDER BY startTime DESC")
    suspend fun getSessionsForProject(projectId: Long): List<SessionEntity>

    /** 删除指定项目的所有会话记录 */
    @Query("DELETE FROM sessions WHERE projectId = :projectId")
    suspend fun deleteSessionsForProject(projectId: Long)

    /** 删除所有会话记录（用于覆盖式导入时清空数据） */
    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}

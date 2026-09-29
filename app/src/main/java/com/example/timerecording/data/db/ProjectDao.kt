package com.example.timerecording.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.timerecording.data.entity.ProjectEntity
import com.example.timerecording.data.entity.ProjectWithSessions
import kotlinx.coroutines.flow.Flow

/**
 * 项目表数据访问对象（DAO），提供对 projects 表的增删改查操作。
 * 所有 suspend 方法在协程中异步执行，Flow 方法支持数据变化的响应式监听。
 */
@Dao
interface ProjectDao {

    /** 观察所有项目及其会话（响应式），数据变化时自动推送更新 */
    @Transaction
    @Query("SELECT * FROM projects ORDER BY id ASC")
    fun observeProjectsWithSessions(): Flow<List<ProjectWithSessions>>

    /** 一次性获取所有项目及其会话列表，按 id 升序排列 */
    @Transaction
    @Query("SELECT * FROM projects ORDER BY id ASC")
    suspend fun getProjectsWithSessions(): List<ProjectWithSessions>

    /** 根据 id 获取单个项目及其关联会话 */
    @Transaction
    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectWithSessions(projectId: Long): ProjectWithSessions?

    /** 查询所有处于计时中（running）状态的项目 */
    @Query("SELECT * FROM projects WHERE state = 'running'")
    suspend fun getRunningProjects(): List<ProjectEntity>

    /** 查询所有非空闲状态（running 或 paused）的项目，用于跨天拆分等逻辑 */
    @Query("SELECT * FROM projects WHERE state != 'idle'")
    suspend fun getNonIdleProjects(): List<ProjectEntity>

    /** 插入项目，冲突时替换，返回新插入行的 id */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    /** 更新项目信息（整行更新） */
    @Update
    suspend fun update(project: ProjectEntity)

    /** 删除指定项目（同时级联删除其会话） */
    @Delete
    suspend fun delete(project: ProjectEntity)

    /** 根据 id 删除项目 */
    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteById(projectId: Long)

    /** 删除所有项目（同时级联删除所有会话） */
    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    /** 重命名指定项目 */
    @Query("UPDATE projects SET name = :name WHERE id = :projectId")
    suspend fun renameProject(projectId: Long, name: String)

    /** 更新项目的计时状态及相关时间字段（计时开始/暂停/恢复时调用） */
    @Query("UPDATE projects SET state = :state, sessionStartTime = :sessionStartTime, currentStartTime = :currentStartTime, currentElapsedMillis = :currentElapsedMillis WHERE id = :projectId")
    suspend fun updateTimerState(
        projectId: Long,
        state: String,
        sessionStartTime: Long,
        currentStartTime: Long,
        currentElapsedMillis: Long
    )

    /** 重置项目的计时状态为空闲，清零所有计时字段（会话结束或取消时调用） */
    @Query("UPDATE projects SET state = 'idle', sessionStartTime = 0, currentStartTime = 0, currentElapsedMillis = 0 WHERE id = :projectId")
    suspend fun resetTimer(projectId: Long)
}

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

@Dao
interface ProjectDao {

    @Transaction
    @Query("SELECT * FROM projects ORDER BY id ASC")
    fun observeProjectsWithSessions(): Flow<List<ProjectWithSessions>>

    @Transaction
    @Query("SELECT * FROM projects ORDER BY id ASC")
    suspend fun getProjectsWithSessions(): List<ProjectWithSessions>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectWithSessions(projectId: Long): ProjectWithSessions?

    @Query("SELECT * FROM projects WHERE state = 'running'")
    suspend fun getRunningProjects(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE state != 'idle'")
    suspend fun getNonIdleProjects(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteById(projectId: Long)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Query("UPDATE projects SET name = :name WHERE id = :projectId")
    suspend fun renameProject(projectId: Long, name: String)

    @Query("UPDATE projects SET state = :state, sessionStartTime = :sessionStartTime, currentStartTime = :currentStartTime, currentElapsedMillis = :currentElapsedMillis WHERE id = :projectId")
    suspend fun updateTimerState(
        projectId: Long,
        state: String,
        sessionStartTime: Long,
        currentStartTime: Long,
        currentElapsedMillis: Long
    )

    @Query("UPDATE projects SET state = 'idle', sessionStartTime = 0, currentStartTime = 0, currentElapsedMillis = 0 WHERE id = :projectId")
    suspend fun resetTimer(projectId: Long)
}

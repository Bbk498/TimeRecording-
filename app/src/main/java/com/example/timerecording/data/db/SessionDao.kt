package com.example.timerecording.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timerecording.data.entity.SessionEntity

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE projectId = :projectId ORDER BY startTime DESC")
    suspend fun getSessionsForProject(projectId: Long): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE projectId = :projectId")
    suspend fun deleteSessionsForProject(projectId: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}

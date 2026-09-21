package com.example.timerecording.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val state: String = "idle",
    val sessionStartTime: Long = 0,
    val currentStartTime: Long = 0,
    val currentElapsedMillis: Long = 0
)

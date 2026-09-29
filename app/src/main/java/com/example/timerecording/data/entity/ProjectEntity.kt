package com.example.timerecording.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 项目表实体，映射到数据库 projects 表。
 * 存储项目基本信息及当前计时状态，不含会话记录（会话由 [SessionEntity] 单独存储）。
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,  // 自增主键
    val name: String,                                    // 项目名称
    val state: String = "idle",                          // 计时状态：idle / running / paused
    val sessionStartTime: Long = 0,                      // 当前会话的开始时间戳（毫秒）
    val currentStartTime: Long = 0,                      // 当前计时片段的开始时间戳（毫秒）
    val currentElapsedMillis: Long = 0                   // 当前会话已累计时长（毫秒）
)

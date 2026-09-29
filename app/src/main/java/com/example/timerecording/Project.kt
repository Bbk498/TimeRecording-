package com.example.timerecording

/**
 * 项目 UI 数据模型，承载项目信息及当前计时状态。
 * 与数据库实体 [com.example.timerecording.data.entity.ProjectEntity] 对应，
 * 但额外携带了已完成的会话列表，供界面直接使用。
 */
data class Project(
    val id: Long = 0,                        // 数据库主键，新增项目时为 0
    var name: String,                        // 项目名称
    val sessions: MutableList<Session> = mutableListOf(),  // 已完成的计时会话列表
    var state: String = "idle",              // 计时状态：idle(空闲) / running(计时中) / paused(已暂停)
    var sessionStartTime: Long = 0,          // 当前会话的开始时间戳（毫秒）
    var currentStartTime: Long = 0,          // 当前计时片段的开始时间戳（毫秒），暂停/恢复时更新
    var currentElapsedMillis: Long = 0       // 当前会话已累计的计时时长（毫秒），暂停时保存
)

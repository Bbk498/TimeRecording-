package com.example.timerecording

/**
 * 单条计时会话的 UI 数据模型。
 * 记录一次完整的计时过程（从开始到结束）。
 * 与数据库实体 [com.example.timerecording.data.entity.SessionEntity] 对应。
 */
data class Session(
    val id: Long = 0,               // 数据库主键，新增会话时为 0
    val startTime: Long,            // 会话开始时间戳（毫秒）
    val endTime: Long,              // 会话结束时间戳（毫秒）
    val durationMillis: Long        // 会话持续时长（毫秒）
)

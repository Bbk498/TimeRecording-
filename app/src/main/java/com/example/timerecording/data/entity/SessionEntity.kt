package com.example.timerecording.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 计时会话表实体，映射到数据库 sessions 表。
 * 每条记录代表一次完整的计时过程（从开始到结束保存）。
 * 通过外键 projectId 关联到 [ProjectEntity]，项目删除时会话级联删除。
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,       // 父表：项目表
            parentColumns = ["id"],               // 父表引用列：项目 id
            childColumns = ["projectId"],         // 子表外键列：projectId
            onDelete = ForeignKey.CASCADE         // 删除项目时级联删除其所有会话
        )
    ],
    indices = [Index("projectId")]                 // 为 projectId 建索引，加速按项目查询会话
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,  // 自增主键
    val projectId: Long,                                 // 所属项目 id（外键）
    val startTime: Long,                                 // 会话开始时间戳（毫秒）
    val endTime: Long,                                   // 会话结束时间戳（毫秒）
    val durationMillis: Long                             // 会话持续时长（毫秒）
)

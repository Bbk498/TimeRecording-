package com.example.timerecording.data.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 项目与其关联会话的嵌套关系模型。
 * Room 通过 @Embedded 内嵌项目实体，通过 @Relation 自动查询该项目的所有会话，
 * 用于一次性获取项目完整数据（项目信息 + 会话列表）。
 */
data class ProjectWithSessions(
    @Embedded val project: ProjectEntity,    // 内嵌的项目实体（对应 projects 表的一行）
    @Relation(
        parentColumn = "id",                  // 父表（projects）的关联列：id
        entityColumn = "projectId"            // 子表（sessions）的外键列：projectId
    )
    val sessions: List<SessionEntity>         // 该项目下的所有计时会话列表
)

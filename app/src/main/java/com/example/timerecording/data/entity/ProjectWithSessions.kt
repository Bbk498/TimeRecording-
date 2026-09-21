package com.example.timerecording.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ProjectWithSessions(
    @Embedded val project: ProjectEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val sessions: List<SessionEntity>
)

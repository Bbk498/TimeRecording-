package com.example.timerecording

data class Session(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationMillis: Long
)
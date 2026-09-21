package com.example.timerecording

data class Project(
    val id: Long = 0,
    var name: String,
    val sessions: MutableList<Session> = mutableListOf(),
    var state: String = "idle",
    var sessionStartTime: Long = 0,
    var currentStartTime: Long = 0,
    var currentElapsedMillis: Long = 0
)
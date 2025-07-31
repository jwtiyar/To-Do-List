package com.example.taskmanager

import java.time.LocalDateTime

data class Task(
    val id: Int,
    val title: String,
    val description: String,
    var isCompleted: Boolean = false,
    var scheduledTime: LocalDateTime? = null,
    var notificationId: Int? = null
) 
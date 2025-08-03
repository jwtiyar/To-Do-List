package com.example.taskmanager

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String,
    var isCompleted: Boolean = false,
    var dueDateMillis: Long? = null,
    var notificationId: Int? = null
) 
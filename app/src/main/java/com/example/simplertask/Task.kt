package com.example.simplertask

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String,
    var isCompleted: Boolean = false,
    var isSaved: Boolean = false,
    var isArchived: Boolean = false,
    var dueDateMillis: Long? = null,
    var notificationId: Int? = null,
    var priority: Priority = Priority.MEDIUM
)

enum class Priority {
    LOW, MEDIUM, HIGH
}

typealias TaskAction = String

const val TOGGLE_SAVE = "TOGGLE_SAVE"
const val TOGGLE_ARCHIVE = "TOGGLE_ARCHIVE"

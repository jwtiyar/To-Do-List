package io.github.jwtiyar.simplertask.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task",
    indices = [
        Index("isCompleted"),
        Index("isArchived"),
        Index("isSaved"),
        Index("dueDateMillis"),
        // Composite index to accelerate common filter + ordering by id desc
        Index(value = ["isArchived", "isCompleted", "id"], name = "idx_task_archived_completed_id")
    ]
)
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
) {
    /**
     * Custom equals implementation that compares all fields except notificationId
     * as it's used for internal notification tracking and shouldn't affect task equality
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Task

        if (id != other.id) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (isCompleted != other.isCompleted) return false
        if (isSaved != other.isSaved) return false
        if (isArchived != other.isArchived) return false
        if (dueDateMillis != other.dueDateMillis) return false
        if (priority != other.priority) return false

        return true
    }

    /**
     * Consistent with equals() implementation
     */
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + isCompleted.hashCode()
        result = 31 * result + isSaved.hashCode()
        result = 31 * result + isArchived.hashCode()
        result = 31 * result + (dueDateMillis?.hashCode() ?: 0)
        result = 31 * result + priority.hashCode()
        return result
    }
}

enum class Priority {
    LOW, MEDIUM, HIGH
}

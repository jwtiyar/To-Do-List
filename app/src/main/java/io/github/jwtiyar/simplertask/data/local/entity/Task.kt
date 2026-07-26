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
        Index("recurrenceType"),
        Index("categoryId"),
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
    var priority: Priority = Priority.MEDIUM,
    // Recurring task fields
    var recurrenceType: RecurrenceType? = null,
    var recurrenceInterval: Int = 1, // every N days/weeks/months
    var recurrenceEndDate: Long? = null, // when to stop recurring (null = indefinite)
    var parentTaskId: Int? = null, // ID of the original recurring task
    var categoryId: Int? = null // Reference to Category.id
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
        if (recurrenceType != other.recurrenceType) return false
        if (recurrenceInterval != other.recurrenceInterval) return false
        if (recurrenceEndDate != other.recurrenceEndDate) return false
        if (parentTaskId != other.parentTaskId) return false
        if (categoryId != other.categoryId) return false

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
        result = 31 * result + (recurrenceType?.hashCode() ?: 0)
        result = 31 * result + recurrenceInterval
        result = 31 * result + (recurrenceEndDate?.hashCode() ?: 0)
        result = 31 * result + (parentTaskId ?: 0)
        result = 31 * result + (categoryId ?: 0)
        return result
    }
}

enum class Priority {
    LOW, MEDIUM, HIGH
}

enum class RecurrenceType {
    DAILY, WEEKLY, MONTHLY, CUSTOM
}

fun Task.isRecurring(): Boolean = recurrenceType != null

fun Task.getNextDueDate(): Long? {
    if (!isRecurring()) return null

    // If the task had no due date, calculate the next one based on right now
    val currentDueDate = dueDateMillis ?: System.currentTimeMillis()
    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = currentDueDate
    }

    val (calField, calAmount) = when (recurrenceType) {
        RecurrenceType.DAILY  -> java.util.Calendar.DAY_OF_MONTH to recurrenceInterval
        RecurrenceType.WEEKLY -> java.util.Calendar.WEEK_OF_YEAR to recurrenceInterval
        RecurrenceType.MONTHLY -> java.util.Calendar.MONTH to recurrenceInterval
        RecurrenceType.CUSTOM -> java.util.Calendar.DAY_OF_MONTH to recurrenceInterval
        null -> return null
    }

    // Advance at least once, then keep advancing until the result is in the future.
    // This prevents the next occurrence from appearing immediately when the task had
    // no due date or a past due date.
    val now = System.currentTimeMillis()
    do {
        calendar.add(calField, calAmount)
    } while (calendar.timeInMillis <= now)

    val nextDueDate = calendar.timeInMillis

    // Check if we've exceeded the end date
    return if (recurrenceEndDate != null && nextDueDate > recurrenceEndDate!!) {
        null // No more occurrences
    } else {
        nextDueDate
    }
}


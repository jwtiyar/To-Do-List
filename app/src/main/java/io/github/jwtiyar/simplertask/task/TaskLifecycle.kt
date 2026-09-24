package io.github.jwtiyar.simplertask.task

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.local.TaskDatabase
import io.github.jwtiyar.simplertask.data.backup.BackupManager
import androidx.room.withTransaction
import io.github.jwtiyar.simplertask.data.repository.TaskRepository
import io.github.jwtiyar.simplertask.service.NotificationHelper
import io.github.jwtiyar.simplertask.widget.TaskWidgetProvider
import javax.inject.Inject

/** Keeps task writes, alarms and the home-screen widget in step. */
class TaskLifecycle @Inject constructor(
    private val repository: TaskRepository,
    private val reminders: NotificationHelper,
    @ApplicationContext private val context: Context,
    private val database: TaskDatabase
) {
    suspend fun backupData(): BackupManager.BackupData = database.withTransaction {
        BackupManager.BackupData(
            database.taskDao().getAllTasksForBackup(),
            database.categoryDao().getAllCategoriesForBackup()
        )
    }

    suspend fun importBackup(data: BackupManager.BackupData, replaceExisting: Boolean) {
        val originalIds = data.tasks.map { it.id }
        require(originalIds.all { it > 0 } && originalIds.distinct().size == originalIds.size) {
            "Backup contains duplicate or invalid task IDs"
        }
        require(data.tasks.all { it.recurrenceType == null || it.recurrenceInterval > 0 }) {
            "Backup contains an invalid recurrence interval"
        }
        require(data.categories.map { it.id }.distinct().size == data.categories.size) {
            "Backup contains duplicate category IDs"
        }

        val before = if (replaceExisting) repository.getAllTasksForBackup() else emptyList()
        val restored = database.withTransaction {
            val categoryIds = mutableMapOf<Int, Int>()
            val categoryDao = database.categoryDao()
            if (replaceExisting) {
                database.taskDao().clearAllTasks()
                if (data.includesCategories) categoryDao.clearAllCategories()
            }
            for (category in data.categories) {
                val existing = categoryDao.getCategoryByName(category.name)
                categoryIds[category.id] = existing?.id ?: categoryDao.insertCategory(category.copy(id = 0)).toInt()
            }
            val taskIds = mutableMapOf<Int, Int>()
            val tasks = data.tasks.map { task ->
                val categoryId = task.categoryId?.let { categoryIds[it] }
                // Legacy backups did not carry category records; keep their tasks uncategorized.
                val row = task.copy(id = 0, parentTaskId = null, categoryId = categoryId, notificationId = null)
                val id = database.taskDao().insertTask(row).toInt()
                taskIds[task.id] = id
                row.copy(id = id)
            }
            tasks.mapIndexed { index, row ->
                val parentId = data.tasks[index].parentTaskId?.let(taskIds::get)
                row.copy(parentTaskId = parentId).also {
                    if (parentId != null) database.taskDao().updateTask(it)
                }
            }
        }
        before.forEach(reminders::cancelNotification)
        restored.forEach(reminders::scheduleNotification)
        TaskWidgetProvider.updateAllWidgets(context)
    }
    suspend fun create(task: Task): Task {
        val id = repository.insertTask(task)
        val inserted = task.copy(id = id.toInt())
        reminders.scheduleNotification(inserted)
        TaskWidgetProvider.updateAllWidgets(context)
        return inserted
    }

    suspend fun complete(id: Long): Long? {
        val completed = repository.completeTask(id) ?: return null
        reminders.cancelNotification(completed.original)
        completed.next?.let(reminders::scheduleNotification)
        TaskWidgetProvider.updateAllWidgets(context)
        return completed.next?.id?.toLong()
    }

    suspend fun reopen(id: Long) {
        val task = repository.getTaskById(id) ?: return
        if (!task.isCompleted) return
        val reopened = task.copy(isCompleted = false)
        repository.updateTask(reopened)
        reminders.scheduleNotification(reopened)
        TaskWidgetProvider.updateAllWidgets(context)
    }

    suspend fun undoCompletion(id: Long, nextId: Long?) {
        val undone = repository.undoCompletion(id, nextId) ?: return
        undone.removedNext?.let(reminders::cancelNotification)
        reminders.scheduleNotification(undone.restored)
        TaskWidgetProvider.updateAllWidgets(context)
    }

    suspend fun update(task: Task) {
        val previous = repository.getTaskById(task.id.toLong()) ?: return
        repository.updateTask(task)
        reminders.cancelNotification(previous)
        reminders.scheduleNotification(task)
        TaskWidgetProvider.updateAllWidgets(context)
    }

    suspend fun delete(id: Long) {
        val task = repository.getTaskById(id) ?: return
        repository.deleteTask(task)
        reminders.cancelNotification(task)
        TaskWidgetProvider.updateAllWidgets(context)
    }

    suspend fun restore(task: Task) {
        database.taskDao().insertRestoredTask(task)
        reminders.scheduleNotification(task)
        TaskWidgetProvider.updateAllWidgets(context)
    }

    suspend fun snooze(id: Long, until: Long) {
        val task = repository.getTaskById(id) ?: return
        if (task.isCompleted || task.isArchived) return
        update(task.copy(dueDateMillis = until))
    }

    suspend fun clearCompleted() {
        val completed = repository.getAllTasksForBackup().filter { it.isCompleted }
        repository.deleteCompletedTasks()
        completed.forEach(reminders::cancelNotification)
        TaskWidgetProvider.updateAllWidgets(context)
    }

    suspend fun resetCompleted() {
        val completed = repository.getAllTasksForBackup().filter { it.isCompleted }
        repository.resetAllTasksToPending()
        completed.forEach { reminders.scheduleNotification(it.copy(isCompleted = false)) }
        TaskWidgetProvider.updateAllWidgets(context)
    }
}

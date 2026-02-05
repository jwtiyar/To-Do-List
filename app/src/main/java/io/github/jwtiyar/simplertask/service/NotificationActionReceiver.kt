package io.github.jwtiyar.simplertask.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.github.jwtiyar.simplertask.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles notification action button clicks (Complete, Snooze).
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: TaskRepository

    companion object {
        const val ACTION_COMPLETE = "io.github.jwtiyar.simplertask.ACTION_COMPLETE"
        const val ACTION_SNOOZE = "io.github.jwtiyar.simplertask.ACTION_SNOOZE"
        const val EXTRA_TASK_ID = "task_id"
        const val SNOOZE_DURATION_MILLIS = 10 * 60 * 1000L // 10 minutes
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        if (taskId == -1) {
            Log.e("NotificationAction", "Invalid task ID")
            return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> handleComplete(context, taskId)
                    ACTION_SNOOZE -> handleSnooze(context, taskId)
                }
            } catch (e: Exception) {
                Log.e("NotificationAction", "Error handling action: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleComplete(context: Context, taskId: Int) {
        Log.d("NotificationAction", "Completing task $taskId")
        
        // Get the task and mark it as completed
        val task = repository.getTaskById(taskId.toLong())
        if (task != null) {
            val completedTask = task.copy(isCompleted = true)
            repository.updateTask(completedTask)
            
            // Cancel the notification
            val notificationHelper = NotificationHelper(context.applicationContext)
            notificationHelper.cancelNotification(completedTask)
            
            Log.d("NotificationAction", "Task $taskId marked as completed")
        } else {
            Log.e("NotificationAction", "Task $taskId not found")
        }
    }

    private suspend fun handleSnooze(context: Context, taskId: Int) {
        Log.d("NotificationAction", "Snoozing task $taskId")
        
        // Get the task
        val task = repository.getTaskById(taskId.toLong())
        if (task != null) {
            val notificationHelper = NotificationHelper(context.applicationContext)
            
            // Cancel current notification
            notificationHelper.cancelNotification(task)
            
            // Update task with new due date (10 minutes from now)
            val newDueDate = System.currentTimeMillis() + SNOOZE_DURATION_MILLIS
            val snoozedTask = task.copy(dueDateMillis = newDueDate)
            repository.updateTask(snoozedTask)
            
            // Reschedule notification
            notificationHelper.scheduleNotification(snoozedTask)
            
            Log.d("NotificationAction", "Task $taskId snoozed for 10 minutes")
        } else {
            Log.e("NotificationAction", "Task $taskId not found")
        }
    }
}

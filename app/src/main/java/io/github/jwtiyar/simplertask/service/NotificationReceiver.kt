package io.github.jwtiyar.simplertask.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.jwtiyar.simplertask.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var repository: TaskRepository
    
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("NotificationReceiver", "Reminder triggered! Broadcast received.")
        val taskId = intent.getIntExtra("task_id", -1)
        if (taskId == -1) return

        val scheduledDue = intent.getLongExtra("scheduled_due", -1L)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val task = repository.getTaskById(taskId.toLong())
                val due = task?.dueDateMillis
                if (task != null && !task.isCompleted && !task.isArchived && due != null &&
                    due <= System.currentTimeMillis() + 5000L && (scheduledDue == -1L || due == scheduledDue)
                ) {
                    notificationHelper.showNotification(task.id, task.title, task.description, task.priority.name)
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationReceiver", "Unable to show task reminder", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

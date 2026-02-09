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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver that reschedules all pending task notifications after device reboot.
 * 
 * This is critical because AlarmManager alarms are cleared when the device reboots.
 * Without this receiver, users would lose all their scheduled task reminders.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: TaskRepository
    
    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d("BootReceiver", "Device boot completed - rescheduling notifications")

        // Use goAsync() to allow background work since we need to query the database
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                // Get all pending (non-completed) tasks with future due dates
                val tasksWithReminders = repository.getPendingTasksForBootReschedule()

                Log.d("BootReceiver", "Found ${tasksWithReminders.size} tasks to reschedule")

                // Reschedule notification for each task
                tasksWithReminders.forEach { task ->
                    try {
                        notificationHelper.scheduleNotification(task)
                        Log.d("BootReceiver", "Rescheduled notification for task: ${task.title}")
                    } catch (e: Exception) {
                        Log.e("BootReceiver", "Failed to reschedule task ${task.id}: ${e.message}")
                    }
                }

                Log.d("BootReceiver", "Notification rescheduling completed")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error during boot notification rescheduling: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}

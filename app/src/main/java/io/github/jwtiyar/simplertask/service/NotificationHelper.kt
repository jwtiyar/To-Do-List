package io.github.jwtiyar.simplertask.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build // Keep for VERSION_CODES.S
import androidx.core.app.NotificationCompat
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.MainActivity
import io.github.jwtiyar.simplertask.R

class NotificationHelper(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val CHANNEL_NAME = "Task Reminders"
        const val CHANNEL_DESCRIPTION = "Notifications for task reminders"
        const val GROUP_KEY_TASKS = "io.github.jwtiyar.simplertask.TASK_GROUP"
        const val SUMMARY_ID = 0 // Unique ID for the summary notification
        
        fun scheduleOrToggle(helper: NotificationHelper, task: Task) {
            if (task.isCompleted) {
                helper.cancelNotification(task)
            } else {
                task.dueDateMillis?.let { scheduledMillis ->
                    helper.scheduleNotification(task)
                }
            }
        }
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        // SDK_INT >= Build.VERSION_CODES.O check removed as minSdkVersion is 26
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH // Increased for heads-up notifications
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableLights(true)
            lightColor = android.graphics.Color.BLUE
            enableVibration(true)
            vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    fun scheduleNotification(task: Task) {
        android.util.Log.d("NotificationHelper", "scheduleNotification called for task: ${task.title}")
        task.dueDateMillis?.let { scheduledMillis ->
            android.util.Log.d("NotificationHelper", "Scheduling notification for ${java.util.Date(scheduledMillis)}")
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("task_id", task.id)
                putExtra("task_title", task.title)
                putExtra("task_description", task.description)
                putExtra("task_priority", task.priority.name)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                // Check if we can schedule exact alarms
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        android.util.Log.d("NotificationHelper", "Using exact alarm")
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            scheduledMillis,
                            pendingIntent
                        )
                    } else {
                        android.util.Log.d("NotificationHelper", "Using inexact alarm - exact alarms not allowed")
                        // Fallback to inexact alarm
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            scheduledMillis,
                            pendingIntent
                        )
                    }
                } else {
                    android.util.Log.d("NotificationHelper", "Using exact alarm for older Android")
                    // For older Android versions (but still >= O)
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduledMillis,
                        pendingIntent
                    )
                }
                
                task.notificationId = task.id
                android.util.Log.d("NotificationHelper", "Alarm scheduled successfully for task ${task.id}")
            } catch (e: SecurityException) {
                android.util.Log.e("NotificationHelper", "Security exception scheduling alarm: ${e.message}")
                // Handle the case where exact alarm permission is denied
                try {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduledMillis,
                        pendingIntent
                    )
                    task.notificationId = task.id
                    android.util.Log.d("NotificationHelper", "Fallback alarm scheduled")
                } catch (e2: Exception) {
                    android.util.Log.e("NotificationHelper", "All alarm scheduling failed: ${e2.message}")
                    // If all else fails, just show a notification immediately
                    showNotification(task.id, task.title, task.description)
                }
            }
        } ?: android.util.Log.w("NotificationHelper", "No dueDateMillis set for task: ${task.title}")
    }
    
    fun cancelNotification(task: Task) {
        task.notificationId?.let { notificationId ->
            // Cancel the scheduled alarm
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            
            // Also dismiss the notification if it's already showing in the notification drawer
            dismissNotification(notificationId)
            
            task.notificationId = null
        }
    }

    /**
     * Dismisses a specific notification and checks if the summary should also be removed.
     */
    fun dismissNotification(taskId: Int) {
        notificationManager.cancel(taskId)
        
        // Check if any other notifications in this group are still active
        val activeNotifications = notificationManager.activeNotifications
        val taskNotificationsCount = activeNotifications.count { 
            it.id != SUMMARY_ID && it.notification.group == GROUP_KEY_TASKS 
        }

        // If no more task notifications exist, remove the summary/header too
        if (taskNotificationsCount == 0) {
            notificationManager.cancel(SUMMARY_ID)
        }
    }
    
    fun showNotification(taskId: Int, title: String, description: String, priority: String = "MEDIUM") {
        android.util.Log.d("NotificationHelper", "Showing notification for task $taskId: $title (Priority: $priority)")
        
        // Intent to open the app when notification is tapped
        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            taskId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Action: Mark as Complete
        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            taskId * 10 + 1, // Unique request code
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Action: Snooze (10 minutes)
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            taskId * 10 + 2, // Unique request code
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Determine notification priority based on task priority
        val notificationPriority = when (priority) {
            "HIGH" -> NotificationCompat.PRIORITY_HIGH
            "LOW" -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
        
        // Add priority emoji for visual distinction
        val priorityEmoji = when (priority) {
            "HIGH" -> "🔴 "
            "MEDIUM" -> "🟡 "
            "LOW" -> "🟢 "
            else -> ""
        }
        
        // Set vibration pattern based on priority
        val vibrationPattern = when (priority) {
            "HIGH" -> longArrayOf(0, 500, 100, 500, 100, 500) // 3 long pulses
            "MEDIUM" -> longArrayOf(0, 250, 250, 250) // 2 pulses
            else -> longArrayOf(0, 250) // 1 short pulse
        }
        
        // Format the due time for the notification content
        val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("$priorityEmoji$title")
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$description\n\nPriority: $priority\nTime: $timeStr")
                .setBigContentTitle("$priorityEmoji$title")
                .setSummaryText("Task Reminder"))
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setPriority(notificationPriority)
            .setVibrate(vibrationPattern)
            .setGroup(GROUP_KEY_TASKS)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Complete",
                completePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                "Snooze 10m",
                snoozePendingIntent
            )
            .build()
        
        notificationManager.notify(taskId, notification)

        // Only show summary if we have at least one notification (actually Android handles this, 
        // but it's good practice to keep the summary updated)
        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Tasks")
            .setContentText("Check your upcoming tasks")
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Task Reminders"))
            .setGroup(GROUP_KEY_TASKS)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(SUMMARY_ID, summaryNotification)
        
        android.util.Log.d("NotificationHelper", "Notification posted for task $taskId (Priority: $priority)")
    }

    /**
     * Opens the system notification settings for this app's reminder channel.
     * Useful for allowing users to customize sounds, vibration, etc.
     */
    fun openNotificationSettings() {
        val intent = Intent().apply {
            action = android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general app notification settings
            val fallbackIntent = Intent().apply {
                action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(fallbackIntent)
        }
    }
}

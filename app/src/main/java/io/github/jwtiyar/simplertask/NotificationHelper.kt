package io.github.jwtiyar.simplertask

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build // Keep for VERSION_CODES.S
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val CHANNEL_NAME = "Task Reminders"
        const val CHANNEL_DESCRIPTION = "Notifications for task reminders"
        
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
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    fun scheduleNotification(task: Task) {
        task.dueDateMillis?.let { scheduledMillis ->
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("task_id", task.id)
                putExtra("task_title", task.title)
                putExtra("task_description", task.description)
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
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            scheduledMillis,
                            pendingIntent
                        )
                    } else {
                        // Fallback to inexact alarm
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            scheduledMillis,
                            pendingIntent
                        )
                    }
                } else {
                    // For older Android versions (but still >= O)
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduledMillis,
                        pendingIntent
                    )
                }
                
                task.notificationId = task.id
            } catch (_: SecurityException) { // Changed e to _
                // Handle the case where exact alarm permission is denied
                try {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduledMillis,
                        pendingIntent
                    )
                    task.notificationId = task.id
                } catch (_: Exception) { // Changed e2 to _
                    // If all else fails, just show a notification immediately
                    showNotification(task.id, task.title, task.description)
                }
            }
        }
    }
    
    fun cancelNotification(task: Task) {
        task.notificationId?.let { notificationId ->
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
            
            task.notificationId = null
        }
    }
    
    fun showNotification(taskId: Int, title: String, description: String) {
        val intent = Intent(context, Class.forName("com.example.simplertask.MainActivity"))
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Task Reminder: $title")
            .setContentText(description)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(taskId, notification)
    }
}

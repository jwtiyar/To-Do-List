package io.github.jwtiyar.simplertask.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jwtiyar.simplertask.MainActivity
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.data.local.entity.Task
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val GROUP_KEY_TASKS = "io.github.jwtiyar.simplertask.TASK_GROUP"
        const val SUMMARY_ID = 0
        
        fun scheduleOrToggle(helper: NotificationHelper, task: Task) {
            if (task.isCompleted) {
                helper.cancelNotification(task)
            } else {
                task.dueDateMillis?.let {
                    helper.scheduleNotification(task)
                }
            }
        }
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableLights(true)
            lightColor = android.graphics.Color.BLUE
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    fun scheduleNotification(task: Task) {
        val scheduledMillis = task.dueDateMillis ?: return
        
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
        
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledMillis,
                    pendingIntent
                )
            }
            task.notificationId = task.id
        } catch (e: SecurityException) {
            // Fallback for unexpected security exceptions
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledMillis,
                pendingIntent
            )
            task.notificationId = task.id
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
            
            dismissNotification(notificationId)
            task.notificationId = null
        }
    }

    fun dismissNotification(taskId: Int) {
        notificationManager.cancel(taskId)
        
        val activeNotifications = notificationManager.activeNotifications
        // Exclude the current taskId from the count because cancel() is asynchronous
        val taskNotificationsCount = activeNotifications.count { 
            it.id != SUMMARY_ID && it.id != taskId && it.notification.group == GROUP_KEY_TASKS 
        }

        if (taskNotificationsCount == 0) {
            notificationManager.cancel(SUMMARY_ID)
        }
    }
    
    fun showNotification(taskId: Int, title: String, description: String, priority: String = "MEDIUM") {
        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            taskId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            taskId * 10 + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            taskId * 10 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val (notificationPriority, priorityEmoji, vibrationPattern) = when (priority) {
            "HIGH" -> Triple(NotificationCompat.PRIORITY_HIGH, "🔴 ", longArrayOf(0, 500, 100, 500, 100, 500))
            "LOW" -> Triple(NotificationCompat.PRIORITY_LOW, "🟢 ", longArrayOf(0, 250))
            else -> Triple(NotificationCompat.PRIORITY_DEFAULT, "🟡 ", longArrayOf(0, 250, 250, 250))
        }
        
        val timeStr = timeFormatter.format(Instant.now())
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("$priorityEmoji$title")
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$description\n\nPriority: $priority\nTime: $timeStr")
                .setBigContentTitle("$priorityEmoji$title")
                .setSummaryText(context.getString(R.string.app_name)))
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setPriority(notificationPriority)
            .setVibrate(vibrationPattern)
            .setGroup(GROUP_KEY_TASKS)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Complete", completePendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 10m", snoozePendingIntent)
            .build()
        
        notificationManager.notify(taskId, notification)

        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Task Reminders"))
            .setGroup(GROUP_KEY_TASKS)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(SUMMARY_ID, summaryNotification)
    }

    fun openNotificationSettings() {
        val intent = Intent().apply {
            action = android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            val fallbackIntent = Intent().apply {
                action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
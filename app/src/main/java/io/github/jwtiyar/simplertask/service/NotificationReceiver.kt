package io.github.jwtiyar.simplertask.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var notificationHelper: NotificationHelper
    
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("NotificationReceiver", "Reminder triggered! Broadcast received.")
        val taskId = intent.getIntExtra("task_id", -1)
        val taskTitle = intent.getStringExtra("task_title") ?: ""
        val taskDescription = intent.getStringExtra("task_description") ?: ""
        val taskPriority = intent.getStringExtra("task_priority") ?: "MEDIUM"
        
        if (taskId != -1) {
            notificationHelper.showNotification(taskId, taskTitle, taskDescription, taskPriority)
        }
    }
}
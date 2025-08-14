package io.github.jwtiyar.simplertask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("NotificationReceiver", "Reminder triggered! Broadcast received.")
        android.widget.Toast.makeText(context, "Reminder triggered!", android.widget.Toast.LENGTH_LONG).show()
        val taskId = intent.getIntExtra("task_id", -1)
        val taskTitle = intent.getStringExtra("task_title") ?: ""
        val taskDescription = intent.getStringExtra("task_description") ?: ""
        if (taskId != -1) {
            val notificationHelper = NotificationHelper(context)
            notificationHelper.showNotification(taskId, taskTitle, taskDescription)
        }
    }
} 

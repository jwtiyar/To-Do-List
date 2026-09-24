package io.github.jwtiyar.simplertask.data

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.github.jwtiyar.simplertask.data.local.TaskDatabase
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.service.NotificationHelper
import io.github.jwtiyar.simplertask.service.NotificationReceiver
import io.github.jwtiyar.simplertask.service.BootReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderDeliveryTest {
    @get:Rule val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun futureTaskNotifiesButArchivedTaskDoesNot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = TaskDatabase.getDatabase(context)
        val helper = NotificationHelper(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val due = System.currentTimeMillis() + 5_000L
        val firstId = database.taskDao().insertTask(Task(title = "Reminder delivery check", description = "", dueDateMillis = due))
        val secondId = database.taskDao().insertTask(Task(title = "Archived reminder check", description = "", dueDateMillis = due, isArchived = true))
        val first = database.taskDao().getTaskById(firstId)!!
        val second = database.taskDao().getTaskById(secondId)!!

        try {
            helper.scheduleNotification(first)
            helper.scheduleNotification(second)
            val deadline = System.currentTimeMillis() + 15_000L
            while (System.currentTimeMillis() < deadline && notifications.activeNotifications.none { it.id == first.id }) {
                Thread.sleep(250)
            }
            assertTrue("Pending task reminder was not delivered", notifications.activeNotifications.any { it.id == first.id })
            assertTrue("Archived task sent a reminder", notifications.activeNotifications.none { it.id == second.id })
        } finally {
            helper.cancelNotification(first)
            helper.cancelNotification(second)
            database.taskDao().deleteTask(first)
            database.taskDao().deleteTask(second)
        }
    }

    @Test
    fun receiverDiscardsAlarmForCompletedTask() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = TaskDatabase.getDatabase(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val due = System.currentTimeMillis() - 1_000L
        val id = database.taskDao().insertTask(Task(title = "Completed reminder check", description = "", dueDateMillis = due, isCompleted = true))
        val task = database.taskDao().getTaskById(id)!!
        try {
            context.sendBroadcast(Intent(context, NotificationReceiver::class.java).apply {
                putExtra("task_id", task.id)
                putExtra("scheduled_due", due)
            })
            Thread.sleep(1_000)
            assertTrue("Completed task sent a reminder", notifications.activeNotifications.none { it.id == task.id })
        } finally {
            NotificationHelper(context).cancelNotification(task)
            database.taskDao().deleteTask(task)
        }
    }
    @Test
    fun receiverIsRegisteredForRebootUpgradeAndTimeChanges() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pm = context.packageManager
        for (action in listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )) {
            val matches = pm.queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
            assertTrue("BootReceiver is not registered for $action", matches.any { it.activityInfo.name.endsWith("BootReceiver") })
        }
    }

}

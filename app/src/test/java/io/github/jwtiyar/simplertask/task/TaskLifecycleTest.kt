package io.github.jwtiyar.simplertask.task

import android.content.Context
import io.github.jwtiyar.simplertask.data.local.dao.TaskDao
import io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.repository.TaskRepository
import io.github.jwtiyar.simplertask.data.local.TaskDatabase
import io.github.jwtiyar.simplertask.service.NotificationHelper
import io.github.jwtiyar.simplertask.widget.TaskWidgetProvider
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TaskLifecycleTest {
    private val repository = mockk<TaskRepository>(relaxed = true)
    private val reminders = mockk<NotificationHelper>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val lifecycle = TaskLifecycle(repository, reminders, context, mockk<TaskDatabase>())

    @Before
    fun setup() {
        mockkObject(TaskWidgetProvider)
        every { TaskWidgetProvider.updateAllWidgets(any()) } just Runs
    }

    @After
    fun teardown() {
        unmockkObject(TaskWidgetProvider)
    }

    @Test
    fun completingRecurringTaskCancelsOldReminderAndSchedulesNext() = runTest {
        val original = Task(id = 7, title = "Water plants", description = "", recurrenceType = RecurrenceType.WEEKLY)
        val next = original.copy(id = 8, parentTaskId = 7, dueDateMillis = System.currentTimeMillis() + 60_000)
        coEvery { repository.completeTask(7) } returns TaskDao.Completion(original, next)

        assertEquals(8L, lifecycle.complete(7))

        verify(exactly = 1) { reminders.cancelNotification(original) }
        verify(exactly = 1) { reminders.scheduleNotification(next) }
        verify(exactly = 1) { TaskWidgetProvider.updateAllWidgets(context) }
    }

    @Test
    fun completingAlreadyCompletedTaskDoesNotChangeReminders() = runTest {
        coEvery { repository.completeTask(7) } returns null

        assertEquals(null, lifecycle.complete(7))

        verify(exactly = 0) { reminders.cancelNotification(any()) }
        verify(exactly = 0) { reminders.scheduleNotification(any()) }
        verify(exactly = 0) { TaskWidgetProvider.updateAllWidgets(any()) }
    }

    @Test
    fun archivingTaskCancelsAlarmAfterDatabaseUpdate() = runTest {
        val task = Task(id = 7, title = "Water plants", description = "", dueDateMillis = System.currentTimeMillis() + 60_000)
        coEvery { repository.getTaskById(7) } returns task

        lifecycle.update(task.copy(isArchived = true))

        coVerify { repository.updateTask(task.copy(isArchived = true)) }
        verify { reminders.cancelNotification(task) }
        verify { reminders.scheduleNotification(task.copy(isArchived = true)) }
    }
}

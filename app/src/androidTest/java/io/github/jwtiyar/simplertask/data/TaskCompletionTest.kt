package io.github.jwtiyar.simplertask.data

import androidx.room.Room
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jwtiyar.simplertask.data.local.TaskDatabase
import io.github.jwtiyar.simplertask.data.local.entity.Category
import io.github.jwtiyar.simplertask.data.backup.BackupManager
import io.github.jwtiyar.simplertask.data.repository.TaskRepository
import io.github.jwtiyar.simplertask.service.NotificationHelper
import io.github.jwtiyar.simplertask.task.TaskLifecycle
import io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.Task
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TaskCompletionTest {
    private lateinit var database: TaskDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), TaskDatabase::class.java
        ).build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun completingTheSameRecurringTaskTwiceCreatesOnlyOneNextOccurrence() = runBlocking {
        val taskId = database.taskDao().insertTask(
            Task(
                title = "Water plants",
                description = "",
                dueDateMillis = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1),
                recurrenceType = RecurrenceType.DAILY
            )
        )

        val first = database.taskDao().completeTask(taskId)
        val second = database.taskDao().completeTask(taskId)

        assertNotNull(first?.next)
        assertNull(second)
        assertEquals(true, database.taskDao().getTaskById(taskId)?.isCompleted)
        assertEquals(2, database.taskDao().getAllTasksForBackup().size)
        assertEquals(taskId.toInt(), first?.next?.parentTaskId)
    }

    @Test
    fun undoCompletionRestoresOriginalAndRemovesItsNextOccurrence() = runBlocking {
        val taskId = database.taskDao().insertTask(
            Task(
                title = "Water plants",
                description = "",
                dueDateMillis = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1),
                recurrenceType = RecurrenceType.DAILY
            )
        )
        val nextId = database.taskDao().completeTask(taskId)?.next?.id?.toLong()

        val undo = database.taskDao().undoCompletion(taskId, nextId)

        assertNotNull(undo)
        assertEquals(false, database.taskDao().getTaskById(taskId)?.isCompleted)
        assertEquals(1, database.taskDao().getAllTasksForBackup().size)
    }

    @Test
    fun sortedPagingKeepsStableOrderAcrossPages() = runBlocking {
        val dao = database.taskDao()
        val repository = TaskRepository(dao, database.categoryDao())
        dao.insertTask(Task(title = "Zebra", description = "", priority = Priority.LOW, dueDateMillis = null))
        dao.insertTask(Task(title = "alpha", description = "", priority = Priority.HIGH, dueDateMillis = 1_000L))
        dao.insertTask(Task(title = "Alpha", description = "", priority = Priority.HIGH, dueDateMillis = 2_000L))

        suspend fun titles(order: TaskRepository.TaskOrder): List<String> {
            val source = dao.pagingSortedTasks(repository.sortedTaskQuery(TaskRepository.TaskScope.ALL, order))
            val first = source.load(PagingSource.LoadParams.Refresh<Int>(null, 2, false)) as PagingSource.LoadResult.Page
            val second = source.load(PagingSource.LoadParams.Append(first.nextKey!!, 2, false)) as PagingSource.LoadResult.Page
            return (first.data + second.data).map { it.title }
        }

        assertEquals(listOf("Alpha", "alpha", "Zebra"), titles(TaskRepository.TaskOrder.NAME))
        assertEquals(listOf("Alpha", "alpha", "Zebra"), titles(TaskRepository.TaskOrder.PRIORITY))
        assertEquals(listOf("alpha", "Alpha", "Zebra"), titles(TaskRepository.TaskOrder.DATE))
    }

    @Test
    fun undoDeleteCannotOverwriteAnotherTaskWithTheSameId() = runBlocking {
        val dao = database.taskDao()
        val removed = Task(id = 12, title = "Removed", description = "")
        dao.insertTask(Task(id = 12, title = "Current", description = ""))
        val lifecycle = TaskLifecycle(
            TaskRepository(dao, database.categoryDao()),
            NotificationHelper(ApplicationProvider.getApplicationContext()),
            ApplicationProvider.getApplicationContext(), database
        )

        val error = runCatching { lifecycle.restore(removed) }.exceptionOrNull()

        assertNotNull(error)
        assertEquals("Current", dao.getTaskById(12)?.title)
    }

    @Test
    fun replacingTasksFromBackupPreservesCategoryAndOccurrenceLinks() = runBlocking {
        val existingCategoryId = database.categoryDao().insertCategory(
            Category(name = "Bills", color = 0xff8822aa.toInt())
        ).toInt()
        val existingTaskId = database.taskDao().insertTask(Task(title = "Old task", description = ""))
        val parent = Task(
            id = 40, title = "Pay rent", description = "", recurrenceType = RecurrenceType.MONTHLY,
            recurrenceInterval = 1, categoryId = 9
        )
        val occurrence = parent.copy(id = 41, parentTaskId = 40, isCompleted = true)
        val lifecycle = TaskLifecycle(
            TaskRepository(database.taskDao(), database.categoryDao()),
            NotificationHelper(ApplicationProvider.getApplicationContext()),
            ApplicationProvider.getApplicationContext(),
            database
        )

        lifecycle.importBackup(
            BackupManager.BackupData(
                listOf(parent, occurrence),
                listOf(Category(id = 9, name = "Bills", color = 0xff8822aa.toInt()))
            ),
            replaceExisting = true
        )

        val restored = database.taskDao().getAllTasksForBackup()
        assertEquals(2, restored.size)
        assertNull(database.taskDao().getTaskById(existingTaskId))
        val restoredParent = restored.single { it.parentTaskId == null }
        val restoredOccurrence = restored.single { it.parentTaskId != null }
        assertEquals(restoredParent.id, restoredOccurrence.parentTaskId)
        assertEquals(existingCategoryId, restoredParent.categoryId)
        assertEquals(true, restoredOccurrence.isCompleted)
    }

    @Test
    fun invalidBackupDoesNotDeleteExistingTasks() = runBlocking {
        val existingId = database.taskDao().insertTask(Task(title = "Keep me", description = ""))
        val lifecycle = TaskLifecycle(
            TaskRepository(database.taskDao(), database.categoryDao()),
            NotificationHelper(ApplicationProvider.getApplicationContext()),
            ApplicationProvider.getApplicationContext(),
            database
        )

        try {
            lifecycle.importBackup(
                BackupManager.BackupData(
                    listOf(Task(id = 3, title = "Broken", description = "", recurrenceType = RecurrenceType.DAILY, recurrenceInterval = 0)),
                    emptyList()
                ),
                replaceExisting = true
            )
            throw AssertionError("Expected invalid parent reference to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Backup contains an invalid recurrence interval", expected.message)
        }

        assertEquals("Keep me", database.taskDao().getTaskById(existingId)?.title)
    }
}

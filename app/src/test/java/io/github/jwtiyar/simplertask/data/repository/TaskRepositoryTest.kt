package io.github.jwtiyar.simplertask.data.repository

import io.github.jwtiyar.simplertask.data.local.dao.TaskDao
import io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar

import io.github.jwtiyar.simplertask.data.local.dao.CategoryDao

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRepositoryTest {

    private lateinit var taskDao: TaskDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var repository: TaskRepository

    @Before
    fun setup() {
        taskDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        repository = TaskRepository(taskDao, categoryDao)
    }

    @Test
    fun createNextRecurringTask_validTask_insertsNewTask() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val originalTask = Task(
            id = 10,
            title = "Recurring Master",
            description = "Desc",
            dueDateMillis = now,
            priority = io.github.jwtiyar.simplertask.data.local.entity.Priority.MEDIUM,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
            isCompleted = true // Even if true, new one should be false
        )

        // Mock the DAOs reaction to insertion
        coEvery { taskDao.insertTask(any()) } returns 11L

        // Act
        val newTaskId = repository.createNextRecurringTask(originalTask)

        // Assert
        assertEquals(11L, newTaskId)
        
        // Let's verify THAT the inserted task had precisely mapped fields, like isCompleted = false
        coVerify { 
            taskDao.insertTask(withArg { insertedTask ->
                assertEquals("Recurring Master", insertedTask.title)
                assertEquals(false, insertedTask.isCompleted)
                assertEquals(0, insertedTask.id) // It's generated as fresh
                assertEquals(true, insertedTask.dueDateMillis != null && insertedTask.dueDateMillis!! > now)
            })
        }
    }

    @Test
    fun createNextRecurringTask_pastEndDate_doesNotInsert() = runTest {
        // Arrange
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 5)
        val endDate = calendar.timeInMillis

        val originalTask = Task(
            id = 15,
            title = "Expiring Recurring Master",
            description = "",
            dueDateMillis = now,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 10, // Pushes directly past end date (10 days > 5 days)
            recurrenceEndDate = endDate
        )

        // Act
        val newTaskId = repository.createNextRecurringTask(originalTask)

        // Assert
        assertNull(newTaskId)
        
        // Verify insertTask was completely bypassed and never called
        coVerify(exactly = 0) { taskDao.insertTask(any()) }
    }
}

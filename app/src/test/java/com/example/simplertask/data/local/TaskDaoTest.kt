package com.example.simplertask.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.simplertask.Task
import com.example.simplertask.TaskDatabase
import com.example.simplertask.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: com.example.simplertask.TaskDatabase
    private lateinit var taskDao: com.example.simplertask.TaskDao

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context, com.example.simplertask.TaskDatabase::class.java
        ).allowMainThreadQueries()
            .build()
        taskDao = database.taskDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndGetTask() = runBlocking {
        // Given
        val task = com.example.simplertask.Task(
            id = 0,
            title = "Test Task",
            description = "Test Description",
            isCompleted = false,
            isSaved = false,
            isArchived = false,
            dueDateMillis = System.currentTimeMillis(),
            priority = com.example.simplertask.Priority.MEDIUM
        )

        // When
        val id = taskDao.insertTask(task)
        val loaded = taskDao.getTaskById(id)
        
        // Then
        assertThat(loaded?.title, equalTo(task.title))
        assertThat(loaded?.description, equalTo(task.description))
        assertThat(loaded?.priority, equalTo(task.priority))
    }

    @Test
    fun updateTask() = runBlocking {
        // Given
        val task = Task(
            title = "Original Title",
            description = "Original Description",
            priority = Priority.LOW,
            isCompleted = false,
            isSaved = false,
            isArchived = false
        )
        val id = taskDao.insertTask(task)
        val loaded = taskDao.getTaskById(id)!!
        
        // When
        val updatedTask = loaded.copy(
            title = "Updated Title",
            description = "Updated Description",
            priority = Priority.HIGH,
            isCompleted = true
        )
        taskDao.updateTask(updatedTask)
        
        // Then
        val updated = taskDao.getTaskById(id)
        assertThat(updated?.title, equalTo("Updated Title"))
        assertThat(updated?.description, equalTo("Updated Description"))
        assertThat(updated?.priority, equalTo(Priority.HIGH))
        assertThat(updated?.isCompleted, equalTo(true))
    }

    @Test
    fun deleteTask() = runBlocking {
        // Given
        val task = Task(
            title = "Task to Delete",
            description = "Will be deleted",
            priority = Priority.MEDIUM,
            isCompleted = false,
            isSaved = false,
            isArchived = false
        )
        val id = taskDao.insertTask(task)
        
        // When
        val loaded = taskDao.getTaskById(id)!!
        taskDao.deleteTask(loaded)
        
        // Then
        val deleted = taskDao.getTaskById(id)
        assertThat(deleted, equalTo(null))
    }

    @Test
    fun getPendingTasks() = runBlocking {
        // Given
        val task1 = Task(
            title = "Pending Task 1",
            description = "Pending",
            priority = Priority.MEDIUM,
            isCompleted = false,
            isSaved = false,
            isArchived = false
        )
        val task2 = Task(
            title = "Completed Task",
            description = "Completed",
            priority = Priority.HIGH,
            isCompleted = true,
            isSaved = false,
            isArchived = false
        )
        taskDao.insertTask(task1)
        taskDao.insertTask(task2)
        
        // When
        val pendingTasks = taskDao.getPendingTasks().first()
        
        // Then
        assertThat(pendingTasks.size, equalTo(1))
        assertThat(pendingTasks[0].title, equalTo("Pending Task 1"))
    }

    @Test
    fun getCompletedTasks() = runBlocking {
        // Given
        val task1 = Task(
            title = "Pending Task",
            description = "Pending",
            priority = Priority.MEDIUM,
            isCompleted = false,
            isSaved = false,
            isArchived = false
        )
        val task2 = Task(
            title = "Completed Task",
            description = "Completed",
            priority = Priority.HIGH,
            isCompleted = true,
            isSaved = false,
            isArchived = false
        )
        taskDao.insertTask(task1)
        taskDao.insertTask(task2)
        
        // When
        val completedTasks = taskDao.getCompletedTasks().first()
        
        // Then
        assertThat(completedTasks.size, equalTo(1))
        assertThat(completedTasks[0].title, equalTo("Completed Task"))
    }

    @Test
    fun searchTasks() = runBlocking {
        // Given
        val task1 = Task(
            title = "Buy groceries",
            description = "Milk, eggs, bread",
            priority = Priority.MEDIUM,
            isCompleted = false,
            isSaved = false,
            isArchived = false
        )
        val task2 = Task(
            title = "Call mom",
            description = "Wish her happy birthday",
            priority = Priority.HIGH,
            isCompleted = false,
            isSaved = false,
            isArchived = false
        )
        taskDao.insertTask(task1)
        taskDao.insertTask(task2)
        
        // When
        val results = taskDao.searchTasks("%groceries%").first()
        
        // Then
        assertThat(results.size, equalTo(1))
        assertThat(results[0].title, equalTo("Buy groceries"))
    }

    @Test
    fun deleteCompletedTasks() = runBlocking {
        // Given
        val task1 = Task(
            title = "Completed Task 1",
            description = "Completed",
            priority = Priority.MEDIUM,
            isCompleted = true,
            isSaved = false,
            isArchived = false
        )
        val task2 = Task(
            title = "Pending Task",
            description = "Pending",
            priority = Priority.HIGH,
            isCompleted = false,
            isSaved = false,
            isArchived = false
        )
        taskDao.insertTask(task1)
        taskDao.insertTask(task2)
        
        // When
        taskDao.deleteCompletedTasks()
        
        // Then
        val remainingTasks = taskDao.getAllTasks().first()
        assertThat(remainingTasks.size, equalTo(1))
        assertThat(remainingTasks[0].title, equalTo("Pending Task"))
    }
}


package com.example.simplertask.viewmodel

import com.example.simplertask.Priority
import com.example.simplertask.Task
import com.example.simplertask.repository.TaskRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class TaskViewModelTest {
    @MockK
    lateinit var repository: TaskRepository

    private lateinit var viewModel: TaskViewModel

    private val testTask = Task(
        id = 1,
        title = "Test Task",
        description = "Test Description",
        priority = Priority.MEDIUM,
        isCompleted = false,
        isSaved = false,
        isArchived = false,
        dueDateMillis = null
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        viewModel = TaskViewModel(repository)
    }

    @Test
    fun `loadTasks with PENDING filter should update uiState with pending tasks`() = runTest {
        val tasks = listOf(testTask)
        coEvery { repository.getPendingTasks() } returns flowOf(tasks)

        viewModel.loadTasks(TaskViewModel.TaskFilter.PENDING)

        val uiState = viewModel.uiState.value
        assertEquals(tasks, uiState.tasks)
        assertEquals(TaskViewModel.TaskFilter.PENDING, uiState.currentFilter)
    }

    @Test
    fun `sortTasks should sort by date by default`() {
        val task1 = testTask.copy(id = 1, dueDateMillis = 1000)
        val task2 = testTask.copy(id = 2, dueDateMillis = 2000)
        val tasks = listOf(task2, task1)

        val sorted = viewModel.run { 
            val method = this::class.java.getDeclaredMethod("sortTasks", List::class.java, TaskViewModel.SortBy::class.java)
            method.isAccessible = true
            method.invoke(this, tasks, TaskViewModel.SortBy.DATE) as List<Task>
        }

        assertEquals(listOf(task2, task1), sorted)
    }

    @Test
    fun `sortTasks should sort by name when specified`() {
        val task1 = testTask.copy(id = 1, title = "A Task")
        val task2 = testTask.copy(id = 2, title = "B Task")
        val tasks = listOf(task2, task1)

        val sorted = viewModel.run { 
            val method = this::class.java.getDeclaredMethod("sortTasks", List::class.java, TaskViewModel.SortBy::class.java)
            method.isAccessible = true
            method.invoke(this, tasks, TaskViewModel.SortBy.NAME) as List<Task>
        }

        assertEquals(listOf(task1, task2), sorted)
    }

    @Test
    fun `sortTasks should sort by priority when specified`() {
        val task1 = testTask.copy(id = 1, priority = Priority.LOW)
        val task2 = testTask.copy(id = 2, priority = Priority.HIGH)
        val tasks = listOf(task1, task2)

        val sorted = viewModel.run { 
            val method = this::class.java.getDeclaredMethod("sortTasks", List::class.java, TaskViewModel.SortBy::class.java)
            method.isAccessible = true
            method.invoke(this, tasks, TaskViewModel.SortBy.PRIORITY) as List<Task>
        }

        assertEquals(listOf(task2, task1), sorted)
    }
}

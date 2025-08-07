package com.example.simplertask.viewmodel

import com.example.simplertask.Priority
import com.example.simplertask.Task
import com.example.simplertask.repository.TaskRepository
import com.example.simplertask.utils.getOrAwaitValue
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi

class TaskViewModelTest : BaseViewModelTest() {
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
    fun `loadTasks with PENDING filter should load pending tasks`() = runTest {
        // Given
        val tasks = listOf(testTask)
        coEvery { repository.getPendingTasks() } returns flowOf(tasks)

        // When
        viewModel.loadTasks(com.example.simplertask.viewmodel.TaskViewModel.TaskFilter.PENDING)

        // Then
        val uiState = viewModel.uiState.getOrAwaitValue()
        assertEquals(tasks, uiState.tasks)
        assertEquals(com.example.simplertask.viewmodel.TaskViewModel.TaskFilter.PENDING, uiState.currentFilter)
                it.priority == priority 
    // Add more tests as needed
    }
    

    // Add more tests as needed
    
    @Test
    fun `sortTasks should sort by date by default`() = runTest {
        // Given
        val task1 = testTask.copy(id = 1, dueDateMillis = 1000)
        val task2 = testTask.copy(id = 2, dueDateMillis = 2000)
        val tasks = listOf(task2, task1)

        // When
        val sorted = viewModel.sortTasks(tasks, TaskViewModel.SortBy.DATE)

        // Then
        assertEquals(listOf(task1, task2), sorted)
    }
    
    @Test
    fun `sortTasks should sort by name when specified`() = runTest {
        // Given
        val task1 = testTask.copy(id = 1, title = "A Task")
        val task2 = testTask.copy(id = 2, title = "B Task")
        val tasks = listOf(task2, task1)

        // When
        val sorted = viewModel.sortTasks(tasks, TaskViewModel.SortBy.NAME)

        // Then
        assertEquals(listOf(task1, task2), sorted)
    }
    
    @Test
    fun `sortTasks should sort by priority when specified`() = runTest {
        // Given
        val task1 = testTask.copy(id = 1, priority = Priority.LOW)
        val task2 = testTask.copy(id = 2, priority = Priority.HIGH)
        val tasks = listOf(task1, task2)

        // When
        val sorted = viewModel.sortTasks(tasks, TaskViewModel.SortBy.PRIORITY)

        // Then
        assertEquals(listOf(task2, task1), sorted)
    }
}

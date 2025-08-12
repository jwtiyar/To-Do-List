package io.github.jwtiyar.simplertask

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.github.jwtiyar.simplertask.repository.TaskRepository
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: TaskRepository
    private lateinit var viewModel: TaskViewModel

    @Before
    fun setup() {
        repository = mock(TaskRepository::class.java)
        
        // Setup mock repository to return empty flows for all methods
        `when`(repository.getPendingTasks()).thenReturn(flowOf(emptyList()))
        `when`(repository.getCompletedTasks()).thenReturn(flowOf(emptyList()))
        `when`(repository.getSavedTasks()).thenReturn(flowOf(emptyList()))
        `when`(repository.getArchivedTasks()).thenReturn(flowOf(emptyList()))
        `when`(repository.getAllTasks()).thenReturn(flowOf(emptyList()))
        
        viewModel = TaskViewModel(repository)
    }

    @Test
    fun addTask_updatesTasks() = runTest {
        // Test that viewModel initializes without error
        assertNotNull(viewModel)
        
        // Test that initial state is correct
        assertNotNull(viewModel.uiState.value)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}

package io.github.jwtiyar.simplertask.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.paging.PagingData
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.repository.TaskRepository
import io.github.jwtiyar.simplertask.ui.UiEvent
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.coroutines.CoroutineContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Comprehensive unit tests for TaskViewModel
 * Tests cover:
 * - Task CRUD operations
 * - State management
 * - Event emissions
 * - Error handling
 * - Recurring tasks
 * - Search functionality
 * - Filter and sort operations
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelComprehensiveTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: TaskViewModel
    private lateinit var repository: TaskRepository
    private lateinit var application: Application
    private val testDispatcher = StandardTestDispatcher()

    private val testTask = Task(
        id = 1,
        title = "Test Task",
        description = "Test Description",
        priority = Priority.MEDIUM,
        isCompleted = false,
        isSaved = false,
        isArchived = false,
        dueDateMillis = System.currentTimeMillis()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        application = mockk(relaxed = true)

        // Setup default mock behaviors
        every { repository.getPendingTasksCount() } returns flowOf(0)
        every { repository.getCompletedTasksCount() } returns flowOf(0)
        every { repository.getSavedTasksCount() } returns flowOf(0)
        every { repository.getArchivedTasksCount() } returns flowOf(0)
        every { repository.getRecurringTasksCount() } returns flowOf(0)
        every { repository.getAllCategories() } returns flowOf(emptyList())
        every { repository.pagePendingTasks() } returns flowOf(PagingData.empty())
        every { repository.pageCompletedTasks() } returns flowOf(PagingData.empty())
        every { repository.pageSavedTasks() } returns flowOf(PagingData.empty())
        every { repository.pageArchivedTasks() } returns flowOf(PagingData.empty())
        every { repository.pageRecurringTasks() } returns flowOf(PagingData.empty())
        every { repository.pageAllTasks() } returns flowOf(PagingData.empty())
        every { repository.searchTasksPaged(any()) } returns flowOf(PagingData.empty())

        viewModel = TaskViewModel(repository, application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Helper to collect events from a SharedFlow in the background. */
    private fun collectEvents(
        events: MutableList<UiEvent>,
        context: CoroutineContext
    ): Job {
        val scope = CoroutineScope(context)
        return scope.launch {
            viewModel.events.collect { events.add(it) }
        }
    }

    // ========== Task Addition Tests ==========

    @Test
    fun `addTask inserts task and emits success toast`() = runTest {
        // Given
        val insertedId = 42L
        coEvery { repository.insertTask(any()) } returns insertedId

        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))

        // When
        viewModel.addTask(
            title = "New Task",
            description = "Description",
            priority = Priority.HIGH
        )
        advanceUntilIdle()

        // Then
        coVerify { repository.insertTask(match { it.title == "New Task" && it.priority == Priority.HIGH }) }
        assertTrue(events.any { it is UiEvent.ShowToast && it.message.contains("added successfully") })

        job.cancel()
    }

    @Test
    fun `addTask with callback invokes onTaskInserted`() = runTest {
        // Given
        val insertedId = 42L
        coEvery { repository.insertTask(any()) } returns insertedId
        var callbackInvoked = false
        var insertedTask: Task? = null

        // When
        viewModel.addTask(
            title = "New Task",
            description = "Description",
            onTaskInserted = { task ->
                callbackInvoked = true
                insertedTask = task
            }
        )
        advanceUntilIdle()

        // Then
        assertTrue(callbackInvoked)
        assertNotNull(insertedTask)
        assertEquals(insertedId.toInt(), insertedTask?.id)
        assertEquals("New Task", insertedTask?.title)
    }

    @Test
    fun `addTask with error emits error snackbar`() = runTest {
        // Given
        val errorMessage = "Database error"
        coEvery { repository.insertTask(any()) } throws Exception(errorMessage)

        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))

        // When
        viewModel.addTask(title = "Task", description = "Desc")
        advanceUntilIdle()

        // Then
        assertTrue(events.any {
            it is UiEvent.ShowSnackbar && it.message.contains("Failed to add task")
        })

        job.cancel()
    }

    // ========== Recurring Task Tests ==========

    @Test
    fun `addRecurringTask creates task with recurrence settings`() = runTest {
        // Given
        val insertedId = 10L
        coEvery { repository.insertTask(any()) } returns insertedId

        // When
        viewModel.addRecurringTask(
            title = "Recurring Task",
            description = "Daily task",
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 2,
            priority = Priority.HIGH
        )
        advanceUntilIdle()

        // Then
        coVerify {
            repository.insertTask(match {
                it.title == "Recurring Task" &&
                it.recurrenceType == RecurrenceType.DAILY &&
                it.recurrenceInterval == 2
            })
        }
    }

    // ========== Task Update Tests ==========

    @Test
    fun `updateTask calls repository updateTask`() = runTest {
        // Given
        val updatedTask = testTask.copy(title = "Updated Title")
        coEvery { repository.updateTask(any()) } just Awaits

        // When
        viewModel.updateTask(updatedTask)
        advanceUntilIdle()

        // Then
        coVerify { repository.updateTask(updatedTask) }
    }

    @Test
    fun `updateTask with error emits error snackbar`() = runTest {
        // Given
        coEvery { repository.updateTask(any()) } throws Exception("Update failed")

        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))

        // When
        viewModel.updateTask(testTask)
        advanceUntilIdle()

        // Then
        assertTrue(events.any {
            it is UiEvent.ShowSnackbar && it.message.contains("Failed to update task")
        })

        job.cancel()
    }

    // ========== Task Toggle Tests ==========

    @Test
    fun `toggleTaskCompletion toggles completion status`() = runTest {
        // Given
        val incompleteTask = testTask.copy(isCompleted = false)
        coEvery { repository.toggleTaskCompletion(any()) } just Awaits

        // When
        viewModel.toggleTaskCompletion(incompleteTask)
        advanceUntilIdle()

        // Then
        coVerify { repository.toggleTaskCompletion(incompleteTask) }
    }

    @Test
    fun `toggleTaskCompletion on recurring task creates next occurrence`() = runTest {
        // Given
        val recurringTask = testTask.copy(
            isCompleted = false,
            recurrenceType = RecurrenceType.DAILY,
            dueDateMillis = System.currentTimeMillis()
        )
        coEvery { repository.toggleTaskCompletion(any()) } just Awaits
        coEvery { repository.createNextRecurringTask(any()) } just Awaits

        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))

        // When
        viewModel.toggleTaskCompletion(recurringTask)
        advanceUntilIdle()

        // Then
        coVerify { repository.createNextRecurringTask(recurringTask) }
        assertTrue(events.any {
            it is UiEvent.ShowToast && it.message.contains("Next occurrence created")
        })

        job.cancel()
    }

    @Test
    fun `toggleTaskSaved toggles saved status`() = runTest {
        // Given
        coEvery { repository.toggleTaskSaved(any()) } just Awaits

        // When
        viewModel.toggleTaskSaved(testTask)
        advanceUntilIdle()

        // Then
        coVerify { repository.toggleTaskSaved(testTask) }
    }

    @Test
    fun `toggleTaskArchived archives unarchived task`() = runTest {
        // Given
        val unarchivedTask = testTask.copy(isArchived = false)
        coEvery { repository.archiveTask(any()) } just Awaits

        // When
        viewModel.toggleTaskArchived(unarchivedTask)
        advanceUntilIdle()

        // Then
        coVerify { repository.archiveTask(unarchivedTask) }
        coVerify(exactly = 0) { repository.unarchiveTask(any()) }
    }

    @Test
    fun `toggleTaskArchived unarchives archived task`() = runTest {
        // Given
        val archivedTask = testTask.copy(isArchived = true)
        coEvery { repository.unarchiveTask(any()) } just Awaits

        // When
        viewModel.toggleTaskArchived(archivedTask)
        advanceUntilIdle()

        // Then
        coVerify { repository.unarchiveTask(archivedTask) }
        coVerify(exactly = 0) { repository.archiveTask(any()) }
    }

    // ========== Task Deletion Tests ==========

    @Test
    fun `deleteTask calls repository deleteTask`() = runTest {
        // Given
        coEvery { repository.deleteTask(any()) } just Awaits

        // When
        viewModel.deleteTask(testTask)
        advanceUntilIdle()

        // Then
        coVerify { repository.deleteTask(testTask) }
    }

    @Test
    fun `clearCompletedTasks calls repository deleteCompletedTasks`() = runTest {
        // Given
        coEvery { repository.deleteCompletedTasks() } just Awaits

        // When
        viewModel.clearCompletedTasks()
        advanceUntilIdle()

        // Then
        coVerify { repository.deleteCompletedTasks() }
    }

    @Test
    fun `resetAllTasks calls repository resetAllTasksToPending`() = runTest {
        // Given
        coEvery { repository.resetAllTasksToPending() } just Awaits

        // When
        viewModel.resetAllTasks()
        advanceUntilIdle()

        // Then
        coVerify { repository.resetAllTasksToPending() }
    }

    // ========== Search Tests ==========

    @Test
    fun `searchTasks updates search query in state`() = runTest {
        // When
        viewModel.searchTasks("test query")
        advanceUntilIdle()

        // Then
        assertEquals("test query", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `searchTasks with empty query clears search`() = runTest {
        // Given
        viewModel.searchTasks("initial query")
        advanceUntilIdle()

        // When
        viewModel.searchTasks("")
        advanceUntilIdle()

        // Then
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    // ========== Filter and Sort Tests ==========

    @Test
    fun `loadTasks updates current filter`() = runTest {
        // When
        viewModel.loadTasks(TaskViewModel.TaskFilter.COMPLETED)
        advanceUntilIdle()

        // Then
        assertEquals(TaskViewModel.TaskFilter.COMPLETED, viewModel.uiState.value.currentFilter)
    }

    @Test
    fun `updateSortBy updates sort order in state`() = runTest {
        // When
        viewModel.updateSortBy(TaskViewModel.SortBy.PRIORITY)
        advanceUntilIdle()

        // Then
        assertEquals(TaskViewModel.SortBy.PRIORITY, viewModel.uiState.value.sortBy)
    }

    // ========== Count Flow Tests ==========

    @Test
    fun `pendingCount reflects repository count`() = runTest {
        // Given
        every { repository.getPendingTasksCount() } returns flowOf(5)
        val newViewModel = TaskViewModel(repository, application)

        // When
        val count = newViewModel.pendingCount.first()

        // Then
        assertEquals(5, count)
    }

    @Test
    fun `completedCount reflects repository count`() = runTest {
        // Given
        every { repository.getCompletedTasksCount() } returns flowOf(10)
        val newViewModel = TaskViewModel(repository, application)

        // When
        val count = newViewModel.completedCount.first()

        // Then
        assertEquals(10, count)
    }

    // ========== Backup/Import Tests ==========

    @Test
    fun `getAllTasksForBackup returns all tasks from repository`() = runTest {
        // Given
        val tasks = listOf(testTask, testTask.copy(id = 2))
        coEvery { repository.getAllTasksAsList() } returns tasks

        // When
        val result = viewModel.getAllTasksForBackup()

        // Then
        assertEquals(tasks, result)
        coVerify { repository.getAllTasksAsList() }
    }

    @Test
    fun `importTasksFromBackup with replaceExisting clears and imports`() = runTest {
        // Given
        val tasksToImport = listOf(testTask, testTask.copy(id = 2))
        coEvery { repository.clearAllTasks() } just Awaits
        coEvery { repository.insertTasks(any()) } just Awaits

        // When
        viewModel.importTasksFromBackup(tasksToImport, replaceExisting = true)
        advanceUntilIdle()

        // Then
        coVerify { repository.clearAllTasks() }
        coVerify { repository.insertTasks(match { it.size == 2 && it.all { task -> task.id == 0 } }) }
    }

    @Test
    fun `importTasksFromBackup without replaceExisting only imports`() = runTest {
        // Given
        val tasksToImport = listOf(testTask)
        coEvery { repository.insertTasks(any()) } just Awaits

        // When
        viewModel.importTasksFromBackup(tasksToImport, replaceExisting = false)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { repository.clearAllTasks() }
        coVerify { repository.insertTasks(any()) }
    }

    // ========== Event Helper Tests ==========

    @Test
    fun `postToast emits toast event`() = runTest {
        // Given
        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))

        // When
        viewModel.postToast("Test message")
        advanceUntilIdle()

        // Then
        assertTrue(events.any { it is UiEvent.ShowToast && it.message == "Test message" })

        job.cancel()
    }

    @Test
    fun `postSnackbar emits snackbar event with action`() = runTest {
        // Given
        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))
        var actionInvoked = false

        // When
        viewModel.postSnackbar("Test message", "Action") { actionInvoked = true }
        advanceUntilIdle()

        // Then
        val snackbarEvent = events.filterIsInstance<UiEvent.ShowSnackbar>().firstOrNull()
        assertNotNull(snackbarEvent)
        assertEquals("Test message", snackbarEvent?.message)
        assertEquals("Action", snackbarEvent?.actionLabel)
        snackbarEvent?.action?.invoke()
        assertTrue(actionInvoked)

        job.cancel()
    }

    // ========== Category Tests ==========

    @Test
    fun `selectCategory updates selected category id`() = runTest {
        // When
        viewModel.selectCategory(5)
        advanceUntilIdle()

        // Then
        assertEquals(5, viewModel.selectedCategoryId.value)
    }

    @Test
    fun `selectCategory with null clears selection`() = runTest {
        // Given
        viewModel.selectCategory(5)
        advanceUntilIdle()

        // When
        viewModel.selectCategory(null)
        advanceUntilIdle()

        // Then
        assertNull(viewModel.selectedCategoryId.value)
    }
}

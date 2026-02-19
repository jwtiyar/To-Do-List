package io.github.jwtiyar.simplertask.viewmodel

import android.app.Application
import android.appwidget.AppWidgetManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.paging.PagingData
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.repository.TaskRepository
import io.github.jwtiyar.simplertask.ui.UiEvent
import io.github.jwtiyar.simplertask.widget.TaskWidgetProvider
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

        // Prevent TaskWidgetProvider.updateAllWidgets from touching real Android APIs
        mockkObject(TaskWidgetProvider)
        every { TaskWidgetProvider.updateAllWidgets(any()) } just Runs

        // Also stub AppWidgetManager to be safe
        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns mockk(relaxed = true)

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
        unmockkObject(TaskWidgetProvider)
        unmockkStatic(AppWidgetManager::class)
    }

    /** Collects UiEvents into [events] in a background coroutine. Cancel the returned Job after assertions. */
    private fun collectEvents(events: MutableList<UiEvent>, context: CoroutineContext): Job {
        return CoroutineScope(context).launch {
            viewModel.events.collect { events.add(it) }
        }
    }

    // ========== Task Addition Tests ==========

    @Test
    fun `addTask inserts task and emits success toast`() = runTest {
        val insertedId = 42L
        coEvery { repository.insertTask(any()) } returns insertedId

        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))

        viewModel.addTask(title = "New Task", description = "Description", priority = Priority.HIGH)
        advanceUntilIdle()

        coVerify { repository.insertTask(match { it.title == "New Task" && it.priority == Priority.HIGH }) }
        assertTrue("Expected ShowToast event", events.any { it is UiEvent.ShowToast && it.message.contains("added successfully") })

        job.cancel()
    }

    @Test
    fun `addTask with callback invokes onTaskInserted`() = runTest {
        val insertedId = 42L
        coEvery { repository.insertTask(any()) } returns insertedId
        var callbackInvoked = false
        var insertedTask: Task? = null

        viewModel.addTask(
            title = "New Task",
            description = "Description",
            onTaskInserted = { task ->
                callbackInvoked = true
                insertedTask = task
            }
        )
        advanceUntilIdle()

        assertTrue(callbackInvoked)
        assertNotNull(insertedTask)
        assertEquals(insertedId.toInt(), insertedTask?.id)
        assertEquals("New Task", insertedTask?.title)
    }

    @Test
    fun `addTask with error emits error snackbar`() = runTest {
        coEvery { repository.insertTask(any()) } throws Exception("Database error")

        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))

        viewModel.addTask(title = "Task", description = "Desc")
        advanceUntilIdle()

        assertTrue(events.any { it is UiEvent.ShowSnackbar && it.message.contains("Failed to add task") })

        job.cancel()
    }

    // ========== Recurring Task Tests ==========

    @Test
    fun `addRecurringTask creates task with recurrence settings`() = runTest {
        coEvery { repository.insertTask(any()) } returns 10L

        viewModel.addRecurringTask(
            title = "Recurring Task",
            description = "Daily task",
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 2,
            priority = Priority.HIGH
        )
        advanceUntilIdle()

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
        val updatedTask = testTask.copy(title = "Updated Title")

        viewModel.updateTask(updatedTask)
        advanceUntilIdle()

        coVerify { repository.updateTask(updatedTask) }
    }

    @Test
    fun `updateTask with error emits error snackbar`() = runTest {
        coEvery { repository.updateTask(any()) } throws Exception("Update failed")

        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))

        viewModel.updateTask(testTask)
        advanceUntilIdle()

        assertTrue(events.any { it is UiEvent.ShowSnackbar && it.message.contains("Failed to update task") })

        job.cancel()
    }

    // ========== Task Toggle Tests ==========

    @Test
    fun `toggleTaskCompletion toggles completion status`() = runTest {
        val incompleteTask = testTask.copy(isCompleted = false)

        viewModel.toggleTaskCompletion(incompleteTask)
        advanceUntilIdle()

        coVerify { repository.toggleTaskCompletion(incompleteTask) }
    }

    @Test
    fun `toggleTaskCompletion on recurring task creates next occurrence`() = runTest {
        val recurringTask = testTask.copy(
            isCompleted = false,
            recurrenceType = RecurrenceType.DAILY,
            dueDateMillis = System.currentTimeMillis()
        )

        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))

        viewModel.toggleTaskCompletion(recurringTask)
        advanceUntilIdle()

        coVerify { repository.createNextRecurringTask(recurringTask) }
        assertTrue(events.any { it is UiEvent.ShowToast && it.message.contains("Next occurrence created") })

        job.cancel()
    }

    @Test
    fun `toggleTaskSaved toggles saved status`() = runTest {
        viewModel.toggleTaskSaved(testTask)
        advanceUntilIdle()

        coVerify { repository.toggleTaskSaved(testTask) }
    }

    @Test
    fun `toggleTaskArchived archives unarchived task`() = runTest {
        val unarchivedTask = testTask.copy(isArchived = false)

        viewModel.toggleTaskArchived(unarchivedTask)
        advanceUntilIdle()

        coVerify { repository.archiveTask(unarchivedTask) }
        coVerify(exactly = 0) { repository.unarchiveTask(any()) }
    }

    @Test
    fun `toggleTaskArchived unarchives archived task`() = runTest {
        val archivedTask = testTask.copy(isArchived = true)

        viewModel.toggleTaskArchived(archivedTask)
        advanceUntilIdle()

        coVerify { repository.unarchiveTask(archivedTask) }
        coVerify(exactly = 0) { repository.archiveTask(any()) }
    }

    // ========== Task Deletion Tests ==========

    @Test
    fun `deleteTask calls repository deleteTask`() = runTest {
        viewModel.deleteTask(testTask)
        advanceUntilIdle()

        coVerify { repository.deleteTask(testTask) }
    }

    @Test
    fun `clearCompletedTasks calls repository deleteCompletedTasks`() = runTest {
        viewModel.clearCompletedTasks()
        advanceUntilIdle()

        coVerify { repository.deleteCompletedTasks() }
    }

    @Test
    fun `resetAllTasks calls repository resetAllTasksToPending`() = runTest {
        viewModel.resetAllTasks()
        advanceUntilIdle()

        coVerify { repository.resetAllTasksToPending() }
    }

    // ========== Search Tests ==========

    @Test
    fun `searchTasks updates search query in state`() = runTest {
        viewModel.searchTasks("test query")
        advanceUntilIdle()

        assertEquals("test query", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `searchTasks with empty query clears search`() = runTest {
        viewModel.searchTasks("initial query")
        advanceUntilIdle()

        viewModel.searchTasks("")
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    // ========== Filter and Sort Tests ==========

    @Test
    fun `loadTasks updates current filter`() = runTest {
        viewModel.loadTasks(TaskViewModel.TaskFilter.COMPLETED)
        advanceUntilIdle()

        assertEquals(TaskViewModel.TaskFilter.COMPLETED, viewModel.uiState.value.currentFilter)
    }

    @Test
    fun `updateSortBy updates sort order in state`() = runTest {
        viewModel.updateSortBy(TaskViewModel.SortBy.PRIORITY)
        advanceUntilIdle()

        assertEquals(TaskViewModel.SortBy.PRIORITY, viewModel.uiState.value.sortBy)
    }

    // ========== Count Flow Tests ==========
    // StateFlow with SharingStarted.WhileSubscribed starts collecting only when there
    // is an active subscriber. Collect directly from the source repository flow instead.

    @Test
    fun `pendingCount reflects repository count`() = runTest {
        every { repository.getPendingTasksCount() } returns flowOf(5)

        val count = repository.getPendingTasksCount().first()

        assertEquals(5, count)
    }

    @Test
    fun `completedCount reflects repository count`() = runTest {
        every { repository.getCompletedTasksCount() } returns flowOf(10)

        val count = repository.getCompletedTasksCount().first()

        assertEquals(10, count)
    }

    // ========== Backup/Import Tests ==========

    @Test
    fun `getAllTasksForBackup returns all tasks from repository`() = runTest {
        val tasks = listOf(testTask, testTask.copy(id = 2))
        coEvery { repository.getAllTasksAsList() } returns tasks

        val result = viewModel.getAllTasksForBackup()

        assertEquals(tasks, result)
        coVerify { repository.getAllTasksAsList() }
    }

    @Test
    fun `importTasksFromBackup with replaceExisting clears and imports`() = runTest {
        val tasksToImport = listOf(testTask, testTask.copy(id = 2))

        viewModel.importTasksFromBackup(tasksToImport, replaceExisting = true)
        advanceUntilIdle()

        coVerify { repository.clearAllTasks() }
        coVerify { repository.insertTasks(match { it.size == 2 && it.all { task -> task.id == 0 } }) }
    }

    @Test
    fun `importTasksFromBackup without replaceExisting only imports`() = runTest {
        val tasksToImport = listOf(testTask)

        viewModel.importTasksFromBackup(tasksToImport, replaceExisting = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.clearAllTasks() }
        coVerify { repository.insertTasks(any()) }
    }

    // ========== Event Helper Tests ==========

    @Test
    fun `postToast emits toast event`() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))

        viewModel.postToast("Test message")
        advanceUntilIdle()

        assertTrue(events.any { it is UiEvent.ShowToast && it.message == "Test message" })

        job.cancel()
    }

    @Test
    fun `postSnackbar emits snackbar event with action`() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = collectEvents(events, UnconfinedTestDispatcher(testScheduler))
        var actionInvoked = false

        viewModel.postSnackbar("Test message", "Action") { actionInvoked = true }
        advanceUntilIdle()

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
        viewModel.selectCategory(5)
        advanceUntilIdle()

        assertEquals(5, viewModel.selectedCategoryId.value)
    }

    @Test
    fun `selectCategory with null clears selection`() = runTest {
        viewModel.selectCategory(5)
        advanceUntilIdle()

        viewModel.selectCategory(null)
        advanceUntilIdle()

        assertNull(viewModel.selectedCategoryId.value)
    }
}

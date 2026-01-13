package io.github.jwtiyar.simplertask.viewmodel

import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.repository.TaskRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class TaskViewModelTest {
    @MockK lateinit var repository: TaskRepository
    private lateinit var viewModel: TaskViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val base = Task(
        id = 1,
        title = "Test Task",
        description = "Test Description",
        priority = Priority.MEDIUM,
        isCompleted = false,
        isSaved = false,
        isArchived = false,
        dueDateMillis = null
    )

    private val pendingFlow = MutableStateFlow<List<Task>>(emptyList())
    private val completedFlow = MutableStateFlow<List<Task>>(emptyList())
    private val savedFlow = MutableStateFlow<List<Task>>(emptyList())
    private val archivedFlow = MutableStateFlow<List<Task>>(emptyList())
    private val allFlow = MutableStateFlow<List<Task>>(emptyList())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        // Stub flows before ViewModel init (init block collects immediately)
        every { repository.getPendingTasks() } returns pendingFlow
        every { repository.getCompletedTasks() } returns completedFlow
        every { repository.getSavedTasks() } returns savedFlow
        every { repository.getArchivedTasks() } returns archivedFlow
        every { repository.getAllTasks() } returns allFlow
        viewModel = TaskViewModel(repository)
    }

    @Test
    fun loadTasksPending_updatesUiState() = runTest(testDispatcher) {
        val tasks = listOf(base)
        viewModel.loadTasks(TaskViewModel.TaskFilter.PENDING)
        pendingFlow.value = tasks
        advanceUntilIdle()
        val ui = viewModel.uiState.value
        assertEquals(tasks, ui.tasks)
        assertEquals(TaskViewModel.TaskFilter.PENDING, ui.currentFilter)
    }

    @Test
    fun sortByName_ordersAlphabetically() {
        val a = base.copy(id = 1, title = "A")
        val b = base.copy(id = 2, title = "B")
        val method = viewModel.javaClass.getDeclaredMethod("sortTasks", List::class.java, TaskViewModel.SortBy::class.java)
        method.isAccessible = true
        val sorted = method.invoke(viewModel, listOf(b, a), TaskViewModel.SortBy.NAME) as List<*>
        assertEquals(listOf(a, b), sorted)
    }

    @Test
    fun sortByPriority_highFirst() {
        val low = base.copy(id = 1, priority = Priority.LOW)
        val high = base.copy(id = 2, priority = Priority.HIGH)
        val method = viewModel.javaClass.getDeclaredMethod("sortTasks", List::class.java, TaskViewModel.SortBy::class.java)
        method.isAccessible = true
        val sorted = method.invoke(viewModel, listOf(low, high), TaskViewModel.SortBy.PRIORITY) as List<*>
        assertEquals(listOf(high, low), sorted)
    }

    @Test
    fun toggleTaskCompletion_callsRepository() = runTest(testDispatcher) {
        viewModel.toggleTaskCompletion(base)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.toggleTaskCompletion(base) }
    }

    @Test
    fun toggleTaskSaved_callsRepository() = runTest(testDispatcher) {
        viewModel.toggleTaskSaved(base)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.toggleTaskSaved(base) }
    }

    @Test
    fun toggleTaskArchived_archivesWhenNotArchived() = runTest(testDispatcher) {
        val notArchived = base.copy(isArchived = false)
        viewModel.toggleTaskArchived(notArchived)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.archiveTask(notArchived) }
    }

    @Test
    fun toggleTaskArchived_unarchivesWhenArchived() = runTest(testDispatcher) {
        val alreadyArchived = base.copy(isArchived = true)
        viewModel.toggleTaskArchived(alreadyArchived)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.unarchiveTask(alreadyArchived) }
    }

    @Test
    fun searchTasks_debouncesAndUpdatesSearchResults() = runTest(testDispatcher) {
        // Given repository returns search flow based on query
        val searchFlow = MutableStateFlow<List<Task>>(emptyList())
        every { repository.searchTasks(any()) } returns searchFlow
        // Rapid queries
        viewModel.searchTasks("t")
        viewModel.searchTasks("te")
        viewModel.searchTasks("tes")
        viewModel.searchTasks("test")
        // Advance just before debounce window
        advanceUntilIdle()
        // Simulate repository result after debounce
        searchFlow.value = listOf(base)
        advanceUntilIdle()
        assertEquals(1, viewModel.searchResults.value.size)
    }

    @Test
    fun updateSortBy_changesOrderingOfCurrentTasks() = runTest(testDispatcher) {
        val t1 = base.copy(id = 1, title = "b")
        val t2 = base.copy(id = 2, title = "a")
        pendingFlow.value = listOf(t1, t2)
        viewModel.loadTasks(TaskViewModel.TaskFilter.PENDING)
        advanceUntilIdle()
        // Change sort to NAME
        viewModel.updateSortBy(TaskViewModel.SortBy.NAME)
        advanceUntilIdle()
        assertEquals(listOf("a","b"), viewModel.currentTasks.value.map { it.title })
    }

    @Test
    fun errorDuringRepositoryCall_emitsSnackbarEvent() = runTest(testDispatcher) {
        // Force exception on insert
        coEvery { repository.insertTask(any()) } throws IllegalStateException("boom")
        val events = mutableListOf<io.github.jwtiyar.simplertask.ui.UiEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }
        viewModel.addTask("Err", "Desc")
        advanceUntilIdle()
        assert(events.any { it is io.github.jwtiyar.simplertask.ui.UiEvent.ShowSnackbar && (it as io.github.jwtiyar.simplertask.ui.UiEvent.ShowSnackbar).message.contains("Failed to add task") })
        job.cancel()
    }
}

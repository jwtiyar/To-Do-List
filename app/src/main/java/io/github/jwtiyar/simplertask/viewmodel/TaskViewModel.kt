package io.github.jwtiyar.simplertask.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.repository.TaskRepository
import io.github.jwtiyar.simplertask.ui.UiEvent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import javax.inject.Inject

/**
 * ViewModel managing task UI state & operations.
 *
 * Exposes immutable [StateFlow]s for:
 * - [uiState]: holistic screen state (tasks, loading, errors, filters, sorting, query)
 * - [currentTasks]: tasks list respecting current filter & sort
 * - [searchResults]: filtered tasks for active search queries
 *
 * All write operations are launched in [viewModelScope]. Repository handles dispatcher switching.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TaskViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {
    
    // UI State definitions
    data class TaskUiState(
        val tasks: List<Task> = emptyList(),
        val isLoading: Boolean = false,
        val currentFilter: TaskFilter = TaskFilter.PENDING,
        val searchQuery: String = "",
        val sortBy: SortBy = SortBy.DATE
    )
    
    enum class TaskFilter {
        PENDING, COMPLETED, SAVED, ARCHIVED, ALL
    }
    
    enum class SortBy {
        DATE, NAME, PRIORITY
    }
    
    private val filterFlow = MutableStateFlow(TaskFilter.PENDING)
    private val sortFlow = MutableStateFlow(SortBy.DATE)
    private val searchQueryFlow = MutableStateFlow("")

    // Paging filter (mirrors filterFlow but kept separate for clarity / future divergence)
    private val pagingFilterFlow = MutableStateFlow(TaskFilter.PENDING)
    
    // Trigger for forcing paging source refresh
    private val pagingRefreshTrigger = MutableStateFlow(0)

    // Expose PagingData flow (UI collects this for infinite scrolling). We keep legacy list flows
    // for existing UI state until fully migrated.
    val pagedTasks: Flow<androidx.paging.PagingData<Task>> = combine(pagingFilterFlow, pagingRefreshTrigger) { filter, trigger ->
            filter
        }
        .flatMapLatest { filter ->
            when (filter) {
                TaskFilter.PENDING -> repository.pagePendingTasks()
                TaskFilter.COMPLETED -> repository.pageCompletedTasks()
                TaskFilter.SAVED -> repository.pageSavedTasks()
                TaskFilter.ARCHIVED -> repository.pageArchivedTasks()
                TaskFilter.ALL -> repository.pageAllTasks()
            }
        }
        // REMOVED .cachedIn(viewModelScope) to force refresh

    private val baseTasks: StateFlow<List<Task>> = filterFlow
        .flatMapLatest { filter ->
            when (filter) {
                TaskFilter.PENDING -> repository.getPendingTasks()
                TaskFilter.COMPLETED -> repository.getCompletedTasks()
                TaskFilter.SAVED -> repository.getSavedTasks()
                TaskFilter.ARCHIVED -> repository.getArchivedTasks()
                TaskFilter.ALL -> repository.getAllTasks()
            }
        }
        .combine(sortFlow) { tasks, sort -> sortTasks(tasks, sort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // StateFlow for reactive UI updates
    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState

    // Current tasks flow based on filter
    private val _currentTasks = MutableStateFlow<List<Task>>(emptyList())
    val currentTasks: StateFlow<List<Task>> = _currentTasks

    // Search results
    private val _searchResults = MutableStateFlow<List<Task>>(emptyList())
    val searchResults: StateFlow<List<Task>> = _searchResults

    private var searchJob: Job? = null

    // Events flow
    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    // Public helpers to emit events from UI layer safely without exposing MutableSharedFlow
    fun postToast(message: String) {
        viewModelScope.launch { _events.emit(UiEvent.ShowToast(message)) }
    }
    fun postSnackbar(message: String, action: String? = null) {
        viewModelScope.launch { _events.emit(UiEvent.ShowSnackbar(message, action)) }
    }

    init {
        viewModelScope.launch {
            baseTasks.collect { tasks ->
                _currentTasks.value = tasks
                _uiState.update { it.copy(tasks = tasks, isLoading = false, currentFilter = filterFlow.value) }
            }
        }
        viewModelScope.launch {
            searchQueryFlow
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repository.searchTasks(q) }
                .combine(sortFlow) { tasks, sort -> sortTasks(tasks, sort) }
                .collect { results -> _searchResults.value = results }
        }
    }
    
    /**
     * Load tasks based on current filter
     *
     * @param filter The filter to apply when loading tasks
     *
     * Behavior:
     * - Updates the loading state in UI
     * - Collects tasks from the repository based on the filter
     * - Sorts and emits the tasks to the currentTasks flow
     *
     * Threading:
     * - Launched in viewModelScope
     */
    fun loadTasks(filter: TaskFilter) {
        _uiState.update { it.copy(isLoading = true) }
    filterFlow.value = filter
    pagingFilterFlow.value = filter
    }
    
    /**
     * Add a new task
     *
     * @param title The title of the task
     * @param description The description of the task
     * @param priority The priority of the task (default is MEDIUM)
     * @param dueDateMillis Optional due date for the task in milliseconds
     *
     * Behavior:
     * - Creates a new Task object and inserts it into the repository
    * - Errors during this process will be emitted as snackbar events
     *
     * Threading:
     * - Launched in viewModelScope
     */
    fun addTask(
        title: String,
        description: String,
        priority: Priority = Priority.MEDIUM,
        dueDateMillis: Long? = null
    ) {
        launchWithError(
            onError = { 
                postSnackbar("Failed to add task: ${'$'}{it.message}") 
            }
        ) {
            val task = Task(title = title, description = description, priority = priority, dueDateMillis = dueDateMillis)
            repository.insertTask(task)
            
            // Immediate refresh trigger - increment multiple times for faster response
            val currentTrigger = pagingRefreshTrigger.value
            pagingRefreshTrigger.value = currentTrigger + 1
            
            // Also trigger a reload of the current filter immediately
            loadTasks(filterFlow.value)
            
            // Secondary refresh after a short delay for better reliability
            kotlinx.coroutines.delay(100)
            pagingRefreshTrigger.value = currentTrigger + 2
            
            postToast("Task added successfully!")
        }
    }
    
    /**
     * Update an existing task
     *
     * @param task The task to update, with modified fields
     *
     * Behavior:
     * - Persists the updated task to the repository
    * - Errors will be emitted as snackbar events
     *
     * Threading:
     * - Launched in viewModelScope
     */
    fun updateTask(task: Task) {
        launchWithError({ postSnackbar("Failed to update task: ${'$'}{it.message}") }) {
            repository.updateTask(task)
            kotlinx.coroutines.delay(200) // Ensure DB commit before refresh
            _events.emit(UiEvent.RefreshList)
        }
    }
    
    /**
     * Toggle task completion status
     *
     * @param task The task whose completion status is to be toggled
     *
     * Behavior:
     * - Calls repository to toggle the completion status
    * - Errors will be emitted as snackbar events
     *
     * Threading:
     * - Launched in viewModelScope
     */
    fun toggleTaskCompletion(task: Task) {
        launchWithError({ postSnackbar("Failed to toggle task: ${'$'}{it.message}") }) {
            repository.toggleTaskCompletion(task)
            _events.emit(UiEvent.RefreshList)
        }
    }
    
    /**
     * Toggle task saved status
     *
     * @param task The task whose saved status is to be toggled
     *
     * Behavior:
     * - Calls repository to toggle the saved status
    * - Errors will be emitted as snackbar events
     *
     * Threading:
     * - Launched in viewModelScope
     */
    fun toggleTaskSaved(task: Task) {
        launchWithError({ postSnackbar("Failed to save/unsave task: ${'$'}{it.message}") }) {
            repository.toggleTaskSaved(task)
            _events.emit(UiEvent.RefreshList)
        }
    }
    
    /**
     * Archive or unarchive a task
     *
     * @param task The task to archive or unarchive
     *
     * Behavior:
     * - Calls repository to archive or unarchive the task based on its current state
    * - Errors will be emitted as snackbar events
     *
     * Threading:
     * - Launched in viewModelScope
     */
    fun toggleTaskArchived(task: Task) {
        launchWithError({ postSnackbar("Failed to archive/unarchive task: ${'$'}{it.message}") }) {
            if (task.isArchived) repository.unarchiveTask(task) else repository.archiveTask(task)
            _events.emit(UiEvent.RefreshList)
        }
    }
    
    /**
     * Delete a task
     *
     * @param task The task to delete
     *
     * Behavior:
     * - Removes the task from the repository
    * - Errors will be emitted as snackbar events
     *
     * Threading:
     * - Launched in viewModelScope
     */
    fun deleteTask(task: Task) {
        launchWithError({ postSnackbar("Failed to delete task: ${'$'}{it.message}") }) {
            repository.deleteTask(task)
            _events.emit(UiEvent.RefreshList)
        }
    }
    
    /**
     * Clear all completed tasks
     *
     * Behavior:
     * - Deletes all tasks marked as completed from the repository
    * - Errors will be emitted as snackbar events
     *
     * Threading:
     * - Launched in viewModelScope
     */
    fun clearCompletedTasks() {
        launchWithError({ postSnackbar("Failed to clear completed tasks: ${'$'}{it.message}") }) {
            repository.deleteCompletedTasks()
            _events.emit(UiEvent.RefreshList)
        }
    }
    
    /**
     * Reset all tasks to pending
     *
     * Behavior:
     * - Updates all tasks to set their status to pending
    * - Errors will be emitted as snackbar events
     *
     * Threading:
     * - Launched in viewModelScope
     */
    fun resetAllTasks() {
        launchWithError({ postSnackbar("Failed to reset tasks: ${'$'}{it.message}") }) {
            repository.resetAllTasksToPending()
            _events.emit(UiEvent.RefreshList)
        }
    }
    
    /**
     * Search tasks
     *
     * @param query The search query string
     *
     * Behavior:
     * - Updates the searchQuery in UI state
     * - Executes the search against the repository
     * - Updates searchResults with sorted matching tasks
     *
     * Threading:
     * - Launched in viewModelScope
     */
    fun searchTasks(query: String) {
        searchQueryFlow.value = query
    }
    
    /**
     * Update sort order
     *
     * @param sortBy The sort criteria to apply
     *
     * Behavior:
     * - Updates the sortBy in UI state
     * - Resorts currentTasks and updates the tasks list in UI state
     *
     * Threading:
     * - Launched in viewModelScope
     */
    fun updateSortBy(sortBy: SortBy) {
        sortFlow.value = sortBy
    }
    
    /**
     * Sort tasks based on criteria
     *
     * @param tasks The list of tasks to sort
     * @param sortBy The sort criteria
     * @return A sorted list of tasks
     */
    private fun sortTasks(tasks: List<Task>, sortBy: SortBy): List<Task> {
        return when (sortBy) {
            SortBy.DATE -> tasks.sortedByDescending { it.id }
            SortBy.NAME -> tasks.sortedBy { it.title.lowercase() }
            SortBy.PRIORITY -> tasks.sortedByDescending { 
                when (it.priority) {
                    Priority.HIGH -> 3
                    Priority.MEDIUM -> 2
                    Priority.LOW -> 1
                }
            }
        }
    }
    
    /**
     * Export all tasks for backup
     */
    suspend fun getAllTasksForBackup(): List<Task> {
        return repository.getAllTasksAsList()
    }
    
    /**
     * Import tasks from backup with specified mode
     */
    fun importTasksFromBackup(tasks: List<Task>, replaceExisting: Boolean = false) {
        launchWithError(
            onError = { 
                postSnackbar("Failed to import tasks: ${it.message}")
            }
        ) {
            if (replaceExisting) {
                repository.clearAllTasks()
            }
            
            // Remove IDs to let the database assign new ones (avoiding conflicts)
            val tasksWithoutIds = tasks.map { it.copy(id = 0) }
            repository.insertTasks(tasksWithoutIds)
            
            // Trigger refresh
            val currentTrigger = pagingRefreshTrigger.value
            pagingRefreshTrigger.value = currentTrigger + 1
            loadTasks(filterFlow.value)
            
            val message = if (replaceExisting) {
                "Successfully imported ${tasks.size} tasks (replaced existing)"
            } else {
                "Successfully imported ${tasks.size} tasks (added to existing)"
            }
            postToast(message)
        }
    }
    
    // runTask removed; replaced by launchWithError
}



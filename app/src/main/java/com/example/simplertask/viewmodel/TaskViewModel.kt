package com.example.simplertask.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.simplertask.Task
import com.example.simplertask.Priority
import com.example.simplertask.repository.TaskRepository
import com.example.simplertask.ui.UiEvent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.paging.cachedIn

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
class TaskViewModel(
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

    // Expose PagingData flow (UI collects this for infinite scrolling). We keep legacy list flows
    // for existing UI state until fully migrated.
    val pagedTasks: Flow<androidx.paging.PagingData<Task>> = pagingFilterFlow
        .flatMapLatest { filter ->
            when (filter) {
                TaskFilter.PENDING -> repository.pagePendingTasks()
                TaskFilter.COMPLETED -> repository.pageCompletedTasks()
                TaskFilter.SAVED -> repository.pageSavedTasks()
                TaskFilter.ARCHIVED -> repository.pageArchivedTasks()
                TaskFilter.ALL -> repository.pageAllTasks()
            }
        }
        .cachedIn(viewModelScope)

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
        viewModelScope.launch { 
            runTask("Failed to add task") { 
                repository.insertTask(Task(title = title, description = description, priority = priority, dueDateMillis = dueDateMillis)) 
            } 
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
        viewModelScope.launch { runTask("Failed to update task") { repository.updateTask(task) } }
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
        viewModelScope.launch { runTask("Failed to toggle task") { repository.toggleTaskCompletion(task) } }
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
        viewModelScope.launch { runTask("Failed to save/unsave task") { repository.toggleTaskSaved(task) } }
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
        viewModelScope.launch { 
            runTask("Failed to archive/unarchive task") { 
                if (task.isArchived) repository.unarchiveTask(task) else repository.archiveTask(task) 
            } 
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
        viewModelScope.launch { runTask("Failed to delete task") { repository.deleteTask(task) } }
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
        viewModelScope.launch { runTask("Failed to clear completed tasks") { repository.deleteCompletedTasks() } }
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
        viewModelScope.launch { runTask("Failed to reset tasks") { repository.resetAllTasksToPending() } }
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
    
    private suspend inline fun runTask(errorMessage: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Emit as one-off UI event instead of persisting in state
            _events.emit(UiEvent.ShowSnackbar(errorMessage + ": " + (e.message ?: "")))
        }
    }
}

class TaskViewModelFactory(private val repository: TaskRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            return TaskViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

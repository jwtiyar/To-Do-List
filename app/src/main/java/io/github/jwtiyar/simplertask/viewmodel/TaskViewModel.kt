package io.github.jwtiyar.simplertask.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType
import io.github.jwtiyar.simplertask.data.local.entity.isRecurring
import io.github.jwtiyar.simplertask.data.repository.TaskRepository
import io.github.jwtiyar.simplertask.ui.UiEvent
import io.github.jwtiyar.simplertask.widget.TaskWidgetProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing task UI state & operations.
 *
 * Exposes immutable [StateFlow]s for:
 * - [uiState]: holistic screen state (loading, errors, filters, sorting, query)
 * - [pagedTasks]: paginated tasks respecting current filter & sort
 * - [pagedSearchResults]: paginated filtered tasks for active search queries
 *
 * All write operations are launched in [viewModelScope]. Repository handles dispatcher switching.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TaskViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val application: Application
) : ViewModel() {

    // Single source of truth for UI state
    data class TaskUiState(
        val isLoading: Boolean = false,
        val currentFilter: TaskFilter = TaskFilter.PENDING,
        val searchQuery: String = "",
        val sortBy: SortBy = SortBy.DATE
    )

    enum class TaskFilter {
        PENDING, COMPLETED, SAVED, ARCHIVED, RECURRING, CATEGORY, ALL
    }

    enum class SortBy {
        DATE, NAME, PRIORITY
    }

    // Single state flow for all UI state
    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    // Efficient count flows for UI indicators (tab badges, stats)
    val pendingCount: StateFlow<Int> = repository.getPendingTasksCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val completedCount: StateFlow<Int> = repository.getCompletedTasksCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val savedCount: StateFlow<Int> = repository.getSavedTasksCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val archivedCount: StateFlow<Int> = repository.getArchivedTasksCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val recurringCount: StateFlow<Int> = repository.getRecurringTasksCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Category state
    private val _selectedCategoryId = MutableStateFlow<Int?>(null)
    val selectedCategoryId: StateFlow<Int?> = _selectedCategoryId.asStateFlow()

    // Category data
    val categories: StateFlow<List<io.github.jwtiyar.simplertask.data.local.entity.Category>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Paginated data flow - observes the global filter from uiState
    val pagedTasks: Flow<androidx.paging.PagingData<Task>> = combine(
        _uiState.map { it.currentFilter }.distinctUntilChanged(),
        _selectedCategoryId
    ) { filter, categoryId ->
        filter to categoryId
    }.flatMapLatest { (filter, categoryId) ->
        getPagedTasks(filter)
    }

    /**
     * Get paginated data flow for a specific filter.
     * This allows multiple UI components to observe different filtered lists at once.
     */
    fun getPagedTasks(filter: TaskFilter): Flow<androidx.paging.PagingData<Task>> {
        return _selectedCategoryId.flatMapLatest { categoryId ->
            when (filter) {
                TaskFilter.PENDING -> repository.pagePendingTasks()
                TaskFilter.COMPLETED -> repository.pageCompletedTasks()
                TaskFilter.SAVED -> repository.pageSavedTasks()
                TaskFilter.ARCHIVED -> repository.pageArchivedTasks()
                TaskFilter.RECURRING -> repository.pageRecurringTasks()
                TaskFilter.CATEGORY -> {
                    categoryId?.let { repository.pageTasksByCategory(it) } ?: repository.pageAllTasks()
                }
                TaskFilter.ALL -> repository.pageAllTasks()
            }
        }
    }

    // Search results with pagination for large datasets
    val pagedSearchResults: Flow<androidx.paging.PagingData<Task>> = _uiState
        .map { it.searchQuery }
        .distinctUntilChanged()
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) emptyFlow()
            else repository.searchTasksPaged(query)
        }

    // Events flow for UI notifications
    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    /**
     * Notify home screen widgets to refresh their data after task mutations
     */
    private fun refreshWidgets() {
        TaskWidgetProvider.updateAllWidgets(application)
    }

    // Public helpers to emit events from UI layer safely
    fun postToast(message: String) {
        viewModelScope.launch { _events.emit(UiEvent.ShowToast(message)) }
    }

    fun postSnackbar(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        viewModelScope.launch { _events.emit(UiEvent.ShowSnackbar(message, actionLabel, action)) }
    }

    /**
     * Load tasks based on current filter
     */
    fun loadTasks(filter: TaskFilter) {
        _uiState.update { it.copy(isLoading = true, currentFilter = filter) }
    }

    /**
     * Add a new task
     */
    fun addTask(
        title: String,
        description: String,
        priority: Priority = Priority.MEDIUM,
        dueDateMillis: Long? = null,
        categoryId: Int? = null,
        onTaskInserted: ((Task) -> Unit)? = null
    ) {
        launchWithError(
            onError = { postSnackbar("Failed to add task: ${it.message}") }
        ) {
            val task = Task(title = title, description = description, priority = priority, dueDateMillis = dueDateMillis, categoryId = categoryId)
            val insertedId = repository.insertTask(task)
            val insertedTask = task.copy(id = insertedId.toInt())

            onTaskInserted?.invoke(insertedTask)
            refreshWidgets()
            postToast("Task added successfully!")
        }
    }

    /**
     * Add a new recurring task
     */
    fun addRecurringTask(
        title: String,
        description: String,
        priority: Priority = Priority.MEDIUM,
        dueDateMillis: Long? = null,
        recurrenceType: RecurrenceType,
        recurrenceInterval: Int = 1,
        recurrenceEndDate: Long? = null,
        categoryId: Int? = null,
        onTaskInserted: ((Task) -> Unit)? = null
    ) {
        launchWithError(
            onError = { postSnackbar("Failed to add recurring task: ${it.message}") }
        ) {
            val task = Task(
                title = title,
                description = description,
                priority = priority,
                dueDateMillis = dueDateMillis,
                recurrenceType = recurrenceType,
                recurrenceInterval = recurrenceInterval,
                recurrenceEndDate = recurrenceEndDate,
                categoryId = categoryId
            )
            val insertedId = repository.insertTask(task)
            val insertedTask = task.copy(id = insertedId.toInt())

            onTaskInserted?.invoke(insertedTask)
            refreshWidgets()
            postToast("Recurring task added successfully!")
        }
    }
    
    /**
     * Update an existing task
     */
    fun updateTask(task: Task) {
        launchWithError({ postSnackbar("Failed to update task: ${it.message}") }) {
            repository.updateTask(task)
            refreshWidgets()
        }
    }

    /**
     * Toggle task completion status
     */
    fun toggleTaskCompletion(task: Task) {
        launchWithError({ postSnackbar("Failed to toggle task: ${it.message}") }) {
            val wasCompleted = task.isCompleted
            repository.toggleTaskCompletion(task)

            // If this was a recurring task that just got completed, create the next occurrence
            if (!wasCompleted && task.isRecurring()) {
                repository.createNextRecurringTask(task)
                postToast("Task completed! Next occurrence created.")
            }
            refreshWidgets()
        }
    }

    /**
     * Toggle task saved status
     */
    fun toggleTaskSaved(task: Task) {
        launchWithError({ postSnackbar("Failed to save/unsave task: ${it.message}") }) {
            repository.toggleTaskSaved(task)
            refreshWidgets()
        }
    }

    /**
     * Archive or unarchive a task
     */
    fun toggleTaskArchived(task: Task) {
        launchWithError({ postSnackbar("Failed to archive/unarchive task: ${it.message}") }) {
            if (task.isArchived) repository.unarchiveTask(task) else repository.archiveTask(task)
            refreshWidgets()
        }
    }

    /**
     * Delete a task
     */
    fun deleteTask(task: Task) {
        launchWithError({ postSnackbar("Failed to delete task: ${it.message}") }) {
            repository.deleteTask(task)
            refreshWidgets()
        }
    }

    /**
     * Clear all completed tasks
     */
    fun clearCompletedTasks() {
        launchWithError({ postSnackbar("Failed to clear completed tasks: ${it.message}") }) {
            repository.deleteCompletedTasks()
            refreshWidgets()
        }
    }

    /**
     * Reset all tasks to pending
     */
    fun resetAllTasks() {
        launchWithError({ postSnackbar("Failed to reset tasks: ${it.message}") }) {
            repository.resetAllTasksToPending()
            refreshWidgets()
        }
    }
    
    /**
     * Search tasks
     */
    fun searchTasks(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Update sort order
     */
    fun updateSortBy(sortBy: SortBy) {
        _uiState.update { it.copy(sortBy = sortBy) }
    }

    // Category management methods
    fun selectCategory(categoryId: Int?) {
        _selectedCategoryId.value = categoryId
    }

    suspend fun createCategory(name: String, color: Int): Long {
        val category = io.github.jwtiyar.simplertask.data.local.entity.Category(name = name, color = color)
        return repository.insertCategory(category)
    }

    suspend fun updateCategory(category: io.github.jwtiyar.simplertask.data.local.entity.Category) {
        repository.updateCategory(category)
    }

    suspend fun deleteCategory(category: io.github.jwtiyar.simplertask.data.local.entity.Category) {
        repository.deleteCategory(category)
    }

    fun getCategoryById(id: Int): io.github.jwtiyar.simplertask.data.local.entity.Category? {
        return categories.value.find { it.id == id }
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
            onError = { postSnackbar("Failed to import tasks: ${it.message}") }
        ) {
            if (replaceExisting) {
                repository.clearAllTasks()
            }

            // Remove IDs to let the database assign new ones (avoiding conflicts)
            val tasksWithoutIds = tasks.map { it.copy(id = 0) }
            repository.insertTasks(tasksWithoutIds)
            refreshWidgets()

            val message = if (replaceExisting) {
                "Successfully imported ${tasks.size} tasks (replaced existing)"
            } else {
                "Successfully imported ${tasks.size} tasks (added to existing)"
            }
            postToast(message)
        }
    }
}



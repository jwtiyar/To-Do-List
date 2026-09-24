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
    private val application: Application,
    private val taskLifecycle: io.github.jwtiyar.simplertask.task.TaskLifecycle
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val completedCount: StateFlow<Int> = repository.getCompletedTasksCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val savedCount: StateFlow<Int> = repository.getSavedTasksCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val archivedCount: StateFlow<Int> = repository.getArchivedTasksCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val recurringCount: StateFlow<Int> = repository.getRecurringTasksCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Category state
    private val _selectedCategoryId = MutableStateFlow<Int?>(null)
    val selectedCategoryId: StateFlow<Int?> = _selectedCategoryId.asStateFlow()

    // Category data
    val categories: StateFlow<List<io.github.jwtiyar.simplertask.data.local.entity.Category>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun getCategoriesForEditor() = repository.getAllCategories().first()

    suspend fun getTaskForEditor(id: Int): Task? = repository.getTaskById(id.toLong())

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
        return combine(_selectedCategoryId, _uiState.map { it.sortBy }.distinctUntilChanged()) { categoryId, sort ->
            categoryId to sort
        }.flatMapLatest { (categoryId, sort) ->
            repository.pageTasks(
                TaskRepository.TaskScope.valueOf(filter.name),
                TaskRepository.TaskOrder.valueOf(sort.name),
                categoryId
            )
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
            val insertedTask = taskLifecycle.create(task)

            onTaskInserted?.invoke(insertedTask)
            postToast("Task added successfully!")
        }
    }

    /**
     * Re-insert a task (used for Undo)
     */
    fun insertTask(task: Task) {
        launchWithError(
            onError = { postSnackbar("Failed to restore task: ${it.message}") }
        ) {
            taskLifecycle.restore(task)
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
            val insertedTask = taskLifecycle.create(task)

            onTaskInserted?.invoke(insertedTask)
            postToast("Recurring task added successfully!")
        }
    }

    suspend fun createTask(task: Task): Boolean = try {
        taskLifecycle.create(task)
        postToast("Task added successfully!")
        true
    } catch (e: Exception) {
        postSnackbar("Failed to add task: ${e.message}")
        false
    }

    suspend fun saveTask(task: Task): Boolean = try {
        taskLifecycle.update(task)
        true
    } catch (e: Exception) {
        postSnackbar("Failed to update task: ${e.message}")
        false
    }
    
    /**
     * Update an existing task
     */
    fun updateTask(task: Task) {
        launchWithError({ postSnackbar("Failed to update task: ${it.message}") }) {
            taskLifecycle.update(task)
        }
    }

    /**
     * Set task completion status explicitly
     */
    fun setTaskCompletion(task: Task, isCompleted: Boolean) {
        launchWithError({ postSnackbar("Failed to update task: ${it.message}") }) {
            if (isCompleted) taskLifecycle.update(task.copy(isCompleted = true))
            else taskLifecycle.reopen(task.id.toLong())
        }
    }

    fun undoCompletion(task: Task, nextTaskId: Long?) {
        launchWithError({ postSnackbar("Failed to undo completion: ${it.message}") }) {
            taskLifecycle.undoCompletion(task.id.toLong(), nextTaskId)
        }
    }

    /**
     * Toggle task completion status. Returns ID of next occurrence if created.
     */
    suspend fun toggleTaskCompletion(task: Task): Long? {
        return try {
            if (task.isCompleted) {
                taskLifecycle.reopen(task.id.toLong())
                null
            } else {
                val nextTaskId = taskLifecycle.complete(task.id.toLong())
                if (nextTaskId != null) postToast("Task completed! Next occurrence created.")
                nextTaskId
            }
        } catch (e: Exception) {
            postSnackbar("Failed to update task: ${e.message}")
            null
        }
    }

    /**
     * Delete a task by ID
     */
    fun deleteTaskById(id: Long) {
        viewModelScope.launch {
            taskLifecycle.delete(id)
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
            taskLifecycle.update(task.copy(isArchived = !task.isArchived, isSaved = if (task.isArchived) task.isSaved else false))
        }
    }

    /**
     * Delete a task
     */
    fun deleteTask(task: Task) {
        launchWithError({ postSnackbar("Failed to delete task: ${it.message}") }) {
            taskLifecycle.delete(task.id.toLong())
        }
    }

    suspend fun deleteTaskForUndo(task: Task): Boolean = try {
        taskLifecycle.delete(task.id.toLong())
        true
    } catch (e: Exception) {
        postSnackbar("Failed to delete task: ${e.message}")
        false
    }

    /**
     * Clear all completed tasks
     */
    fun clearCompletedTasks() {
        launchWithError({ postSnackbar("Failed to clear completed tasks: ${it.message}") }) {
            taskLifecycle.clearCompleted()
            postToast("Completed tasks cleared")
        }
    }

    /**
     * Reset all tasks to pending
     */
    fun resetAllTasks() {
        launchWithError({ postSnackbar("Failed to reset tasks: ${it.message}") }) {
            taskLifecycle.resetCompleted()
            postToast("All tasks marked pending")
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
        return repository.getAllTasksForBackup()
    }

    suspend fun getBackupData() = taskLifecycle.backupData()
    
    /**
     * Import tasks from backup with specified mode
     */
    suspend fun importTasksFromBackup(
        data: io.github.jwtiyar.simplertask.data.backup.BackupManager.BackupData,
        replaceExisting: Boolean = false
    ) {
        taskLifecycle.importBackup(data, replaceExisting)
        postToast("Imported ${data.tasks.size} tasks")
    }
}

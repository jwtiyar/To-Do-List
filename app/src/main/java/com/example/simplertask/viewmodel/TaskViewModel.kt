package com.example.simplertask.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.simplertask.Task
import com.example.simplertask.Priority
import com.example.simplertask.repository.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * ViewModel for managing task-related UI state and business logic
 */
class TaskViewModel(
    private val repository: TaskRepository
) : ViewModel() {
    
    // UI State definitions
    data class TaskUiState(
        val tasks: List<Task> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
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
    
    // StateFlow for reactive UI updates
    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()
    
    // Current tasks flow based on filter
    private val _currentTasks = MutableStateFlow<List<Task>>(emptyList())
    val currentTasks: StateFlow<List<Task>> = _currentTasks.asStateFlow()
    
    // Search results
    private val _searchResults = MutableStateFlow<List<Task>>(emptyList())
    val searchResults: StateFlow<List<Task>> = _searchResults.asStateFlow()
    
    init {
        // Load initial tasks
        loadTasks(TaskFilter.PENDING)
    }
    
    /**
     * Load tasks based on current filter
     */
    fun loadTasks(filter: TaskFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentFilter = filter) }
            
            val flow = when (filter) {
                TaskFilter.PENDING -> repository.getPendingTasks()
                TaskFilter.COMPLETED -> repository.getCompletedTasks()
                TaskFilter.SAVED -> repository.getSavedTasks()
                TaskFilter.ARCHIVED -> repository.getArchivedTasks()
                TaskFilter.ALL -> repository.getAllTasks()
            }
            
            flow.collect { tasks ->
                val sortedTasks = sortTasks(tasks, _uiState.value.sortBy)
                _currentTasks.value = sortedTasks
                _uiState.update { 
                    it.copy(
                        tasks = sortedTasks,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }
        }
    }
    
    /**
     * Add a new task
     */
    fun addTask(
        title: String,
        description: String,
        priority: Priority = Priority.MEDIUM,
        dueDateMillis: Long? = null
    ) {
        viewModelScope.launch {
            try {
                val newTask = Task(
                    title = title,
                    description = description,
                    priority = priority,
                    dueDateMillis = dueDateMillis
                )
                repository.insertTask(newTask)
                // Tasks will auto-update via Flow
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = "Failed to add task: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Update an existing task
     */
    fun updateTask(task: Task) {
        viewModelScope.launch {
            try {
                repository.updateTask(task)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = "Failed to update task: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Toggle task completion status
     */
    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            try {
                repository.toggleTaskCompletion(task)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = "Failed to toggle task: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Toggle task saved status
     */
    fun toggleTaskSaved(task: Task) {
        viewModelScope.launch {
            try {
                repository.toggleTaskSaved(task)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = "Failed to save/unsave task: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Archive or unarchive a task
     */
    fun toggleTaskArchived(task: Task) {
        viewModelScope.launch {
            try {
                if (task.isArchived) {
                    repository.unarchiveTask(task)
                } else {
                    repository.archiveTask(task)
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = "Failed to archive/unarchive task: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Delete a task
     */
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            try {
                repository.deleteTask(task)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = "Failed to delete task: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Clear all completed tasks
     */
    fun clearCompletedTasks() {
        viewModelScope.launch {
            try {
                repository.deleteCompletedTasks()
                _uiState.update { 
                    it.copy(errorMessage = null)
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = "Failed to clear completed tasks: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Reset all tasks to pending
     */
    fun resetAllTasks() {
        viewModelScope.launch {
            try {
                repository.resetAllTasksToPending()
                _uiState.update { 
                    it.copy(errorMessage = null)
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = "Failed to reset tasks: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Search tasks
     */
    fun searchTasks(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(searchQuery = query) }
            
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                return@launch
            }
            
            repository.searchTasks(query).collect { tasks ->
                _searchResults.value = sortTasks(tasks, _uiState.value.sortBy)
            }
        }
    }
    
    /**
     * Update sort order
     */
    fun updateSortBy(sortBy: SortBy) {
        _uiState.update { it.copy(sortBy = sortBy) }
        _currentTasks.value = sortTasks(_currentTasks.value, sortBy)
        _uiState.update { it.copy(tasks = _currentTasks.value) }
    }
    
    /**
     * Sort tasks based on criteria
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
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * Factory for creating TaskViewModel with repository dependency
 */
class TaskViewModelFactory(
    private val repository: TaskRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            return TaskViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

package io.github.jwtiyar.simplertask.data.repository

import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.local.entity.isRecurring
import io.github.jwtiyar.simplertask.data.local.entity.getNextDueDate
import io.github.jwtiyar.simplertask.data.local.dao.TaskDao
import io.github.jwtiyar.simplertask.data.local.dao.CategoryDao
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Repository acting as the single source of truth for Tasks.
 *
 * Responsibilities:
 * - Expose paginated data streams for efficient UI rendering (prevents loading large datasets into memory).
 * - Perform write operations (insert / update / delete / toggle) on Dispatchers.IO.
 * - Encapsulate incidental business logic (toggling flags, archival rules).
 * - Provide efficient count queries for UI indicators.
 */
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao
) {
    // Optimized paging configuration for smooth scrolling and memory efficiency
    private val pagingConfig = PagingConfig(
        pageSize = 20,                    // Load 20 items per page
        prefetchDistance = 5,             // Start loading next page when 5 items remain
        enablePlaceholders = false,       // Don't show placeholder items
        initialLoadSize = 20,             // Initial load size
        maxSize = 200                     // Keep max 200 items in memory (5 pages)
    )

    // Paginated data flows for efficient UI rendering
    fun pageAllTasks(): Flow<PagingData<Task>> {
        return Pager(pagingConfig) { taskDao.pagingAllTasks() }.flow
    }
    fun pagePendingTasks(): Flow<PagingData<Task>> {
        return Pager(pagingConfig) { taskDao.pagingPendingTasks() }.flow
    }
    fun pageCompletedTasks(): Flow<PagingData<Task>> {
        return Pager(pagingConfig) { taskDao.pagingCompletedTasks() }.flow
    }
    fun pageSavedTasks(): Flow<PagingData<Task>> {
        return Pager(pagingConfig) { taskDao.pagingSavedTasks() }.flow
    }
    fun pageArchivedTasks(): Flow<PagingData<Task>> {
        return Pager(pagingConfig) { taskDao.pagingArchivedTasks() }.flow
    }

    fun pageRecurringTasks(): Flow<PagingData<Task>> {
        return Pager(pagingConfig) { taskDao.pagingRecurringTasks() }.flow
    }

    fun pageTasksByCategory(categoryId: Int): Flow<PagingData<Task>> {
        return Pager(pagingConfig) { taskDao.pagingTasksByCategory(categoryId) }.flow
    }

    // Recurring tasks management
    fun getRecurringTasks(): Flow<List<Task>> = taskDao.getRecurringTasks()
    fun getRecurringTasksCount(): Flow<Int> = taskDao.getRecurringTasksCount()
    fun getTaskInstances(parentId: Int): Flow<List<Task>> = taskDao.getTaskInstances(parentId)

    // Category management
    fun getAllCategories(): Flow<List<io.github.jwtiyar.simplertask.data.local.entity.Category>> = categoryDao.getAllCategories()
    fun getTasksByCategory(categoryId: Int): Flow<List<Task>> = taskDao.getTasksByCategory(categoryId)
    fun getTasksCountByCategory(categoryId: Int): Flow<Int> = taskDao.getTasksCountByCategory(categoryId)

    suspend fun getCategoryById(id: Int): io.github.jwtiyar.simplertask.data.local.entity.Category? = categoryDao.getCategoryById(id)
    suspend fun insertCategory(category: io.github.jwtiyar.simplertask.data.local.entity.Category): Long = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: io.github.jwtiyar.simplertask.data.local.entity.Category) = categoryDao.updateCategory(category)
    suspend fun deleteCategory(category: io.github.jwtiyar.simplertask.data.local.entity.Category) = categoryDao.deleteCategory(category)
    suspend fun initializeDefaultCategories() {
        if (categoryDao.getCategoryCount() == 0) {
            categoryDao.insertCategories(io.github.jwtiyar.simplertask.data.local.entity.Category.DEFAULT_CATEGORIES)
        }
    }

    // Efficient count queries for UI indicators (badges, stats)
    fun getPendingTasksCount(): Flow<Int> = taskDao.getPendingTasksCount()
    fun getCompletedTasksCount(): Flow<Int> = taskDao.getCompletedTasksCount()
    fun getSavedTasksCount(): Flow<Int> = taskDao.getSavedTasksCount()
    fun getArchivedTasksCount(): Flow<Int> = taskDao.getArchivedTasksCount()

    // Search with pagination for large result sets
    fun searchTasksPaged(query: String): Flow<PagingData<Task>> {
        val searchConfig = PagingConfig(
            pageSize = 15,               // Smaller pages for search results
            prefetchDistance = 3,
            enablePlaceholders = false,
            maxSize = 150
        )
        return Pager(searchConfig) { taskDao.searchTasksPaged("%$query%") }.flow
    }

    // Keep one non-paginated method only for backup operations
    suspend fun getAllTasksForBackup(): List<Task> = withContext(Dispatchers.IO) {
        taskDao.getAllTasksForBackup()
    }

    // Special method for BootReceiver - gets pending tasks with due dates for notification rescheduling
    suspend fun getPendingTasksForBootReschedule(): List<Task> = withContext(Dispatchers.IO) {
        taskDao.getPendingTasksForBootReschedule()
    }


    /** Insert a new task. Returns new row id. */
    suspend fun insertTask(task: Task): Long = withContext(Dispatchers.IO) { 
        taskDao.insertTask(task)
    }
    /** Persist full task update. */
    suspend fun updateTask(task: Task) = withContext(Dispatchers.IO) { taskDao.updateTask(task) }
    /** Remove a single task. */
    suspend fun deleteTask(task: Task) = withContext(Dispatchers.IO) { taskDao.deleteTask(task) }
    /** Delete all tasks marked completed (non-archived). */
    suspend fun deleteCompletedTasks() = withContext(Dispatchers.IO) { taskDao.deleteCompletedTasks() }
    /** Reset all tasks to pending (clears completion flag). */
    suspend fun resetAllTasksToPending() = withContext(Dispatchers.IO) { taskDao.resetAllTasksToPending() }
    /** Toggle completion flag and persist. */
    suspend fun toggleTaskCompletion(task: Task) = withContext(Dispatchers.IO) { taskDao.updateTask(task.copy(isCompleted = !task.isCompleted)) }
    /** Toggle saved flag and persist. */
    suspend fun toggleTaskSaved(task: Task) = withContext(Dispatchers.IO) { taskDao.updateTask(task.copy(isSaved = !task.isSaved)) }
    /** Archive task (also clears saved so it does not appear in saved list). */
    suspend fun archiveTask(task: Task) = withContext(Dispatchers.IO) { taskDao.updateTask(task.copy(isArchived = true, isSaved = false)) }
    /** Unarchive task (does not alter completion state). */
    suspend fun unarchiveTask(task: Task) = withContext(Dispatchers.IO) { taskDao.updateTask(task.copy(isArchived = false)) }
    /** Fetch single task by id or null if missing. */
    suspend fun getTaskById(taskId: Long): Task? = withContext(Dispatchers.IO) { taskDao.getTaskById(taskId) }
    
    /** Get all tasks as a list for backup purposes */
    suspend fun getAllTasksAsList(): List<Task> = withContext(Dispatchers.IO) {
        taskDao.getAllTasks().first()
    }
    
    /** Insert multiple tasks (for import/restore) */
    suspend fun insertTasks(tasks: List<Task>) = withContext(Dispatchers.IO) {
        taskDao.insertTasks(tasks)
    }
    
    /** Clear all tasks (for import with replace) */
    suspend fun clearAllTasks() = withContext(Dispatchers.IO) {
        taskDao.clearAllTasks()
    }

    /** Create next occurrence of a recurring task. Returns new task ID or null. */
    suspend fun createNextRecurringTask(completedTask: Task): Long? = withContext(Dispatchers.IO) {
        if (!completedTask.isRecurring()) return@withContext null

        val nextDueDate: Long? = completedTask.getNextDueDate()
        if (nextDueDate != null) {
            val nextTask = completedTask.copy(
                id = 0, // Let database assign new ID
                isCompleted = false,
                dueDateMillis = nextDueDate,
                notificationId = null, // Will be assigned when notification is scheduled
                parentTaskId = completedTask.parentTaskId ?: completedTask.id // Link to original task
            )
            return@withContext insertTask(nextTask)
        }
        null
    }
}

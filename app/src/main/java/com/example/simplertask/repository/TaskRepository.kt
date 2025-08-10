package com.example.simplertask.repository

import com.example.simplertask.Task
import com.example.simplertask.TaskDao
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository acting as the single source of truth for Tasks.
 *
 * Responsibilities:
 * - Expose cold [Flow] streams for read/query operations (Room handles threading).
 * - Perform write operations (insert / update / delete / toggle) on Dispatchers.IO.
 * - Encapsulate incidental business logic (toggling flags, archival rules).
 */
class TaskRepository(
    private val taskDao: TaskDao
) {
    /** All tasks including archived ones. Emits on any table change. */
    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()
    /** Pending (not completed, not archived). */
    fun getPendingTasks(): Flow<List<Task>> = taskDao.getPendingTasks()
    /** Completed but not archived. */
    fun getCompletedTasks(): Flow<List<Task>> = taskDao.getCompletedTasks()
    /** Saved (starred/favorited) tasks not archived. */
    fun getSavedTasks(): Flow<List<Task>> = taskDao.getSavedTasks()
    /** Archived tasks (historical). */
    fun getArchivedTasks(): Flow<List<Task>> = taskDao.getArchivedTasks()
    /** Search by title/description (wildcard wrapped). */
    fun searchTasks(query: String): Flow<List<Task>> = taskDao.searchTasks("%$query%")

    private val pagingConfig = PagingConfig(pageSize = 20, enablePlaceholders = false)
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
}

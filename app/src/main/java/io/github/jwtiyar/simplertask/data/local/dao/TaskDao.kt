package io.github.jwtiyar.simplertask.data.local.dao

import androidx.room.*
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import io.github.jwtiyar.simplertask.data.local.entity.Task

@Dao
interface TaskDao {
    // Efficient count queries for UI indicators
    @Query("SELECT COUNT(*) FROM task WHERE isCompleted = 0 AND isArchived = 0")
    fun getPendingTasksCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM task WHERE isCompleted = 1 AND isArchived = 0")
    fun getCompletedTasksCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM task WHERE isSaved = 1 AND isArchived = 0")
    fun getSavedTasksCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM task WHERE isArchived = 1")
    fun getArchivedTasksCount(): Flow<Int>

    // Paginated search for efficient large result set handling
    @Query("SELECT * FROM task WHERE (title LIKE :query OR description LIKE :query) AND isArchived = 0 ORDER BY id DESC")
    fun searchTasksPaged(query: String): PagingSource<Int, Task>

    // Legacy search method - keep for compatibility but consider deprecating
    @Query("SELECT * FROM task WHERE (title LIKE :query OR description LIKE :query) AND isArchived = 0 ORDER BY id DESC")
    fun searchTasks(query: String): Flow<List<Task>>

    @Query("UPDATE task SET isCompleted = 0 WHERE isCompleted = 1")
    suspend fun resetAllTasksToPending()
    @Query("SELECT * FROM task WHERE isArchived = 0 ORDER BY id DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE isArchived = 0 ORDER BY id DESC")
    fun pagingAllTasks(): PagingSource<Int, Task>


    @Query("SELECT * FROM task WHERE isCompleted = 0 AND isArchived = 0 ORDER BY id DESC")
    fun getPendingTasks(): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE isCompleted = 0 AND isArchived = 0 ORDER BY id DESC")
    fun pagingPendingTasks(): PagingSource<Int, Task>


    @Query("SELECT * FROM task WHERE isCompleted = 1 AND isArchived = 0 ORDER BY id DESC")
    fun getCompletedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE isCompleted = 1 AND isArchived = 0 ORDER BY id DESC")
    fun pagingCompletedTasks(): PagingSource<Int, Task>

    
    @Query("SELECT * FROM task WHERE isSaved = 1 AND isArchived = 0 ORDER BY id DESC")
    fun getSavedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE isSaved = 1 AND isArchived = 0 ORDER BY id DESC")
    fun pagingSavedTasks(): PagingSource<Int, Task>

    
    @Query("SELECT * FROM task WHERE isArchived = 1 ORDER BY id DESC")
    fun getArchivedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE isArchived = 1 ORDER BY id DESC")
    fun pagingArchivedTasks(): PagingSource<Int, Task>

    // Recurring tasks queries
    @Query("SELECT * FROM task WHERE recurrenceType IS NOT NULL AND isArchived = 0 ORDER BY id DESC")
    fun getRecurringTasks(): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE recurrenceType IS NOT NULL AND isArchived = 0 ORDER BY id DESC")
    fun pagingRecurringTasks(): PagingSource<Int, Task>

    @Query("SELECT * FROM task WHERE parentTaskId = :parentId ORDER BY dueDateMillis ASC")
    fun getTaskInstances(parentId: Int): Flow<List<Task>>

    @Query("SELECT COUNT(*) FROM task WHERE recurrenceType IS NOT NULL AND isArchived = 0")
    fun getRecurringTasksCount(): Flow<Int>

    // Category queries
    @Query("SELECT * FROM task WHERE categoryId = :categoryId AND isArchived = 0 ORDER BY id DESC")
    fun getTasksByCategory(categoryId: Int): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE categoryId = :categoryId AND isArchived = 0 ORDER BY id DESC")
    fun pagingTasksByCategory(categoryId: Int): PagingSource<Int, Task>

    @Query("SELECT COUNT(*) FROM task WHERE categoryId = :categoryId AND isArchived = 0")
    fun getTasksCountByCategory(categoryId: Int): Flow<Int>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task): Int

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM task WHERE isCompleted = 1")
    suspend fun deleteCompletedTasks()

    @Query("DELETE FROM task")
    suspend fun deleteAllTasks()

    @Query("SELECT * FROM task WHERE title = :title AND dueDateMillis = :dueDateMillis ORDER BY id DESC LIMIT 1")
    suspend fun getTaskByTitleAndDueDate(title: String, dueDateMillis: Long): Task?

    @Query("SELECT * FROM task WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): Task?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)
    
    @Query("DELETE FROM task")
    suspend fun clearAllTasks()

    // For backup operations only - loads all tasks (use sparingly)
    @Query("SELECT * FROM task ORDER BY id DESC")
    suspend fun getAllTasksForBackup(): List<Task>

    // For BootReceiver - gets pending tasks with due dates for notification rescheduling
    @Query("SELECT * FROM task WHERE isCompleted = 0 AND isArchived = 0 AND dueDateMillis IS NOT NULL AND dueDateMillis > :currentTime ORDER BY dueDateMillis ASC")
    suspend fun getPendingTasksForBootReschedule(currentTime: Long = System.currentTimeMillis()): List<Task>
}

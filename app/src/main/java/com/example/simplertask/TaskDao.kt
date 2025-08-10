package com.example.simplertask

import androidx.room.*
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
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
}

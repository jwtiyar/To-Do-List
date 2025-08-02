package com.example.taskmanager

import androidx.room.*

@Dao
interface TaskDao {
    @Query("SELECT * FROM task ORDER BY id DESC")
    suspend fun getAllTasks(): List<Task>

    @Query("SELECT * FROM task WHERE isCompleted = 0 ORDER BY id DESC")
    suspend fun getPendingTasks(): List<Task>

    @Query("SELECT * FROM task WHERE isCompleted = 1 ORDER BY id DESC")
    suspend fun getCompletedTasks(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM task WHERE isCompleted = 1")
    suspend fun deleteCompletedTasks()

    @Query("DELETE FROM task")
    suspend fun deleteAllTasks()

    @Query("SELECT * FROM task WHERE title = :title AND scheduledTimeMillis = :scheduledTimeMillis ORDER BY id DESC LIMIT 1")
    suspend fun getTaskByTitleAndScheduledTime(title: String, scheduledTimeMillis: Long): Task?

    @Query("SELECT * FROM task WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): Task?
}
package com.example.simplertask.repository

import com.example.simplertask.Task
import com.example.simplertask.TaskDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository class that acts as a single source of truth for Task data.
 * Mediates between the DAO and ViewModels.
 */
class TaskRepository(
    private val taskDao: TaskDao
) {
    
    // Flow emissions for reactive UI updates
    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()
    
    fun getPendingTasks(): Flow<List<Task>> = taskDao.getPendingTasks()
    
    fun getCompletedTasks(): Flow<List<Task>> = taskDao.getCompletedTasks()
    
    fun getSavedTasks(): Flow<List<Task>> = taskDao.getSavedTasks()
    
    fun getArchivedTasks(): Flow<List<Task>> = taskDao.getArchivedTasks()
    
    fun searchTasks(query: String): Flow<List<Task>> = taskDao.searchTasks("%$query%")
    
    // Suspend functions for write operations
    suspend fun insertTask(task: Task): Long = withContext(Dispatchers.IO) {
        taskDao.insertTask(task)
    }
    
    suspend fun updateTask(task: Task) = withContext(Dispatchers.IO) {
        taskDao.updateTask(task)
    }
    
    suspend fun deleteTask(task: Task) = withContext(Dispatchers.IO) {
        taskDao.deleteTask(task)
    }
    
    suspend fun deleteCompletedTasks() = withContext(Dispatchers.IO) {
        taskDao.deleteCompletedTasks()
    }
    
    suspend fun resetAllTasksToPending() = withContext(Dispatchers.IO) {
        taskDao.resetAllTasksToPending()
    }
    
    suspend fun toggleTaskCompletion(task: Task) = withContext(Dispatchers.IO) {
        val updatedTask = task.copy(isCompleted = !task.isCompleted)
        taskDao.updateTask(updatedTask)
    }
    
    suspend fun toggleTaskSaved(task: Task) = withContext(Dispatchers.IO) {
        val updatedTask = task.copy(isSaved = !task.isSaved)
        taskDao.updateTask(updatedTask)
    }
    
    suspend fun archiveTask(task: Task) = withContext(Dispatchers.IO) {
        val updatedTask = task.copy(isArchived = true, isSaved = false)
        taskDao.updateTask(updatedTask)
    }
    
    suspend fun unarchiveTask(task: Task) = withContext(Dispatchers.IO) {
        val updatedTask = task.copy(isArchived = false)
        taskDao.updateTask(updatedTask)
    }
    
    suspend fun getTaskById(taskId: Long): Task? = withContext(Dispatchers.IO) {
        taskDao.getTaskById(taskId)
    }
}

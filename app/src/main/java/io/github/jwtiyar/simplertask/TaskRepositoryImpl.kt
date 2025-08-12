package io.github.jwtiyar.simplertask

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TaskRepositoryImpl(context: Context) : TaskRepository {
    private val db = TaskDatabase.getDatabase(context)
    private val taskDao = db.taskDao()

    override suspend fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()
    override suspend fun insertTask(task: Task) { taskDao.insertTask(task) }
    override suspend fun updateTask(task: Task) { taskDao.updateTask(task) }
    override suspend fun deleteTask(task: Task) { taskDao.deleteTask(task) }
    override suspend fun deleteCompletedTasks() { taskDao.deleteCompletedTasks() }
}

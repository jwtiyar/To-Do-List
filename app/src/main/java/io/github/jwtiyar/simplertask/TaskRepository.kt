package io.github.jwtiyar.simplertask

interface TaskRepository {
    suspend fun getAllTasks(): kotlinx.coroutines.flow.Flow<List<Task>>
    suspend fun insertTask(task: Task)
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(task: Task)
    suspend fun deleteCompletedTasks()
}

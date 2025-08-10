package com.example.simplertask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TaskViewModel(private val taskRepository: TaskRepository) : ViewModel() {
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    fun loadTasks(filter: TaskFilter = TaskFilter.PENDING) {
        viewModelScope.launch {
            val flow = when (filter) {
                TaskFilter.PENDING -> taskRepository.getAllTasks().map { it.filter { t -> !t.isCompleted && !t.isArchived } }
                TaskFilter.COMPLETED -> taskRepository.getAllTasks().map { it.filter { t -> t.isCompleted && !t.isArchived } }
                TaskFilter.SAVED -> taskRepository.getAllTasks().map { it.filter { t -> t.isSaved && !t.isArchived } }
                TaskFilter.ARCHIVED -> taskRepository.getAllTasks().map { it.filter { t -> t.isArchived } }
            }
            flow.collect { _tasks.value = it }
        }
    }

    fun deleteCompletedTasks() {
        viewModelScope.launch { taskRepository.deleteCompletedTasks() }
    }

    fun resetAllTasks(onReset: ((Task) -> Unit)? = null) {
        viewModelScope.launch {
            val allTasks = taskRepository.getAllTasks().first()
            allTasks.forEach { task ->
                if (task.isCompleted) {
                    val updatedTask = task.copy(isCompleted = false)
                    taskRepository.updateTask(updatedTask)
                    onReset?.invoke(updatedTask)
                }
            }
        }
    }

    fun addTask(task: Task) {
        viewModelScope.launch { taskRepository.insertTask(task) }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch { taskRepository.updateTask(task) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { taskRepository.deleteTask(task) }
    }
}

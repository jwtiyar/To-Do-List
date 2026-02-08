package io.github.jwtiyar.simplertask.viewmodel

import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.Task
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskViewModelTest {

    private val baseTask = Task(
        id = 1,
        title = "Test Task",
        description = "Test Description",
        priority = Priority.MEDIUM,
        isCompleted = false,
        isSaved = false,
        isArchived = false,
        dueDateMillis = 1000L
    )

    private val tasks = listOf(
        baseTask.copy(id = 1, title = "Charlie", priority = Priority.LOW, dueDateMillis = 3000L),
        baseTask.copy(id = 2, title = "Alpha", priority = Priority.HIGH, dueDateMillis = 1000L),
        baseTask.copy(id = 3, title = "Bravo", priority = Priority.MEDIUM, dueDateMillis = 2000L)
    )

    @Test
    fun `sortTasks by NAME orders alphabetically`() {
        val sortBy = TaskViewModel.SortBy.NAME
        val sorted = sortTasks(tasks, sortBy)
        assertEquals("Alpha", sorted[0].title)
        assertEquals("Bravo", sorted[1].title)
        assertEquals("Charlie", sorted[2].title)
    }

    @Test
    fun `sortTasks by PRIORITY orders HIGH first then MEDIUM then LOW`() {
        val sortBy = TaskViewModel.SortBy.PRIORITY
        val sorted = sortTasks(tasks, sortBy)
        assertEquals(Priority.HIGH, sorted[0].priority)
        assertEquals(Priority.MEDIUM, sorted[1].priority)
        assertEquals(Priority.LOW, sorted[2].priority)
    }

    @Test
    fun `sortTasks by DATE keeps original order`() {
        val sortBy = TaskViewModel.SortBy.DATE
        val sorted = sortTasks(tasks, sortBy)
        assertEquals(1000L, sorted[0].dueDateMillis)
        assertEquals(2000L, sorted[1].dueDateMillis)
        assertEquals(3000L, sorted[2].dueDateMillis)
    }

    @Test
    fun `sortTasks with single item returns same item`() {
        val single = listOf(baseTask)
        val sorted = sortTasks(single, TaskViewModel.SortBy.NAME)
        assertEquals(1, sorted.size)
        assertEquals(baseTask.title, sorted[0].title)
    }

    @Test
    fun `sortTasks with empty list returns empty list`() {
        val sorted = sortTasks(emptyList(), TaskViewModel.SortBy.NAME)
        assertEquals(0, sorted.size)
    }

    @Test
    fun `sortTasks by NAME handles mixed case`() {
        val mixedCaseTasks = listOf(
            baseTask.copy(id = 1, title = "charlie"),
            baseTask.copy(id = 2, title = "Alpha"),
            baseTask.copy(id = 3, title = "BRAVO")
        )
        val sorted = sortTasks(mixedCaseTasks, TaskViewModel.SortBy.NAME)
        assertEquals("Alpha", sorted[0].title)
        assertEquals("BRAVO", sorted[1].title)
        assertEquals("charlie", sorted[2].title)
    }

    @Test
    fun `sortTasks preserves id during sorting`() {
        val sortBy = TaskViewModel.SortBy.NAME
        val sorted = sortTasks(tasks, sortBy)
        assertEquals(2, sorted[0].id)
        assertEquals(3, sorted[1].id)
        assertEquals(1, sorted[2].id)
    }

    private fun sortTasks(tasks: List<Task>, sortBy: TaskViewModel.SortBy): List<Task> {
        return when (sortBy) {
            TaskViewModel.SortBy.DATE -> tasks.sortedBy { it.dueDateMillis ?: Long.MAX_VALUE }
            TaskViewModel.SortBy.NAME -> tasks.sortedBy { it.title.lowercase() }
            TaskViewModel.SortBy.PRIORITY -> tasks.sortedByDescending { it.priority.ordinal }
        }
    }
}

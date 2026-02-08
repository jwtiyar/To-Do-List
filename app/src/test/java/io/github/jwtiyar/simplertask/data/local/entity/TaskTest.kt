package io.github.jwtiyar.simplertask.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskTest {

    @Test
    fun `two tasks with same core fields are equal`() {
        val task1 = Task(
            id = 1,
            title = "Test Task",
            description = "Description",
            priority = Priority.MEDIUM
        )
        val task2 = Task(
            id = 1,
            title = "Test Task",
            description = "Description",
            priority = Priority.MEDIUM
        )
        assertEquals(task1, task2)
        assertEquals(task1.hashCode(), task2.hashCode())
    }

    @Test
    fun `two tasks with different id are not equal`() {
        val task1 = Task(id = 1, title = "Test Task", description = "Description", priority = Priority.MEDIUM)
        val task2 = Task(id = 2, title = "Test Task", description = "Description", priority = Priority.MEDIUM)
        assertNotEquals(task1, task2)
    }

    @Test
    fun `two tasks with different title are not equal`() {
        val task1 = Task(id = 1, title = "Task A", description = "Description", priority = Priority.MEDIUM)
        val task2 = Task(id = 1, title = "Task B", description = "Description", priority = Priority.MEDIUM)
        assertNotEquals(task1, task2)
    }

    @Test
    fun `two tasks with different priority are not equal`() {
        val task1 = Task(id = 1, title = "Test Task", description = "Description", priority = Priority.LOW)
        val task2 = Task(id = 1, title = "Test Task", description = "Description", priority = Priority.HIGH)
        assertNotEquals(task1, task2)
    }

    @Test
    fun `notificationId is ignored in equals`() {
        val task1 = Task(id = 1, title = "Test Task", description = "Description", priority = Priority.MEDIUM, notificationId = 100)
        val task2 = Task(id = 1, title = "Test Task", description = "Description", priority = Priority.MEDIUM, notificationId = 200)
        assertEquals(task1, task2)
    }

    @Test
    fun `recurrence fields are ignored in equals`() {
        val task1 = Task(id = 1, title = "Test Task", description = "Description", priority = Priority.MEDIUM, recurrenceType = RecurrenceType.DAILY, recurrenceInterval = 2)
        val task2 = Task(id = 1, title = "Test Task", description = "Description", priority = Priority.MEDIUM, recurrenceType = RecurrenceType.WEEKLY, recurrenceInterval = 5)
        assertEquals(task1, task2)
    }

    @Test
    fun `isRecurring returns true when recurrenceType is set`() {
        val task = Task(id = 1, title = "Recurring Task", description = "Description", priority = Priority.MEDIUM, recurrenceType = RecurrenceType.DAILY)
        assertTrue(task.isRecurring())
    }

    @Test
    fun `isRecurring returns false when recurrenceType is null`() {
        val task = Task(id = 1, title = "Non-recurring Task", description = "Description", priority = Priority.MEDIUM)
        assertFalse(task.isRecurring())
    }

    @Test
    fun `getNextDueDate returns null for non-recurring task`() {
        val task = Task(id = 1, title = "Task", description = "Description", priority = Priority.MEDIUM)
        assertNull(task.getNextDueDate())
    }

    @Test
    fun `getNextDueDate calculates daily recurrence correctly`() {
        val baseDate = Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 15, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val task = Task(id = 1, title = "Daily Task", description = "Description", priority = Priority.MEDIUM, recurrenceType = RecurrenceType.DAILY, recurrenceInterval = 1, dueDateMillis = baseDate)
        val nextDue = task.getNextDueDate()
        val expectedDate = Calendar.getInstance().apply { timeInMillis = baseDate; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        assertEquals(expectedDate, nextDue)
    }

    @Test
    fun `getNextDueDate calculates weekly recurrence correctly`() {
        val baseDate = Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 15, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val task = Task(id = 1, title = "Weekly Task", description = "Description", priority = Priority.MEDIUM, recurrenceType = RecurrenceType.WEEKLY, recurrenceInterval = 1, dueDateMillis = baseDate)
        val nextDue = task.getNextDueDate()
        val expectedDate = Calendar.getInstance().apply { timeInMillis = baseDate; add(Calendar.WEEK_OF_YEAR, 1) }.timeInMillis
        assertEquals(expectedDate, nextDue)
    }

    @Test
    fun `getNextDueDate calculates monthly recurrence correctly`() {
        val baseDate = Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 15, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val task = Task(id = 1, title = "Monthly Task", description = "Description", priority = Priority.MEDIUM, recurrenceType = RecurrenceType.MONTHLY, recurrenceInterval = 1, dueDateMillis = baseDate)
        val nextDue = task.getNextDueDate()
        val expectedDate = Calendar.getInstance().apply { timeInMillis = baseDate; add(Calendar.MONTH, 1) }.timeInMillis
        assertEquals(expectedDate, nextDue)
    }

    @Test
    fun `getNextDueDate respects recurrence interval`() {
        val baseDate = Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 15, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val task = Task(id = 1, title = "Task", description = "Description", priority = Priority.MEDIUM, recurrenceType = RecurrenceType.DAILY, recurrenceInterval = 3, dueDateMillis = baseDate)
        val nextDue = task.getNextDueDate()
        val expectedDate = Calendar.getInstance().apply { timeInMillis = baseDate; add(Calendar.DAY_OF_MONTH, 3) }.timeInMillis
        assertEquals(expectedDate, nextDue)
    }

    @Test
    fun `getNextDueDate returns null when exceeding recurrenceEndDate`() {
        val baseDate = Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 15, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val endDate = Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 16, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val task = Task(id = 1, title = "Task", description = "Description", priority = Priority.MEDIUM, recurrenceType = RecurrenceType.DAILY, recurrenceInterval = 7, dueDateMillis = baseDate, recurrenceEndDate = endDate)
        assertNull(task.getNextDueDate())
    }

    @Test
    fun `getNextDueDate returns next date when before recurrenceEndDate`() {
        val baseDate = Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 15, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val endDate = Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 15, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val task = Task(id = 1, title = "Task", description = "Description", priority = Priority.MEDIUM, recurrenceType = RecurrenceType.DAILY, recurrenceInterval = 1, dueDateMillis = baseDate, recurrenceEndDate = endDate)
        val nextDue = task.getNextDueDate()
        val expectedDate = Calendar.getInstance().apply { timeInMillis = baseDate; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        assertEquals(expectedDate, nextDue)
    }
}

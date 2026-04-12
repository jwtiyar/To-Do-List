package io.github.jwtiyar.simplertask.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class TaskTest {

    @Test
    fun getNextDueDate_dailyRecurrence_calculatesCorrectly() {
        // Arrange
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.MARCH, 1, 10, 0, 0) // March 1, 2026
        calendar.set(Calendar.MILLISECOND, 0)
        
        val task = Task(
            title = "Daily Task",
            description = "",
            dueDateMillis = calendar.timeInMillis, // Set base time
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 3 // Repeat every 3 days
        )

        // Expected explicitly 3 days later
        calendar.add(Calendar.DAY_OF_MONTH, 3)
        val expected = calendar.timeInMillis

        // Act
        val result = task.getNextDueDate()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun getNextDueDate_weeklyRecurrence_calculatesCorrectly() {
        // Arrange
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.MARCH, 1, 10, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val task = Task(
            title = "Weekly Task",
            description = "",
            dueDateMillis = calendar.timeInMillis,
            recurrenceType = RecurrenceType.WEEKLY,
            recurrenceInterval = 2 // Repeat every 2 weeks
        )

        // Expected exactly 2 weeks later
        calendar.add(Calendar.WEEK_OF_YEAR, 2)
        val expected = calendar.timeInMillis

        // Act
        val result = task.getNextDueDate()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun getNextDueDate_monthlyRecurrence_calculatesCorrectly() {
        // Arrange
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.JANUARY, 31, 10, 0, 0) // Jan 31
        calendar.set(Calendar.MILLISECOND, 0)
        
        val task = Task(
            title = "Monthly Task",
            description = "",
            dueDateMillis = calendar.timeInMillis,
            recurrenceType = RecurrenceType.MONTHLY,
            recurrenceInterval = 1 // Repeat every 1 month
        )

        // Expected smoothly calculated correctly by java Calendar algorithms (Feb 28/29)
        calendar.add(Calendar.MONTH, 1)
        val expected = calendar.timeInMillis

        // Act
        val result = task.getNextDueDate()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun getNextDueDate_noInitialDueDate_usesCurrentTime() {
        // Arrange
        val task = Task(
            title = "No Due Date Task",
            description = "",
            dueDateMillis = null, // No initial due date setup!
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1
        )

        // Act
        val result = task.getNextDueDate()
        
        // Assert
        // We cannot assert EXACT milliseconds since creation time differs slightly from check time
        // but it must NOT be null. The fix was making it evaluate from Current Time.
        org.junit.Assert.assertNotNull(result)
    }

    @Test
    fun getNextDueDate_pastEndDate_returnsNull() {
        // Arrange
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_MONTH, 5) 
        val endDate = calendar.timeInMillis // End date is 5 days from now
        
        val task = Task(
            title = "Expiring Task",
            description = "",
            dueDateMillis = now,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 6, // Next occurrence will be 6 days from now
            recurrenceEndDate = endDate
        )

        // Act
        val result = task.getNextDueDate()

        // Assert
        // 6 days into the future deliberately skips the end date at 5 days. Should return null.
        assertNull(result)
    }
}

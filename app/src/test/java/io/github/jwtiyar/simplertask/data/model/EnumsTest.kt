package io.github.jwtiyar.simplertask.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskFilterTest {

    @Test
    fun `TaskFilter has all expected values`() {
        val values = TaskFilter.values()
        assertEquals(4, values.size)
    }

    @Test
    fun `TaskFilter contains PENDING`() {
        assertTrue(TaskFilter.values().contains(TaskFilter.PENDING))
    }

    @Test
    fun `TaskFilter contains COMPLETED`() {
        assertTrue(TaskFilter.values().contains(TaskFilter.COMPLETED))
    }

    @Test
    fun `TaskFilter contains SAVED`() {
        assertTrue(TaskFilter.values().contains(TaskFilter.SAVED))
    }

    @Test
    fun `TaskFilter contains ARCHIVED`() {
        assertTrue(TaskFilter.values().contains(TaskFilter.ARCHIVED))
    }

    @Test
    fun `TaskFilter valueOf works correctly`() {
        assertEquals(TaskFilter.PENDING, TaskFilter.valueOf("PENDING"))
        assertEquals(TaskFilter.COMPLETED, TaskFilter.valueOf("COMPLETED"))
        assertEquals(TaskFilter.SAVED, TaskFilter.valueOf("SAVED"))
        assertEquals(TaskFilter.ARCHIVED, TaskFilter.valueOf("ARCHIVED"))
    }

    @Test
    fun `TaskFilter ordinal values are sequential`() {
        assertEquals(0, TaskFilter.PENDING.ordinal)
        assertEquals(1, TaskFilter.COMPLETED.ordinal)
        assertEquals(2, TaskFilter.SAVED.ordinal)
        assertEquals(3, TaskFilter.ARCHIVED.ordinal)
    }
}

class TaskActionTest {

    @Test
    fun `TaskAction has all expected values`() {
        val values = TaskAction.values()
        assertEquals(4, values.size)
    }

    @Test
    fun `TaskAction contains SAVE`() {
        assertTrue(TaskAction.values().contains(TaskAction.SAVE))
    }

    @Test
    fun `TaskAction contains UNSAVE`() {
        assertTrue(TaskAction.values().contains(TaskAction.UNSAVE))
    }

    @Test
    fun `TaskAction contains ARCHIVE`() {
        assertTrue(TaskAction.values().contains(TaskAction.ARCHIVE))
    }

    @Test
    fun `TaskAction contains UNARCHIVE`() {
        assertTrue(TaskAction.values().contains(TaskAction.UNARCHIVE))
    }

    @Test
    fun `TaskAction valueOf works correctly`() {
        assertEquals(TaskAction.SAVE, TaskAction.valueOf("SAVE"))
        assertEquals(TaskAction.UNSAVE, TaskAction.valueOf("UNSAVE"))
        assertEquals(TaskAction.ARCHIVE, TaskAction.valueOf("ARCHIVE"))
        assertEquals(TaskAction.UNARCHIVE, TaskAction.valueOf("UNARCHIVE"))
    }

    @Test
    fun `TaskAction ordinal values are sequential`() {
        assertEquals(0, TaskAction.SAVE.ordinal)
        assertEquals(1, TaskAction.UNSAVE.ordinal)
        assertEquals(2, TaskAction.ARCHIVE.ordinal)
        assertEquals(3, TaskAction.UNARCHIVE.ordinal)
    }
}

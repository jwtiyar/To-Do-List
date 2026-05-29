package io.github.jwtiyar.simplertask.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test



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

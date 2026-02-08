package io.github.jwtiyar.simplertask.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CategoryTest {

    @Test
    fun `two categories with same fields are equal`() {
        val cat1 = Category(id = 1, name = "Work", color = 0xFF1E88E5.toInt())
        val cat2 = Category(id = 1, name = "Work", color = 0xFF1E88E5.toInt())
        assertEquals(cat1, cat2)
    }

    @Test
    fun `two categories with different id are not equal`() {
        val cat1 = Category(id = 1, name = "Work", color = 0xFF1E88E5.toInt())
        val cat2 = Category(id = 2, name = "Work", color = 0xFF1E88E5.toInt())
        assertNotEquals(cat1, cat2)
    }

    @Test
    fun `two categories with different name are not equal`() {
        val cat1 = Category(id = 1, name = "Work", color = 0xFF1E88E5.toInt())
        val cat2 = Category(id = 1, name = "Personal", color = 0xFF1E88E5.toInt())
        assertNotEquals(cat1, cat2)
    }

    @Test
    fun `default categories has correct count`() {
        assertEquals(6, Category.DEFAULT_CATEGORIES.size)
    }

    @Test
    fun `default categories contains expected names`() {
        val names = Category.DEFAULT_CATEGORIES.map { it.name }
        assert(names.contains("Work"))
        assert(names.contains("Personal"))
        assert(names.contains("Health"))
        assert(names.contains("Learning"))
        assert(names.contains("Shopping"))
        assert(names.contains("Home"))
    }

    @Test
    fun `default categories have valid colors`() {
        Category.DEFAULT_CATEGORIES.forEach { cat ->
            assert(cat.color != 0) { "Category ${cat.name} has invalid color" }
        }
    }

    @Test
    fun `default categories have unique names`() {
        val names = Category.DEFAULT_CATEGORIES.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `default categories have unique ids`() {
        val ids = Category.DEFAULT_CATEGORIES.map { it.id }
        assertEquals(1, ids.distinct().size)
        assertEquals(0, ids.distinct().single())
    }

    @Test
    fun `work category has blue color`() {
        val work = Category.DEFAULT_CATEGORIES.find { it.name == "Work" }
        assertEquals(0xFF1E88E5.toInt(), work?.color)
    }

    @Test
    fun `personal category has green color`() {
        val personal = Category.DEFAULT_CATEGORIES.find { it.name == "Personal" }
        assertEquals(0xFF43A047.toInt(), personal?.color)
    }

    @Test
    fun `health category has red color`() {
        val health = Category.DEFAULT_CATEGORIES.find { it.name == "Health" }
        assertEquals(0xFFE53935.toInt(), health?.color)
    }
}

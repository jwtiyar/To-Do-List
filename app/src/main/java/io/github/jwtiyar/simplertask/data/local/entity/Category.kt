package io.github.jwtiyar.simplertask.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val color: Int, // Color as ARGB integer
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        // Predefined categories with colors
        val DEFAULT_CATEGORIES = listOf(
            Category(name = "Work", color = 0xFF1E88E5.toInt()), // Blue
            Category(name = "Personal", color = 0xFF43A047.toInt()), // Green
            Category(name = "Health", color = 0xFFE53935.toInt()), // Red
            Category(name = "Learning", color = 0xFF8E24AA.toInt()), // Purple
            Category(name = "Shopping", color = 0xFFFF9800.toInt()), // Orange
            Category(name = "Home", color = 0xFF0097A7.toInt()) // Teal
        )
    }
}
package io.github.jwtiyar.simplertask.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jwtiyar.simplertask.data.local.MIGRATION_5_6
import io.github.jwtiyar.simplertask.data.local.MIGRATION_1_2
import io.github.jwtiyar.simplertask.data.local.MIGRATION_2_3
import io.github.jwtiyar.simplertask.data.local.MIGRATION_3_4
import io.github.jwtiyar.simplertask.data.local.MIGRATION_4_5
import io.github.jwtiyar.simplertask.data.local.TaskDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskMigrationTest {
    @Test
    fun versionOneTaskSurvivesAllUpgrades() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-one-to-six-check"
        context.deleteDatabase(name)
        val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        try {
            old.execSQL("""CREATE TABLE task (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL, description TEXT NOT NULL,
                isCompleted INTEGER NOT NULL, dueDateMillis INTEGER
            )""".trimIndent())
            old.execSQL("INSERT INTO task (id, title, description, isCompleted) VALUES (9, 'Original task', 'Keep notes', 0)")
            old.version = 1
        } finally {
            old.close()
        }
        val upgraded = Room.databaseBuilder(context, TaskDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
        try {
            assertEquals("Original task", upgraded.taskDao().getTaskById(9)?.title)
            assertEquals("Keep notes", upgraded.taskDao().getTaskById(9)?.description)
            assertEquals(6, upgraded.categoryDao().getCategoryCount())
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun versionFiveTaskSurvivesUpgradeToSix() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-five-to-six-check"
        context.deleteDatabase(name)
        val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        try {
            old.execSQL("""CREATE TABLE task (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL, description TEXT NOT NULL,
                isCompleted INTEGER NOT NULL, dueDateMillis INTEGER,
                priority TEXT NOT NULL DEFAULT 'MEDIUM', notificationId INTEGER,
                isSaved INTEGER NOT NULL DEFAULT 0, isArchived INTEGER NOT NULL DEFAULT 0,
                recurrenceType TEXT, recurrenceInterval INTEGER NOT NULL DEFAULT 1,
                recurrenceEndDate INTEGER, parentTaskId INTEGER
            )""".trimIndent())
            old.execSQL("INSERT INTO task (id, title, description, isCompleted) VALUES (7, 'Keep this task', '', 0)")
            old.version = 5
        } finally {
            old.close()
        }
        val upgraded = Room.databaseBuilder(context, TaskDatabase::class.java, name)
            .addMigrations(MIGRATION_5_6).build()
        try {
            assertEquals("Keep this task", upgraded.taskDao().getTaskById(7)?.title)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}


package com.example.simplertask.backup

import androidx.room.Room
import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.example.simplertask.Task
import com.example.simplertask.TaskDao
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Minimal Room database for testing. We inline here to avoid touching production DB class if absent.
@androidx.room.Database(entities = [Task::class], version = 1, exportSchema = false)
abstract class TestDb : androidx.room.RoomDatabase() {
    abstract fun taskDao(): TaskDao
}

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    @Test
    fun exportImport_roundTrip() = runBlocking {
        // Use a plain JVM context for Room in-memory DB
        val context: Context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, TestDb::class.java)
            .allowMainThreadQueries().build()
        val dao = db.taskDao()

        // Seed
        dao.insertTask(Task(title = "TitleA", description = "DescA", isCompleted = true))
        dao.insertTask(Task(title = "TitleB", description = "DescB", isCompleted = false))

        val exporter = BackupExporter(context, dao)
        val json = exporter.exportAllTasks()

        // Wipe and re-import
        dao.deleteAllTasks()
        val importer = BackupImporter(dao)
        val result = importer.import(json)
        require(result is ImportResult.Success)

        val tasks = dao.getAllTasks().first()
    assertEquals(2, tasks.count())
    val importedTitles = tasks.map { t -> t.title }.toSet()
    assertEquals(setOf("TitleA", "TitleB"), importedTitles)
    }
}

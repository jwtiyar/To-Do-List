package io.github.jwtiyar.simplertask.data.backup

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.Category
import io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupManagerTest {

    @Test
    fun `encrypted backup preserves category and recurrence relationships`() = runTest {
        val parent = sampleTasks.first().copy(
            recurrenceType = RecurrenceType.MONTHLY,
            recurrenceInterval = 2,
            recurrenceEndDate = 1_234_567L,
            categoryId = 17
        )
        val child = sampleTasks.last().copy(parentTaskId = parent.id, categoryId = 17, isArchived = true)
        val category = Category(id = 17, name = "Bills", color = 0xff8822aa.toInt())

        val exported = backupManager.exportBackup(
            BackupManager.BackupData(listOf(parent, child), listOf(category)), "password".toCharArray()
        )
        val restored = backupManager.importBackup(exported, "password".toCharArray())

        assertEquals(listOf(parent, child), restored.tasks)
        assertEquals(listOf(category), restored.categories)
        assertEquals(true, restored.includesCategories)
    }

    @Test
    fun `URI import rejects content exceeding the backup size limit`() = runTest {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(10 * 1024 * 1024 + 1))

        val error = runCatching { BackupManager(context).readFromUri(uri) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Backup file is too large", error?.message)
    }

    private val backupManager = BackupManager(mockk<Context>(relaxed = true))

    private val sampleTasks = listOf(
        Task(
            id = 1,
            title = "Task 1",
            description = "Desc 1",
            isCompleted = false,
            isSaved = true,
            isArchived = false,
            dueDateMillis = 1234L,
            notificationId = 11,
            priority = Priority.HIGH
        ),
        Task(
            id = 2,
            title = "Task 2",
            description = "",
            isCompleted = true,
            isSaved = false,
            isArchived = false,
            dueDateMillis = null,
            notificationId = null,
            priority = Priority.LOW
        )
    )

    @Test
    fun `export and import encrypted backup roundtrip with correct password`() = runTest {
        val password = "correct-password".toCharArray()
        val exported = backupManager.exportTasks(sampleTasks, password)

        val imported = backupManager.importTasks(exported, "correct-password".toCharArray())

        assertEquals(sampleTasks.size, imported.size)
        assertEquals(sampleTasks[0].title, imported[0].title)
        assertEquals(sampleTasks[0].priority, imported[0].priority)
        assertEquals(sampleTasks[1].isCompleted, imported[1].isCompleted)
        assertEquals(sampleTasks[1].priority, imported[1].priority)
    }

    @Test
    fun `import encrypted backup fails with wrong password`() = runTest {
        val exported = backupManager.exportTasks(sampleTasks, "correct-password".toCharArray())

        val error = kotlin.runCatching {
            backupManager.importTasks(exported, "wrong-password".toCharArray())
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("Incorrect backup password", ignoreCase = true) == true)
    }

    @Test
    fun `import malformed envelope fails clearly`() = runTest {
        val malformed = JSONObject()
            .put("version", 2)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", 150000)
            .put("salt", "invalid")
            .toString()

        val error = kotlin.runCatching {
            backupManager.importTasks(malformed, "password".toCharArray())
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("Malformed encrypted backup envelope", ignoreCase = true) == true)
    }

    @Test
    fun `import legacy v1 plaintext backup remains compatible`() = runTest {
        val legacy = JSONObject()
            .put("version", 1)
            .put("created_at", System.currentTimeMillis())
            .put(
                "tasks", org.json.JSONArray().put(
                    JSONObject()
                        .put("id", 3)
                        .put("title", "Legacy Task")
                        .put("description", "legacy")
                        .put("is_completed", false)
                        .put("is_saved", false)
                        .put("is_archived", false)
                        .put("due_date_millis", JSONObject.NULL)
                        .put("notification_id", JSONObject.NULL)
                        .put("priority", "MEDIUM")
                )
            )
            .toString()

        val imported = backupManager.importTasks(legacy, null)

        assertEquals(1, imported.size)
        assertEquals("Legacy Task", imported.first().title)
        assertEquals(Priority.MEDIUM, imported.first().priority)
    }
}

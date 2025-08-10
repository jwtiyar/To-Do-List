package com.example.simplertask.backup

import com.example.simplertask.Priority
import com.example.simplertask.Task
import com.example.simplertask.TaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

sealed class ImportResult {
    data class Success(val inserted: Int, val skipped: Int) : ImportResult()
    data class UnsupportedVersion(val schemaVersion: Int) : ImportResult()
    data class Malformed(val reason: String) : ImportResult()
}

class BackupImporter(
    private val taskDao: TaskDao,
    private val json: Json = BackupJson.config
) {
    suspend fun import(jsonContent: String): ImportResult = withContext(Dispatchers.IO) {
        val bundle = try {
            json.decodeFromString<BackupBundle>(jsonContent)
        } catch (e: Exception) {
            return@withContext ImportResult.Malformed(e.message ?: "Parse error")
        }

        if (bundle.schemaVersion != BACKUP_SCHEMA_VERSION) {
            return@withContext ImportResult.UnsupportedVersion(bundle.schemaVersion)
        }

        var inserted = 0
        var skipped = 0
        for (task in bundle.tasks) {
            if (shouldSkip(task)) {
                skipped++
                continue
            }
            val entity = task.toEntity()
            taskDao.insertTask(entity)
            inserted++
        }
        ImportResult.Success(inserted, skipped)
    }

    private suspend fun shouldSkip(task: BackupTask): Boolean {
        val dueDate = task.dueDateMillis ?: return false // only hash-based for tasks with due date
        val existing = taskDao.getTaskByTitleAndDueDate(task.title, dueDate)
        if (existing == null) return false
        // Recompute hash for existing entity for stronger match
        val existingHash = HashUtil.sha256(listOf(
            existing.title,
            existing.description,
            existing.isCompleted.toString(),
            existing.isSaved.toString(),
            existing.isArchived.toString(),
            (existing.dueDateMillis?.toString() ?: ""),
            existing.priority.name
        ).joinToString("|"))
        return existingHash == task.contentHash
    }
}

private fun BackupTask.toEntity(): Task = Task(
    title = title,
    description = description,
    isCompleted = isCompleted,
    isSaved = isSaved,
    isArchived = isArchived,
    dueDateMillis = dueDateMillis,
    priority = runCatching { Priority.valueOf(priority) }.getOrElse { Priority.MEDIUM }
)

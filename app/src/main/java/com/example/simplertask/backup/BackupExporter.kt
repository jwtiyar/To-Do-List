package com.example.simplertask.backup

import android.content.Context
import com.example.simplertask.Task
import com.example.simplertask.TaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupExporter(
    private val context: Context,
    private val taskDao: TaskDao,
    private val json: Json = BackupJson.config
) {
    suspend fun exportAllTasks(): String = withContext(Dispatchers.IO) {
        val tasks = taskDao.getAllTasks().first()
        val backupTasks = tasks.map { it.toBackupTask() }
        val bundle = BackupBundle(
            tasks = backupTasks,
            exportedAtUtc = System.currentTimeMillis(),
            appVersionName = null, // Could be populated via PackageManager
            appVersionCode = null
        )
        json.encodeToString(bundle)
    }
}

internal object BackupJson {
    val config: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}

private fun Task.toBackupTask(): BackupTask {
    val core = listOf(
        title,
        description,
        isCompleted.toString(),
        isSaved.toString(),
        isArchived.toString(),
        (dueDateMillis?.toString() ?: ""),
        priority.name
    ).joinToString("|")
    return BackupTask(
        title = title,
        description = description,
        isCompleted = isCompleted,
        isSaved = isSaved,
        isArchived = isArchived,
        dueDateMillis = dueDateMillis,
        priority = priority.name,
        contentHash = HashUtil.sha256(core)
    )
}

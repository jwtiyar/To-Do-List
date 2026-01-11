package io.github.jwtiyar.simplertask.data.backup

import android.content.Context
import android.net.Uri
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages backup and restore operations for tasks
 */
class BackupManager(private val context: Context) {
    
    companion object {
        private const val BACKUP_VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_TASKS = "tasks"
        
        // Task JSON keys
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_IS_COMPLETED = "is_completed"
        private const val KEY_IS_SAVED = "is_saved"
        private const val KEY_IS_ARCHIVED = "is_archived"
        private const val KEY_DUE_DATE_MILLIS = "due_date_millis"
        private const val KEY_NOTIFICATION_ID = "notification_id"
        private const val KEY_PRIORITY = "priority"
    }
    
    /**
     * Export tasks to JSON format
     */
    suspend fun exportTasks(tasks: List<Task>): String = withContext(Dispatchers.IO) {
        val backupJson = JSONObject()
        backupJson.put(KEY_VERSION, BACKUP_VERSION)
        backupJson.put(KEY_CREATED_AT, System.currentTimeMillis())
        
        val tasksArray = JSONArray()
        tasks.forEach { task ->
            val taskJson = JSONObject().apply {
                put(KEY_ID, task.id)
                put(KEY_TITLE, task.title)
                put(KEY_DESCRIPTION, task.description)
                put(KEY_IS_COMPLETED, task.isCompleted)
                put(KEY_IS_SAVED, task.isSaved)
                put(KEY_IS_ARCHIVED, task.isArchived)
                put(KEY_DUE_DATE_MILLIS, task.dueDateMillis)
                put(KEY_NOTIFICATION_ID, task.notificationId)
                put(KEY_PRIORITY, task.priority.name)
            }
            tasksArray.put(taskJson)
        }
        
        backupJson.put(KEY_TASKS, tasksArray)
        return@withContext backupJson.toString(2) // Pretty print with 2-space indent
    }
    
    /**
     * Import tasks from JSON format
     */
    suspend fun importTasks(jsonString: String): List<Task> = withContext(Dispatchers.IO) {
        val tasks = mutableListOf<Task>()
        
        try {
            val backupJson = JSONObject(jsonString)
            val version = backupJson.optInt(KEY_VERSION, 1)
            
            // Handle different backup versions if needed in the future
            when (version) {
                1 -> {
                    val tasksArray = backupJson.getJSONArray(KEY_TASKS)
                    
                    for (i in 0 until tasksArray.length()) {
                        val taskJson = tasksArray.getJSONObject(i)
                        
                        val task = Task(
                            id = taskJson.optInt(KEY_ID, 0),
                            title = taskJson.getString(KEY_TITLE),
                            description = taskJson.optString(KEY_DESCRIPTION, ""),
                            isCompleted = taskJson.optBoolean(KEY_IS_COMPLETED, false),
                            isSaved = taskJson.optBoolean(KEY_IS_SAVED, false),
                            isArchived = taskJson.optBoolean(KEY_IS_ARCHIVED, false),
                            dueDateMillis = if (taskJson.has(KEY_DUE_DATE_MILLIS) && !taskJson.isNull(KEY_DUE_DATE_MILLIS)) {
                                taskJson.getLong(KEY_DUE_DATE_MILLIS)
                            } else null,
                            notificationId = if (taskJson.has(KEY_NOTIFICATION_ID) && !taskJson.isNull(KEY_NOTIFICATION_ID)) {
                                taskJson.getInt(KEY_NOTIFICATION_ID)
                            } else null,
                            priority = try {
                                Priority.valueOf(taskJson.optString(KEY_PRIORITY, Priority.MEDIUM.name))
                            } catch (e: IllegalArgumentException) {
                                Priority.MEDIUM
                            }
                        )
                        
                        tasks.add(task)
                    }
                }
                else -> throw IllegalArgumentException("Unsupported backup version: $version")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid backup file format: ${e.message}", e)
        }
        
        return@withContext tasks
    }
    
    /**
     * Read content from URI (for file picker results)
     */
    suspend fun readFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                return@withContext reader.readText()
            }
        } ?: throw IllegalArgumentException("Could not read from selected file")
    }
    
    /**
     * Write content to URI (for file picker results)
     */
    suspend fun writeToUri(uri: Uri, content: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(content.toByteArray())
            outputStream.flush()
        } ?: throw IllegalArgumentException("Could not write to selected file")
    }
    
    /**
     * Generate backup filename with timestamp
     */
    fun generateBackupFilename(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "simplertask_backup_$timestamp.json"
    }
    
    /**
     * Get backup metadata from JSON string
     */
    fun getBackupMetadata(jsonString: String): BackupMetadata? {
        return try {
            val backupJson = JSONObject(jsonString)
            val version = backupJson.optInt(KEY_VERSION, 1)
            val createdAt = backupJson.optLong(KEY_CREATED_AT, 0)
            val tasksArray = backupJson.getJSONArray(KEY_TASKS)
            val taskCount = tasksArray.length()
            
            BackupMetadata(
                version = version,
                createdAt = createdAt,
                taskCount = taskCount
            )
        } catch (e: Exception) {
            null
        }
    }
    
    data class BackupMetadata(
        val version: Int,
        val createdAt: Long,
        val taskCount: Int
    )
}

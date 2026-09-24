package io.github.jwtiyar.simplertask.data.backup

import android.content.Context
import android.net.Uri
import java.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages backup and restore operations for tasks
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val BACKUP_VERSION = 3
        private const val ENCRYPTED_BACKUP_VERSION = 2
        private const val LEGACY_BACKUP_VERSION = 1

        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val KDF_ITERATIONS = 150_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_SIZE_BYTES = 16
        private const val IV_SIZE_BYTES = 12
        private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
        private const val GCM_TAG_LENGTH_BITS = 128

        private const val KEY_VERSION = "version"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_TASKS = "tasks"
        private const val KEY_CATEGORIES = "categories"

        private const val KEY_KDF = "kdf"
        private const val KEY_ITERATIONS = "iterations"
        private const val KEY_SALT = "salt"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"

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
        private const val KEY_RECURRENCE_TYPE = "recurrence_type"
        private const val KEY_RECURRENCE_INTERVAL = "recurrence_interval"
        private const val KEY_RECURRENCE_END_DATE = "recurrence_end_date"
        private const val KEY_PARENT_TASK_ID = "parent_task_id"
        private const val KEY_CATEGORY_ID = "category_id"
    }

    /**
     * Export tasks to encrypted JSON format.
     */
    suspend fun exportTasks(tasks: List<Task>, password: CharArray): String =
        exportBackup(BackupData(tasks, emptyList()), password)

    data class BackupData(
        val tasks: List<Task>,
        val categories: List<io.github.jwtiyar.simplertask.data.local.entity.Category>,
        val includesCategories: Boolean = false
    )

    suspend fun exportBackup(data: BackupData, password: CharArray): String = withContext(Dispatchers.IO) {
        require(password.isNotEmpty()) { "Backup password cannot be empty" }

        val plaintextJson = serializeTasksJson(data.tasks, data.categories).toString()
        val secureRandom = SecureRandom()
        val salt = ByteArray(SALT_SIZE_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_SIZE_BYTES).also(secureRandom::nextBytes)
        val key = deriveKey(password, salt)

        val ciphertext = encrypt(
            plaintext = plaintextJson.toByteArray(StandardCharsets.UTF_8),
            key = key,
            iv = iv
        )

        val envelope = JSONObject().apply {
            put(KEY_VERSION, BACKUP_VERSION)
            put(KEY_KDF, KDF_ALGORITHM)
            put(KEY_ITERATIONS, KDF_ITERATIONS)
            put(KEY_SALT, Base64.getEncoder().encodeToString(salt))
            put(KEY_IV, Base64.getEncoder().encodeToString(iv))
            put(KEY_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext))
            put(KEY_CREATED_AT, System.currentTimeMillis())
        }

        return@withContext envelope.toString(2)
    }

    /**
     * Import tasks from backup JSON format (encrypted v2 or legacy v1 plaintext).
     */
    suspend fun importTasks(content: String, password: CharArray?): List<Task> =
        importBackup(content, password).tasks

    suspend fun importBackup(content: String, password: CharArray?): BackupData = withContext(Dispatchers.IO) {
        require(content.length <= MAX_BACKUP_BYTES) { "Backup file is too large" }
        try {
            val backupJson = JSONObject(content)
            when (val version = backupJson.optInt(KEY_VERSION, LEGACY_BACKUP_VERSION)) {
                BACKUP_VERSION, ENCRYPTED_BACKUP_VERSION -> importEncryptedBackup(backupJson, password)
                LEGACY_BACKUP_VERSION -> parseBackupJson(backupJson)
                else -> throw IllegalArgumentException("Unsupported backup version: $version")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid backup file format: ${e.message}", e)
        }
    }

    private fun importEncryptedBackup(envelope: JSONObject, password: CharArray?): BackupData {
        val providedPassword = password ?: throw IllegalArgumentException("Password is required for encrypted backup")
        if (providedPassword.isEmpty()) {
            throw IllegalArgumentException("Password is required for encrypted backup")
        }

        val kdf = envelope.optString(KEY_KDF)
        if (kdf != KDF_ALGORITHM) {
            throw IllegalArgumentException("Unsupported key derivation function: $kdf")
        }

        try {
            val iterations = envelope.optInt(KEY_ITERATIONS, KDF_ITERATIONS)
            require(iterations == KDF_ITERATIONS) { "Unsupported backup key settings" }
            val salt = Base64.getDecoder().decode(envelope.getString(KEY_SALT))
            val iv = Base64.getDecoder().decode(envelope.getString(KEY_IV))
            val ciphertext = Base64.getDecoder().decode(envelope.getString(KEY_CIPHERTEXT))
            require(salt.size == SALT_SIZE_BYTES && iv.size == IV_SIZE_BYTES && ciphertext.size <= MAX_BACKUP_BYTES) {
                "Invalid backup encryption settings"
            }

            val key = deriveKey(providedPassword, salt, iterations)
            val plaintext = decrypt(ciphertext, key, iv)
            val plaintextJson = JSONObject(String(plaintext, StandardCharsets.UTF_8))
            return parseBackupJson(plaintextJson)
        } catch (e: AEADBadTagException) {
            throw IllegalArgumentException("Incorrect backup password or corrupted backup file", e)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Malformed encrypted backup envelope", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Malformed encrypted backup envelope", e)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decrypt backup: ${e.message}", e)
        }
    }

    internal fun serializeTasksJson(tasks: List<Task>): JSONObject = serializeTasksJson(tasks, emptyList())

    internal fun serializeTasksJson(
        tasks: List<Task>,
        categories: List<io.github.jwtiyar.simplertask.data.local.entity.Category>
    ): JSONObject {
        val backupJson = JSONObject()
        backupJson.put(KEY_VERSION, LEGACY_BACKUP_VERSION)
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
                put(KEY_RECURRENCE_TYPE, task.recurrenceType?.name)
                put(KEY_RECURRENCE_INTERVAL, task.recurrenceInterval)
                put(KEY_RECURRENCE_END_DATE, task.recurrenceEndDate)
                put(KEY_PARENT_TASK_ID, task.parentTaskId)
                put(KEY_CATEGORY_ID, task.categoryId)
            }
            tasksArray.put(taskJson)
        }

        backupJson.put(KEY_TASKS, tasksArray)
        backupJson.put(KEY_CATEGORIES, JSONArray().apply {
            categories.forEach { category ->
                put(JSONObject().apply {
                    put(KEY_ID, category.id)
                    put("name", category.name)
                    put("color", category.color)
                    put("created_at", category.createdAt)
                })
            }
        })
        return backupJson
    }

    internal fun parseTasksJson(backupJson: JSONObject): List<Task> = parseBackupJson(backupJson).tasks

    internal fun parseBackupJson(backupJson: JSONObject): BackupData {
        val tasks = mutableListOf<Task>()
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
                } else {
                    null
                },
                notificationId = if (taskJson.has(KEY_NOTIFICATION_ID) && !taskJson.isNull(KEY_NOTIFICATION_ID)) {
                    taskJson.getInt(KEY_NOTIFICATION_ID)
                } else {
                    null
                },
                priority = try {
                    Priority.valueOf(taskJson.optString(KEY_PRIORITY, Priority.MEDIUM.name))
                } catch (_: IllegalArgumentException) {
                    Priority.MEDIUM
                },
                recurrenceType = if (taskJson.isNull(KEY_RECURRENCE_TYPE)) null else
                    io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType.valueOf(taskJson.getString(KEY_RECURRENCE_TYPE)),
                recurrenceInterval = taskJson.optInt(KEY_RECURRENCE_INTERVAL, 1),
                recurrenceEndDate = if (taskJson.isNull(KEY_RECURRENCE_END_DATE)) null else taskJson.getLong(KEY_RECURRENCE_END_DATE),
                parentTaskId = if (taskJson.isNull(KEY_PARENT_TASK_ID)) null else taskJson.getInt(KEY_PARENT_TASK_ID),
                categoryId = if (taskJson.isNull(KEY_CATEGORY_ID)) null else taskJson.getInt(KEY_CATEGORY_ID)
            )

            tasks.add(task)
        }

        val categories = mutableListOf<io.github.jwtiyar.simplertask.data.local.entity.Category>()
        val array = backupJson.optJSONArray(KEY_CATEGORIES)
        if (array != null) for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            categories.add(io.github.jwtiyar.simplertask.data.local.entity.Category(
                id = item.getInt(KEY_ID), name = item.getString("name"),
                color = item.getInt("color"), createdAt = item.getLong("created_at")
            ))
        }
        return BackupData(tasks, categories, backupJson.has(KEY_CATEGORIES))
    }

    internal fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = KDF_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
            Arrays.fill(password, '\u0000')
        }
    }

    internal fun encrypt(plaintext: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(plaintext)
    }

    internal fun decrypt(ciphertext: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Read content from URI (for file picker results)
     */
    suspend fun readFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = inputStream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BACKUP_BYTES) { "Backup file is too large" }
                output.write(buffer, 0, count)
            }
            return@withContext output.toString(StandardCharsets.UTF_8.name())
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
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        return "simplertask_backup_$timestamp.json"
    }

    /**
     * Get backup metadata from JSON string
     */
    fun getBackupMetadata(jsonString: String): BackupMetadata? {
        return try {
            val backupJson = JSONObject(jsonString)
            val version = backupJson.optInt(KEY_VERSION, LEGACY_BACKUP_VERSION)
            val createdAt = backupJson.optLong(KEY_CREATED_AT, 0)

            when (version) {
                BACKUP_VERSION, ENCRYPTED_BACKUP_VERSION -> BackupMetadata(
                    version = version,
                    createdAt = createdAt,
                    taskCount = null,
                    isEncrypted = true
                )

                LEGACY_BACKUP_VERSION -> {
                    val tasksArray = backupJson.getJSONArray(KEY_TASKS)
                    BackupMetadata(
                        version = version,
                        createdAt = createdAt,
                        taskCount = tasksArray.length(),
                        isEncrypted = false
                    )
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    data class BackupMetadata(
        val version: Int,
        val createdAt: Long,
        val taskCount: Int?,
        val isEncrypted: Boolean
    )
}

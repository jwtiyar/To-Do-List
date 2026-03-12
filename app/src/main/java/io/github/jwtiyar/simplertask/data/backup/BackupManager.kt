package io.github.jwtiyar.simplertask.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
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
        private const val BACKUP_VERSION = 2
        private const val LEGACY_BACKUP_VERSION = 1

        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val KDF_ITERATIONS = 150_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_SIZE_BYTES = 16
        private const val IV_SIZE_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        private const val KEY_VERSION = "version"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_TASKS = "tasks"

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
    }

    /**
     * Export tasks to encrypted JSON format.
     */
    suspend fun exportTasks(tasks: List<Task>, password: CharArray): String = withContext(Dispatchers.IO) {
        require(password.isNotEmpty()) { "Backup password cannot be empty" }

        val plaintextJson = serializeTasksJson(tasks).toString()
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
            put(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            put(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            put(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            put(KEY_CREATED_AT, System.currentTimeMillis())
        }

        return@withContext envelope.toString(2)
    }

    /**
     * Import tasks from backup JSON format (encrypted v2 or legacy v1 plaintext).
     */
    suspend fun importTasks(content: String, password: CharArray?): List<Task> = withContext(Dispatchers.IO) {
        try {
            val backupJson = JSONObject(content)
            when (val version = backupJson.optInt(KEY_VERSION, LEGACY_BACKUP_VERSION)) {
                BACKUP_VERSION -> importEncryptedBackup(backupJson, password)
                LEGACY_BACKUP_VERSION -> parseTasksJson(backupJson)
                else -> throw IllegalArgumentException("Unsupported backup version: $version")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid backup file format: ${e.message}", e)
        }
    }

    private fun importEncryptedBackup(envelope: JSONObject, password: CharArray?): List<Task> {
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
            val salt = Base64.decode(envelope.getString(KEY_SALT), Base64.DEFAULT)
            val iv = Base64.decode(envelope.getString(KEY_IV), Base64.DEFAULT)
            val ciphertext = Base64.decode(envelope.getString(KEY_CIPHERTEXT), Base64.DEFAULT)

            val key = deriveKey(providedPassword, salt, iterations)
            val plaintext = decrypt(ciphertext, key, iv)
            val plaintextJson = JSONObject(String(plaintext, StandardCharsets.UTF_8))
            return parseTasksJson(plaintextJson)
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

    internal fun serializeTasksJson(tasks: List<Task>): JSONObject {
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
            }
            tasksArray.put(taskJson)
        }

        backupJson.put(KEY_TASKS, tasksArray)
        return backupJson
    }

    internal fun parseTasksJson(backupJson: JSONObject): List<Task> {
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
                }
            )

            tasks.add(task)
        }

        return tasks
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
                BACKUP_VERSION -> BackupMetadata(
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

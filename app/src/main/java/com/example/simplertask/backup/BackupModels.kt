package com.example.simplertask.backup

import kotlinx.serialization.Serializable

/** Schema version for backup JSON. Increment if structure changes. */
internal const val BACKUP_SCHEMA_VERSION: Int = 1

/** Top-level backup bundle (tasks + metadata). */
@Serializable
data class BackupBundle(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val tasks: List<BackupTask>,
    val exportedAtUtc: Long,
    val appVersionName: String? = null,
    val appVersionCode: Int? = null
)

/** Task representation in backup (no internal DB id / notificationId). */
@Serializable
data class BackupTask(
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val isSaved: Boolean,
    val isArchived: Boolean,
    val dueDateMillis: Long? = null,
    val priority: String,
    // Deterministic content hash for fast duplicate detection
    val contentHash: String
)

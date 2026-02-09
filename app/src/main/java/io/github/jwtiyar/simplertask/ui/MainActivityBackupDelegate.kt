package io.github.jwtiyar.simplertask.ui

import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.scopes.ActivityScoped
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.data.backup.BackupManager
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

/**
 * Handles the UI logic for backup and restore operations in MainActivity.
 */
@ActivityScoped
class MainActivityBackupDelegate @Inject constructor(
    private val backupManager: BackupManager
) {
    private var activity: AppCompatActivity? = null
    private lateinit var taskViewModel: TaskViewModel

    fun attach(activity: AppCompatActivity) {
        this.activity = activity
        this.taskViewModel = ViewModelProvider(activity)[TaskViewModel::class.java]
    }

    fun generateBackupFilename(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        return "simplertask_backup_$timestamp.json"
    }

    fun exportBackupToUri(uri: Uri) {
        val currentActivity = activity ?: return
        currentActivity.lifecycleScope.launch {
            try {
                val tasks = taskViewModel.getAllTasksForBackup()
                val backupJson = backupManager.exportTasks(tasks)
                backupManager.writeToUri(uri, backupJson)
                
                taskViewModel.postToast(currentActivity.getString(R.string.backup_exported, tasks.size))
            } catch (e: Exception) {
                taskViewModel.postSnackbar(currentActivity.getString(R.string.error_export_backup, e.message))
            }
        }
    }

    fun importBackupFromUri(uri: Uri) {
        val currentActivity = activity ?: return
        currentActivity.lifecycleScope.launch {
            try {
                val backupContent = backupManager.readFromUri(uri)
                val metadata = backupManager.getBackupMetadata(backupContent)
                
                if (metadata == null) {
                    taskViewModel.postSnackbar(currentActivity.getString(R.string.error_invalid_backup))
                    return@launch
                }
                
                showImportConfirmationDialog(backupContent, metadata)
                
            } catch (e: Exception) {
                taskViewModel.postSnackbar(currentActivity.getString(R.string.error_read_backup, e.message))
            }
        }
    }

    private fun showImportConfirmationDialog(backupContent: String, metadata: BackupManager.BackupMetadata) {
        val currentActivity = activity ?: return
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val createdAtString = if (metadata.createdAt > 0) {
            dateFormat.format(Date(metadata.createdAt))
        } else {
            currentActivity.getString(R.string.unknown)
        }
        
        val message = currentActivity.getString(
            R.string.backup_info_format,
            createdAtString,
            metadata.taskCount,
            metadata.version
        )
        
        val items = arrayOf(
            currentActivity.getString(R.string.import_mode_add),
            currentActivity.getString(R.string.import_mode_replace)
        )
        var selectedOption = 0
        
        AlertDialog.Builder(currentActivity)
            .setTitle(currentActivity.getString(R.string.dialog_import_title))
            .setMessage(message)
            .setSingleChoiceItems(items, 0) { _, which ->
                selectedOption = which
            }
            .setPositiveButton(currentActivity.getString(R.string.button_import)) { _, _ ->
                performImport(backupContent, replaceExisting = selectedOption == 1)
            }
            .setNegativeButton(currentActivity.getString(R.string.cancel), null)
            .show()
    }

    private fun performImport(backupContent: String, replaceExisting: Boolean) {
        activity?.lifecycleScope?.launch {
            try {
                val tasks = backupManager.importTasks(backupContent)
                taskViewModel.importTasksFromBackup(tasks, replaceExisting)
            } catch (e: Exception) {
                val currentActivity = activity
                if (currentActivity != null) {
                    taskViewModel.postSnackbar(currentActivity.getString(R.string.error_import_tasks, e.message))
                }
            }
        }
    }
}
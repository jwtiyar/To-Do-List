package io.github.jwtiyar.simplertask.ui

import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.data.backup.BackupManager
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles the UI logic for backup and restore operations in MainActivity.
 */
class MainActivityBackupDelegate(
    private val activity: AppCompatActivity,
    private val taskViewModel: TaskViewModel,
    private val backupManager: BackupManager
) {

    fun exportBackupToUri(uri: Uri) {
        activity.lifecycleScope.launch {
            try {
                val tasks = taskViewModel.getAllTasksForBackup()
                val backupJson = backupManager.exportTasks(tasks)
                backupManager.writeToUri(uri, backupJson)
                
                taskViewModel.postToast(activity.getString(R.string.backup_exported, tasks.size))
            } catch (e: Exception) {
                taskViewModel.postSnackbar(activity.getString(R.string.error_export_backup, e.message))
            }
        }
    }

    fun importBackupFromUri(uri: Uri) {
        activity.lifecycleScope.launch {
            try {
                val backupContent = backupManager.readFromUri(uri)
                val metadata = backupManager.getBackupMetadata(backupContent)
                
                if (metadata == null) {
                    taskViewModel.postSnackbar(activity.getString(R.string.error_invalid_backup))
                    return@launch
                }
                
                showImportConfirmationDialog(backupContent, metadata)
                
            } catch (e: Exception) {
                taskViewModel.postSnackbar(activity.getString(R.string.error_read_backup, e.message))
            }
        }
    }

    private fun showImportConfirmationDialog(backupContent: String, metadata: BackupManager.BackupMetadata) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val createdAtString = if (metadata.createdAt > 0) {
            dateFormat.format(Date(metadata.createdAt))
        } else {
            activity.getString(R.string.unknown)
        }
        
        val message = activity.getString(
            R.string.backup_info_format,
            createdAtString,
            metadata.taskCount,
            metadata.version
        )
        
        val items = arrayOf(
            activity.getString(R.string.import_mode_add),
            activity.getString(R.string.import_mode_replace)
        )
        var selectedOption = 0
        
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.dialog_import_title))
            .setMessage(message)
            .setSingleChoiceItems(items, 0) { _, which ->
                selectedOption = which
            }
            .setPositiveButton(activity.getString(R.string.button_import)) { _, _ ->
                performImport(backupContent, replaceExisting = selectedOption == 1)
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    private fun performImport(backupContent: String, replaceExisting: Boolean) {
        activity.lifecycleScope.launch {
            try {
                val tasks = backupManager.importTasks(backupContent)
                taskViewModel.importTasksFromBackup(tasks, replaceExisting)
            } catch (e: Exception) {
                taskViewModel.postSnackbar(activity.getString(R.string.error_import_tasks, e.message))
            }
        }
    }
}

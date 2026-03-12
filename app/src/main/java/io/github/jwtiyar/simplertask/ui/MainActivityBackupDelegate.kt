package io.github.jwtiyar.simplertask.ui

import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
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
import java.util.Arrays
import java.util.Date
import java.util.Locale
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

        showPasswordDialog(
            title = currentActivity.getString(R.string.dialog_backup_password_title),
            requireConfirmation = true
        ) { password ->
            currentActivity.lifecycleScope.launch {
                try {
                    val tasks = taskViewModel.getAllTasksForBackup()
                    val backupJson = backupManager.exportTasks(tasks, password)
                    backupManager.writeToUri(uri, backupJson)

                    taskViewModel.postToast(currentActivity.getString(R.string.backup_exported, tasks.size))
                } catch (e: Exception) {
                    taskViewModel.postSnackbar(currentActivity.getString(R.string.error_export_backup, e.message))
                } finally {
                    Arrays.fill(password, '\u0000')
                }
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

        val taskCountString = metadata.taskCount?.toString()
            ?: currentActivity.getString(R.string.backup_tasks_encrypted)

        val message = currentActivity.getString(
            R.string.backup_info_format,
            createdAtString,
            taskCountString,
            metadata.version.toString()
        )

        val items = arrayOf(
            currentActivity.getString(R.string.import_mode_add),
            currentActivity.getString(R.string.import_mode_replace)
        )
        var selectedOption = 0

        AlertDialog.Builder(currentActivity)
            .setTitle(currentActivity.getString(R.string.dialog_import_title))
            .setMessage(
                if (metadata.isEncrypted) {
                    "$message\n\n${currentActivity.getString(R.string.backup_encrypted_notice)}"
                } else {
                    message
                }
            )
            .setSingleChoiceItems(items, 0) { _, which ->
                selectedOption = which
            }
            .setPositiveButton(currentActivity.getString(R.string.button_import)) { _, _ ->
                if (metadata.isEncrypted) {
                    showPasswordDialog(
                        title = currentActivity.getString(R.string.dialog_import_password_title),
                        requireConfirmation = false
                    ) { password ->
                        performImport(
                            backupContent = backupContent,
                            replaceExisting = selectedOption == 1,
                            password = password
                        )
                    }
                } else {
                    performImport(
                        backupContent = backupContent,
                        replaceExisting = selectedOption == 1,
                        password = null
                    )
                }
            }
            .setNegativeButton(currentActivity.getString(R.string.cancel), null)
            .show()
    }

    private fun showPasswordDialog(
        title: String,
        requireConfirmation: Boolean,
        onPasswordConfirmed: (CharArray) -> Unit
    ) {
        val currentActivity = activity ?: return

        val container = LinearLayout(currentActivity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, 0)
        }

        val passwordInput = EditText(currentActivity).apply {
            hint = currentActivity.getString(R.string.backup_password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        container.addView(passwordInput)

        val confirmInput = if (requireConfirmation) {
            EditText(currentActivity).apply {
                hint = currentActivity.getString(R.string.backup_confirm_password_hint)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }.also { container.addView(it) }
        } else {
            null
        }

        val dialog = AlertDialog.Builder(currentActivity)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(currentActivity.getString(R.string.button_ok), null)
            .setNegativeButton(currentActivity.getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = passwordInput.text?.toString().orEmpty()
                val confirm = confirmInput?.text?.toString().orEmpty()

                when {
                    password.isBlank() -> {
                        passwordInput.error = currentActivity.getString(R.string.error_backup_password_empty)
                    }

                    requireConfirmation && password != confirm -> {
                        confirmInput?.error = currentActivity.getString(R.string.error_backup_password_mismatch)
                    }

                    else -> {
                        onPasswordConfirmed(password.toCharArray())
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun performImport(backupContent: String, replaceExisting: Boolean, password: CharArray?) {
        activity?.lifecycleScope?.launch {
            try {
                val tasks = backupManager.importTasks(backupContent, password)
                taskViewModel.importTasksFromBackup(tasks, replaceExisting)
            } catch (e: Exception) {
                val currentActivity = activity
                if (currentActivity != null) {
                    val errorRes = if ((e.message ?: "").contains("Incorrect backup password", ignoreCase = true)) {
                        R.string.error_backup_password_incorrect
                    } else {
                        R.string.error_import_tasks
                    }
                    val message = if (errorRes == R.string.error_import_tasks) {
                        currentActivity.getString(errorRes, e.message)
                    } else {
                        currentActivity.getString(errorRes)
                    }
                    taskViewModel.postSnackbar(message)
                }
            } finally {
                password?.let { Arrays.fill(it, '\u0000') }
            }
        }
    }
}

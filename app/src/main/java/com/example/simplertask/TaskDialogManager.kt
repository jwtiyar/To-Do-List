package com.example.simplertask

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.simplertask.Task

class TaskDialogManager(private val context: Context) {
    fun showAddTaskDialog(onTaskAdded: (Task) -> Unit) {
        // TODO: Implement dialog UI and logic
        AlertDialog.Builder(context)
            .setTitle("Add Task")
            .setMessage("Dialog UI here")
            .setPositiveButton("Add") { _, _ ->
                // onTaskAdded(newTask)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showEditTaskDialog(task: Task, onTaskUpdated: (Task) -> Unit) {
        // TODO: Implement dialog UI and logic
        AlertDialog.Builder(context)
            .setTitle("Edit Task")
            .setMessage("Dialog UI here")
            .setPositiveButton("Save") { _, _ ->
                // onTaskUpdated(updatedTask)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

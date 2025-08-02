package com.example.taskmanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanager.databinding.ItemTaskBinding
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TaskAdapter(
    private val tasks: MutableList<Task>,
    private val onTaskClick: (Task) -> Unit // Callback to MainActivity
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val timeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())

    inner class TaskViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(task: Task) {
            binding.apply {
                taskTitle.text = task.title
                taskDescription.text = task.description
               
                // Temporarily remove listener to prevent firing during programmatic update of isChecked
                taskCheckBox.setOnCheckedChangeListener(null)
                taskCheckBox.isChecked = task.isCompleted
                // The functional listener for user interactions is (re-)attached below

                if (task.scheduledTimeMillis != null) {
                    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(task.scheduledTimeMillis!!), ZoneId.systemDefault())
                    // Use string resource for concatenation and translation
                    taskScheduledTime.text = itemView.context.getString(R.string.task_reminder_prefix, ldt.format(timeFormatter))
                    taskScheduledTime.visibility = View.VISIBLE
                } else {
                    taskScheduledTime.visibility = View.GONE
                }
                
                updateVisualState(task) // Separate method for UI updates

                taskCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                        val currentTask = tasks[bindingAdapterPosition]
                        currentTask.isCompleted = isChecked
                        updateVisualState(currentTask)
                        onTaskClick(currentTask) // Let MainActivity handle DB and broader UI refresh
                    }
                }
                
                root.setOnClickListener {
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                        val currentTask = tasks[bindingAdapterPosition]
                        currentTask.isCompleted = !currentTask.isCompleted
                        taskCheckBox.isChecked = currentTask.isCompleted // Sync checkbox
                        updateVisualState(currentTask)
                        onTaskClick(currentTask) // Let MainActivity handle DB and broader UI refresh
                    }
                }
            }
        }

        private fun updateVisualState(task: Task) {
            binding.apply {
                if (task.isCompleted) {
                    taskTitle.alpha = 0.6f
                    taskDescription.alpha = 0.6f
                    taskScheduledTime.alpha = 0.6f
                    root.alpha = 0.8f
                } else {
                    taskTitle.alpha = 1.0f
                    taskDescription.alpha = 1.0f
                    taskScheduledTime.alpha = 1.0f
                    root.alpha = 1.0f
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount(): Int = tasks.size

    // Removed unused updateTasks function
    // Removed unused removeCompletedTasks function
}

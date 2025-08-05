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
private var tasks: List<Task>,
    private val onTaskClick: (Task) -> Unit, // Callback to MainActivity
    private val onEditClick: (Task) -> Unit // Edit callback
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {
    fun updateTasks(newTasks: List<Task>) {
        val diffCallback = object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = tasks.size
            override fun getNewListSize() = newTasks.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return tasks[oldItemPosition].id == newTasks[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return tasks[oldItemPosition] == newTasks[newItemPosition]
            }
        }
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        tasks = newTasks
        diffResult.dispatchUpdatesTo(this)
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())

    inner class TaskViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(task: Task) {
            binding.apply {
                taskTitle.text = task.title
                taskDescription.text = task.description
                // Always use black text for visibility
                taskTitle.setTextColor(root.context.getColor(R.color.black))
                taskDescription.setTextColor(root.context.getColor(R.color.black))

                // Priority chip
                chipPriority.text = when (task.priority) {
                    Priority.LOW -> "Low"
                    Priority.MEDIUM -> "Medium"
                    Priority.HIGH -> "High"
                }
                val chipColor = when (task.priority) {
                    Priority.LOW -> R.color.priority_low
                    Priority.MEDIUM -> R.color.priority_medium
                    Priority.HIGH -> R.color.priority_high
                }
                chipPriority.setBackgroundResource(R.drawable.chip_priority_bg)
                chipPriority.background.setTint(root.context.getColor(chipColor))

                // Temporarily remove listener to prevent firing during programmatic update of isChecked
                taskCheckBox.setOnCheckedChangeListener(null)
                taskCheckBox.isChecked = task.isCompleted

                if (task.dueDateMillis != null) {
                    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(task.dueDateMillis!!), ZoneId.systemDefault())
                    taskScheduledTime.text = "Due: " + ldt.format(timeFormatter)
                    taskScheduledTime.visibility = View.VISIBLE
                } else {
                    taskScheduledTime.visibility = View.GONE
                }

                updateVisualState()

                taskCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION && pos < tasks.size) {
                        val currentTask = tasks[pos]
                        currentTask.isCompleted = isChecked
                        updateVisualState()
                        onTaskClick(currentTask)
                    }
                }

                root.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION && pos < tasks.size) {
                        val currentTask = tasks[pos]
                        currentTask.isCompleted = !currentTask.isCompleted
                        taskCheckBox.isChecked = currentTask.isCompleted
                        updateVisualState()
                        onTaskClick(currentTask)
                    }
                }
                btnEditTask.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION && pos < tasks.size) {
                        val currentTask = tasks[pos]
                        onEditClick(currentTask)
                    }
                }
            }
        }

        private fun updateVisualState() {
            binding.apply {
                taskTitle.alpha = 1.0f
                taskDescription.alpha = 1.0f
                taskScheduledTime.alpha = 1.0f
                root.alpha = 1.0f
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

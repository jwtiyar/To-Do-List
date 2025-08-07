package com.example.simplertask

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.simplertask.databinding.ItemTaskBinding
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TaskAdapter(
    private var tasks: List<Task>,
    private val onTaskClick: (Task) -> Unit, // Callback for task completion toggle
    private val onEditClick: (Task) -> Unit, // Callback for edit action
    private val onTaskAction: (Task, String) -> Unit // Callback for save/archive actions
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
                
                root.setOnLongClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION && pos < tasks.size) {
                        val currentTask = tasks[pos]
                        showTaskActions(currentTask)
                        true
                    } else {
                        false
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

        private fun showTaskActions(task: Task) {
            val actions = arrayOf(
                if (task.isSaved) "Remove from Saved" else "Save Task",
                if (task.isArchived) "Unarchive" else "Archive"
            )
            
            androidx.appcompat.app.AlertDialog.Builder(binding.root.context)
                .setTitle("Task Actions")
                .setItems(actions) { _, which ->
                    when (which) {
                        0 -> onTaskAction(task, "TOGGLE_SAVE")
                        1 -> onTaskAction(task, "TOGGLE_ARCHIVE")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun updateVisualState() {
            binding.apply {
                // Update visual state based on completion
                if (taskCheckBox.isChecked) {
                    // Task is completed
                    taskTitle.paintFlags = taskTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    taskDescription.paintFlags = taskDescription.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    root.alpha = 0.6f
                } else {
                    // Task is not completed
                    taskTitle.paintFlags = taskTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    taskDescription.paintFlags = taskDescription.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    root.alpha = 1.0f
                }
                
                // Update save icon visibility based on task state
                val saveIconRes = if (bindingAdapterPosition != RecyclerView.NO_POSITION && 
                    bindingAdapterPosition < tasks.size) {
                    val currentTask = tasks[bindingAdapterPosition]
                    if (currentTask.isSaved) {
                        R.drawable.ic_bookmark_24
                    } else {
                        R.drawable.ic_bookmark_border_24
                    }
                } else {
                    R.drawable.ic_bookmark_border_24
                }
                
                // If you have a save icon in your layout, uncomment these lines
                // btnSave.setImageResource(saveIconRes)
                // btnSave.contentDescription = if (saveIconRes == R.drawable.ic_bookmark_24) "Unsave task" else "Save task"
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

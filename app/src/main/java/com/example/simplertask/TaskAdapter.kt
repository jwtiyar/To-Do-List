package com.example.simplertask

import android.annotation.SuppressLint
import android.graphics.Rect
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
    /**
     * Returns a copy of the current tasks list
     */
    fun getTasks(): List<Task> = tasks.toList()
    
    /**
     * Updates the tasks list using DiffUtil for efficient RecyclerView updates
     * @param newTasks The new list of tasks to display
     */
    fun updateTasks(newTasks: List<Task>) {
        val diffCallback = object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = tasks.size
            
            override fun getNewListSize(): Int = newTasks.size
            
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return tasks[oldItemPosition].id == newTasks[newItemPosition].id
            }
            
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = tasks[oldItemPosition]
                val newItem = newTasks[newItemPosition]
                return oldItem == newItem
            }
            
            @SuppressLint("DiffUtilEquals")
            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                // You can return specific change payloads here if needed for more granular updates
                return super.getChangePayload(oldItemPosition, newItemPosition)
            }
        }
        
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        tasks = newTasks.toList() // Create a new list to ensure immutability
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

                // Store the previous checked state to prevent multiple callbacks
                var previousCheckedState = taskCheckBox.isChecked
                
                // Handle task completion when clicking the checkbox
                taskCheckBox.setOnCheckedChangeListener(null) // Clear any existing listeners
                taskCheckBox.isChecked = task.isCompleted // Set initial state
                
                taskCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    // Only proceed if the state actually changed
                    if (isChecked != previousCheckedState) {
                        previousCheckedState = isChecked
                        val pos = bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION && pos < tasks.size) {
                            val currentTask = tasks[pos].copy(isCompleted = isChecked)
                            updateVisualState()
                            onTaskClick(currentTask)
                        }
                    }
                }

                // Handle task item clicks (excluding the checkbox)
                // Removed item click toggling for completion; only checkbox marks as completed
                
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
                if (task.isArchived) "Unarchive" else "Archive",
                "Cancel"
            )
            
            androidx.appcompat.app.AlertDialog.Builder(binding.root.context)
                .setTitle("Task Actions")
                .setItems(actions) { _, which ->
                    when (which) {
                        0 -> onTaskAction(task, if (task.isSaved) "unsave" else "save")
                        1 -> onTaskAction(task, if (task.isArchived) "unarchive" else "archive")
                        // 2 is Cancel, do nothing
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
    
    /**
     * Helper function to check if a click is on the checkbox
     */
    private fun isClickOnCheckbox(view: View, checkbox: View): Boolean {
        val checkboxRect = Rect()
        checkbox.getHitRect(checkboxRect)
        // Convert checkbox coordinates to the parent view's coordinate system
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        checkboxRect.offset(-location[0], -location[1])
        
        // Get the click coordinates relative to the view
        val x = view.width / 2
        val y = view.height / 2
        
        // Check if the click was inside the checkbox bounds
        return checkboxRect.contains(x, y)
    }

    // Removed unused updateTasks function
    // Removed unused removeCompletedTasks function
}

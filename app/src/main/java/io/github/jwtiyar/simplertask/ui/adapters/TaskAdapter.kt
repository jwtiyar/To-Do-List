package io.github.jwtiyar.simplertask.ui.adapters

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.jwtiyar.simplertask.databinding.ItemTaskBinding
import java.time.format.DateTimeFormatter
import io.github.jwtiyar.simplertask.data.model.TaskAction
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.isRecurring
import io.github.jwtiyar.simplertask.R
import java.util.Locale
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TaskAdapter(
    private var tasks: List<Task>,
    private val onTaskClick: (Task) -> Unit,
    private val onEditClick: (Task) -> Unit,
    private val onTaskAction: (Task, TaskAction) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {
    companion object {
        // Ensures only one task actions dialog is visible at a time across instances
        private var currentDialog: androidx.appcompat.app.AlertDialog? = null
    }
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

                // Handle description visibility and content
                if (task.description.isNotBlank()) {
                    taskDescription.text = task.description
                    taskDescription.visibility = View.VISIBLE
                } else {
                    taskDescription.visibility = View.GONE
                }

                // Enhanced priority chip styling
                val (chipText, chipColor, chipTextColor) = when (task.priority) {
                    Priority.LOW -> Triple(
                        root.context.getString(R.string.priority_low),
                        root.context.getColor(R.color.priority_low),
                        root.context.getColor(android.R.color.white)
                    )
                    Priority.MEDIUM -> Triple(
                        root.context.getString(R.string.priority_medium),
                        root.context.getColor(R.color.priority_medium),
                        root.context.getColor(android.R.color.black)
                    )
                    Priority.HIGH -> Triple(
                        root.context.getString(R.string.priority_high),
                        root.context.getColor(R.color.priority_high),
                        root.context.getColor(android.R.color.white)
                    )
                }

                chipPriority.text = chipText
                chipPriority.setTextColor(chipTextColor)
                chipPriority.chipBackgroundColor = android.content.res.ColorStateList.valueOf(chipColor)

                taskCheckBox.setOnCheckedChangeListener(null)
                taskCheckBox.isChecked = task.isCompleted

                // Enhanced due date display
                if (task.dueDateMillis != null) {
                    val dueInstant = Instant.ofEpochMilli(task.dueDateMillis!!)
                    val now = Instant.now()
                    val ldt = LocalDateTime.ofInstant(dueInstant, ZoneId.systemDefault())

                    val timeText = when {
                        dueInstant.isBefore(now) -> {
                            // Overdue - show in error color
                            taskScheduledTime.setTextColor(root.context.getColor(android.R.color.holo_red_dark))
                            "Overdue: ${ldt.format(timeFormatter)}"
                        }
                        dueInstant.isBefore(now.plusSeconds(86400)) -> { // Next 24 hours
                            // Due soon - show in warning color
                            taskScheduledTime.setTextColor(root.context.getColor(android.R.color.holo_orange_dark))
                            root.context.getString(R.string.due_prefix, ldt.format(timeFormatter))
                        }
                        else -> {
                            // Future - show in primary color
                            taskScheduledTime.setTextColor(root.context.getColor(android.R.color.darker_gray))
                            root.context.getString(R.string.due_prefix, ldt.format(timeFormatter))
                        }
                    }
                    taskScheduledTime.text = timeText
                    taskScheduledTime.visibility = View.VISIBLE
                } else {
                    taskScheduledTime.visibility = View.GONE
                }

                // Enhanced recurring indicator
                iconRecurring.visibility = if (task.isRecurring()) View.VISIBLE else View.GONE

                // Category indicator - simple color dot for now
                if (task.categoryId != null) {
                    categoryIndicator.visibility = View.VISIBLE
                    // Use different colors based on category ID
                    val color = when (task.categoryId) {
                        1 -> android.graphics.Color.parseColor("#1E88E5") // Work - Blue
                        2 -> android.graphics.Color.parseColor("#43A047") // Personal - Green
                        3 -> android.graphics.Color.parseColor("#E53935") // Health - Red
                        4 -> android.graphics.Color.parseColor("#8E24AA") // Learning - Purple
                        5 -> android.graphics.Color.parseColor("#FF9800") // Shopping - Orange
                        6 -> android.graphics.Color.parseColor("#0097A7") // Home - Teal
                        else -> android.graphics.Color.parseColor("#757575") // Default - Gray
                    }
                    categoryIndicator.background.setTint(color)
                } else {
                    categoryIndicator.visibility = View.GONE
                }

                updateVisualState(task)

                var previousCheckedState = taskCheckBox.isChecked
                taskCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked != previousCheckedState) {
                        previousCheckedState = isChecked
                        val current = task.copy(isCompleted = isChecked)
                        updateVisualState(current)

                        // Add smooth animation for completion
                        if (isChecked) {
                            val animation = android.view.animation.AnimationUtils.loadAnimation(
                                root.context, R.anim.task_complete_fade
                            )
                            root.startAnimation(animation)
                        }

                        onTaskClick(current)
                    }
                }

                root.setOnLongClickListener { 
                    showTaskActions(task); true 
                }
                btnEditTask.setOnClickListener { 
                    onEditClick(task) 
                }
            }
        }

        private fun showTaskActions(task: Task) {
            val ctx = binding.root.context
            // Dismiss any previously shown dialog to avoid stacking / duplicate cancel buttons
            currentDialog?.dismiss()
            val actionItems: List<Pair<String, TaskAction>> = mutableListOf<Pair<String, TaskAction>>().apply {
                add(if (task.isSaved) ctx.getString(R.string.action_unsave) to TaskAction.UNSAVE else ctx.getString(R.string.action_save) to TaskAction.SAVE)
                add(if (task.isArchived) ctx.getString(R.string.action_unarchive) to TaskAction.UNARCHIVE else ctx.getString(R.string.action_archive) to TaskAction.ARCHIVE)
            }
            val labels = actionItems.map { it.first }.toTypedArray()
            currentDialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle(ctx.getString(R.string.task_actions_title))
                .setItems(labels) { _, which ->
                    val (_, action) = actionItems[which]
                    onTaskAction(task, action)
                }.create()
            currentDialog?.setOnDismissListener { if (currentDialog?.isShowing != true) currentDialog = null }
            currentDialog?.show()
        }

        private fun updateVisualState(task: Task) {
            binding.apply {
                if (task.isCompleted) {
                    // Completed state - subtle strikethrough and muted appearance
                    taskTitle.paintFlags = taskTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    taskTitle.setTextColor(root.context.getColor(android.R.color.darker_gray))
                    taskDescription.paintFlags = taskDescription.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    taskDescription.setTextColor(root.context.getColor(android.R.color.darker_gray))

                    // Subtle background tint for completed tasks
                    root.setCardBackgroundColor(root.context.getColor(android.R.color.white))

                    // Disable checkbox interaction
                    taskCheckBox.isEnabled = false
                } else {
                    // Active state - normal appearance
                    taskTitle.paintFlags = taskTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    taskTitle.setTextColor(root.context.getColor(android.R.color.black))
                    taskDescription.paintFlags = taskDescription.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    taskDescription.setTextColor(root.context.getColor(android.R.color.darker_gray))

                    // Normal background
                    root.setCardBackgroundColor(root.context.getColor(android.R.color.white))

                    // Enable checkbox interaction
                    taskCheckBox.isEnabled = true
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

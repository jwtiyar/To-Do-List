package com.example.simplertask

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.simplertask.databinding.ItemTaskBinding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TaskPagingAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onEditClick: (Task) -> Unit,
    private val onTaskAction: (Task, TaskAction) -> Unit
) : PagingDataAdapter<Task, TaskPagingAdapter.TaskVH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean = oldItem == newItem
        }
    private var currentDialog: androidx.appcompat.app.AlertDialog? = null
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())

    inner class TaskVH(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(task: Task?) {
            if (task == null) return
            binding.taskTitle.text = task.title
            binding.taskDescription.text = task.description
            binding.chipPriority.text = when (task.priority) {
                Priority.LOW -> binding.root.context.getString(R.string.priority_low)
                Priority.MEDIUM -> binding.root.context.getString(R.string.priority_medium)
                Priority.HIGH -> binding.root.context.getString(R.string.priority_high)
            }
            val chipColor = when (task.priority) {
                Priority.LOW -> R.color.priority_low
                Priority.MEDIUM -> R.color.priority_medium
                Priority.HIGH -> R.color.priority_high
            }
            binding.chipPriority.setBackgroundResource(R.drawable.chip_priority_bg)
            binding.chipPriority.background.setTint(binding.root.context.getColor(chipColor))
            binding.taskCheckBox.setOnCheckedChangeListener(null)
            binding.taskCheckBox.isChecked = task.isCompleted
            if (task.dueDateMillis != null) {
                val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(task.dueDateMillis!!), ZoneId.systemDefault())
                binding.taskScheduledTime.text = binding.root.context.getString(R.string.due_prefix, ldt.format(timeFormatter))
                binding.taskScheduledTime.visibility = android.view.View.VISIBLE
            } else binding.taskScheduledTime.visibility = android.view.View.GONE

            updateVisualState(task)
            var previousChecked = binding.taskCheckBox.isChecked
            binding.taskCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != previousChecked) {
                    previousChecked = isChecked
                    onTaskClick(task.copy(isCompleted = isChecked))
                }
            }
            binding.root.setOnLongClickListener { showActions(task); true }
            binding.btnEditTask.setOnClickListener { onEditClick(task) }
        }

        private fun showActions(task: Task) {
            val ctx = itemView.context
            currentDialog?.dismiss()
            val actionItems = buildList {
                add(if (task.isSaved) ctx.getString(R.string.action_unsave) to TaskAction.UNSAVE else ctx.getString(R.string.action_save) to TaskAction.SAVE)
                add(if (task.isArchived) ctx.getString(R.string.action_unarchive) to TaskAction.UNARCHIVE else ctx.getString(R.string.action_archive) to TaskAction.ARCHIVE)
            }
            val labels = actionItems.map { it.first }.toTypedArray()
            currentDialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle(ctx.getString(R.string.task_actions_title))
                .setItems(labels) { _, which -> onTaskAction(task, actionItems[which].second) }
                .create()
            currentDialog?.setOnDismissListener { if (currentDialog?.isShowing != true) currentDialog = null }
            currentDialog?.show()
        }

        private fun updateVisualState(task: Task) {
            val title = itemView.findViewById<android.widget.TextView>(R.id.taskTitle)
            val desc = itemView.findViewById<android.widget.TextView>(R.id.taskDescription)
            if (task.isCompleted) {
                title.paintFlags = title.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                desc.paintFlags = desc.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                itemView.alpha = 0.6f
            } else {
                title.paintFlags = title.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                desc.paintFlags = desc.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                itemView.alpha = 1f
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskVH {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskVH(binding)
    }

    override fun onBindViewHolder(holder: TaskVH, position: Int) {
        holder.bind(getItem(position))
    }
}

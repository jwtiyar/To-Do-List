package io.github.jwtiyar.simplertask.ui.adapters

import io.github.jwtiyar.simplertask.data.model.TaskAction
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.Category
import io.github.jwtiyar.simplertask.data.local.entity.isRecurring
import io.github.jwtiyar.simplertask.R

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.github.jwtiyar.simplertask.databinding.ItemTaskBinding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TaskPagingAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onEditClick: (Task) -> Unit,
    private val onTaskAction: (Task, TaskAction) -> Unit,
    private val categories: () -> List<Category> = { emptyList() }
) : PagingDataAdapter<Task, TaskPagingAdapter.TaskVH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
                return oldItem.id == newItem.id
            }
            override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
                return oldItem == newItem
            }
        }
    }

    fun getTaskAtPosition(position: Int): Task? {
        return getItem(position)
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())

    inner class TaskVH(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(task: Task?) {
            if (task == null) return
            
            binding.taskTitle.text = task.title
            binding.checkboxComplete.isChecked = task.isCompleted
            binding.checkboxComplete.contentDescription = binding.root.context.getString(
                if (task.isCompleted) R.string.mark_task_pending else R.string.mark_task_complete,
                task.title
            )
            binding.checkboxComplete.setOnClickListener {
                binding.checkboxComplete.isChecked = task.isCompleted
                onTaskClick(task)
            }
            
            if (task.description.isNotBlank()) {
                binding.taskDescription.text = task.description
                binding.taskDescription.visibility = android.view.View.VISIBLE
            } else {
                binding.taskDescription.visibility = android.view.View.GONE
            }
            binding.chipPriority.text = when (task.priority) {
                Priority.LOW -> binding.root.context.getString(R.string.priority_low)
                Priority.MEDIUM -> binding.root.context.getString(R.string.priority_medium)
                Priority.HIGH -> binding.root.context.getString(R.string.priority_high)
            }
            binding.chipPriority.visibility = if (task.priority != Priority.MEDIUM) android.view.View.VISIBLE else android.view.View.GONE
            val chipColor = when (task.priority) {
                Priority.LOW -> R.color.priority_low
                Priority.MEDIUM -> R.color.priority_medium
                Priority.HIGH -> R.color.priority_high
            }
            binding.chipPriority.setChipBackgroundColorResource(chipColor)

            if (task.isRecurring()) {
                val unit = when (task.recurrenceType) {
                    io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType.DAILY,
                    io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType.CUSTOM -> R.string.recurrence_daily
                    io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType.WEEKLY -> R.string.recurrence_weekly
                    io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType.MONTHLY -> R.string.recurrence_monthly
                    null -> null
                }
                val summary = if (unit != null) {
                    binding.root.context.getString(
                        R.string.task_repeats_summary, task.recurrenceInterval, binding.root.context.getString(unit)
                    )
                } else {
                    binding.root.context.getString(R.string.repeat_preview)
                }
                binding.iconRecurring.text = summary
                binding.iconRecurring.visibility = android.view.View.VISIBLE
            } else {
                binding.iconRecurring.visibility = android.view.View.GONE
            }

            val category = categories().firstOrNull { it.id == task.categoryId }
            binding.categoryIndicator.visibility = if (category != null) android.view.View.VISIBLE else android.view.View.GONE
            category?.let {
                binding.categoryIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(it.color)
            }
            binding.btnTaskActions.contentDescription = binding.root.context.getString(
                R.string.task_actions_for, task.title
            )

            if (task.dueDateMillis != null) {
                val context = binding.root.context
                val now = LocalDateTime.now()
                val dueLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(task.dueDateMillis!!), ZoneId.systemDefault())
                val timeOnly = dueLdt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
                val dateOnly = dueLdt.toLocalDate()
                val today = now.toLocalDate()

                val dueText = when {
                    dateOnly == today -> context.getString(R.string.due_today, timeOnly)
                    dateOnly == today.plusDays(1) -> context.getString(R.string.due_tomorrow, timeOnly)
                    dueLdt.isBefore(now) && !task.isCompleted -> context.getString(
                        R.string.due_overdue,
                        dueLdt.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.getDefault()))
                    )
                    else -> context.getString(R.string.due_prefix, dueLdt.format(timeFormatter))
                }
                binding.taskScheduledTime.text = dueText
                val textColor = if (dueLdt.isBefore(now) && !task.isCompleted) {
                    context.getColor(R.color.error)
                } else {
                    context.getColor(R.color.primary)
                }
                binding.taskScheduledTime.setTextColor(textColor)
                binding.taskScheduledTime.compoundDrawablesRelative[0]?.setTint(textColor)
                binding.taskScheduledTime.visibility = android.view.View.VISIBLE
            } else {
                binding.taskScheduledTime.visibility = android.view.View.GONE
            }

            updateVisualState(task)
            binding.btnTaskActions.setOnClickListener { showActions(task) }
            binding.root.setOnClickListener { onEditClick(task) }
        }

        private fun showActions(task: Task) {
            val ctx = itemView.context
            val actionItems = buildList {
                add(if (task.isSaved) ctx.getString(R.string.action_unsave) to TaskAction.UNSAVE else ctx.getString(R.string.action_save) to TaskAction.SAVE)
                add(if (task.isArchived) ctx.getString(R.string.action_unarchive) to TaskAction.UNARCHIVE else ctx.getString(R.string.action_archive) to TaskAction.ARCHIVE)
                add(ctx.getString(R.string.delete) to TaskAction.DELETE)
            }
            PopupMenu(ctx, binding.btnTaskActions).apply {
                actionItems.forEach { (label, action) ->
                    menu.add(label).setOnMenuItemClickListener {
                        onTaskAction(task, action)
                        true
                    }
                }
                show()
            }
        }

        private fun updateVisualState(task: Task) {
            if (task.isCompleted) {
                binding.taskTitle.paintFlags = binding.taskTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                binding.taskDescription.paintFlags = binding.taskDescription.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                itemView.alpha = 0.6f
            } else {
                binding.taskTitle.paintFlags = binding.taskTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.taskDescription.paintFlags = binding.taskDescription.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                itemView.alpha = 1f
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskVH {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskVH(binding)
    }

    override fun onBindViewHolder(holder: TaskVH, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }
    
    override fun getItemCount(): Int {
        return super.getItemCount()
    }
}

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
    private val onTaskClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val timeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())

    inner class TaskViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(task: Task) {
            binding.apply {
                taskTitle.text = task.title
                taskDescription.text = task.description
                taskCheckBox.isChecked = task.isCompleted
                
                // Display scheduled time if available
                if (task.scheduledTimeMillis != null) {
                    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(task.scheduledTimeMillis!!), ZoneId.systemDefault())
                    taskScheduledTime.text = "Reminder: ${ldt.format(timeFormatter)}"
                    taskScheduledTime.visibility = View.VISIBLE
                } else {
                    taskScheduledTime.visibility = View.GONE
                }
                
                // Update visual state based on completion
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
                
                // Handle checkbox click
                taskCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    task.isCompleted = isChecked
                    onTaskClick(task)
                    notifyItemChanged(bindingAdapterPosition)
                }
                
                // Handle item click
                root.setOnClickListener {
                    task.isCompleted = !task.isCompleted
                    taskCheckBox.isChecked = task.isCompleted
                    onTaskClick(task)
                    notifyItemChanged(bindingAdapterPosition)
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

    fun updateTasks(newTasks: List<Task>) {
        tasks.clear()
        tasks.addAll(newTasks)
        notifyDataSetChanged()
    }

    fun removeCompletedTasks() {
        val completedTasks = tasks.filter { it.isCompleted }
        tasks.removeAll(completedTasks.toSet())
        notifyDataSetChanged()
    }
}

package io.github.jwtiyar.simplertask.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.data.local.TaskDatabase
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Service that provides the data for the widget's ListView
 */
class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TaskWidgetViewsFactory(this.applicationContext)
    }
}

/**
 * Factory that creates RemoteViews for each item in the widget's ListView
 */
class TaskWidgetViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<Task> = emptyList()
    private val timeFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.getDefault())

    override fun onCreate() {
        // Initialize
    }

    override fun onDataSetChanged() {
        // Load pending tasks from database
        // This is called on the main thread, so we use runBlocking
        runBlocking {
            try {
                val database = TaskDatabase.getDatabase(context)
                val taskDao = database.taskDao()
                // Get pending tasks (not completed, not archived)
                tasks = taskDao.getPendingTasks().first().take(10) // Limit to 10 tasks
            } catch (e: Exception) {
                tasks = emptyList()
            }
        }
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= tasks.size) {
            return RemoteViews(context.packageName, R.layout.widget_task_item)
        }

        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.widget_task_item)

        // Set task title
        views.setTextViewText(R.id.widget_task_title, task.title)

        // Set task description (if not empty)
        if (task.description.isNotBlank()) {
            views.setTextViewText(R.id.widget_task_description, task.description)
            views.setViewVisibility(R.id.widget_task_description, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_task_description, android.view.View.GONE)
        }

        // Set priority indicator color
        val priorityColor = when (task.priority) {
            Priority.HIGH -> context.getColor(R.color.priority_high)
            Priority.MEDIUM -> context.getColor(R.color.priority_medium)
            Priority.LOW -> context.getColor(R.color.priority_low)
        }
        views.setInt(R.id.widget_priority_indicator, "setBackgroundColor", priorityColor)

        // Set due date if available
        if (task.dueDateMillis != null) {
            val ldt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(task.dueDateMillis!!),
                ZoneId.systemDefault()
            )
            views.setTextViewText(R.id.widget_task_due_date, ldt.format(timeFormatter))
            views.setViewVisibility(R.id.widget_task_due_date, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_task_due_date, android.view.View.GONE)
        }

        // Set up click intent for this item
        val fillInIntent = Intent().apply {
            putExtra("task_id", task.id)
        }
        views.setOnClickFillInIntent(R.id.widget_task_item_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position < tasks.size) tasks[position].id.toLong() else position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}

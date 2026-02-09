package io.github.jwtiyar.simplertask.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.scopes.ActivityScoped
import io.github.jwtiyar.simplertask.MainActivity
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.local.entity.isRecurring
import io.github.jwtiyar.simplertask.data.model.TaskAction
import io.github.jwtiyar.simplertask.databinding.ActivityMainBinding
import io.github.jwtiyar.simplertask.service.NotificationHelper
import io.github.jwtiyar.simplertask.ui.adapters.TaskPagingAdapter
import io.github.jwtiyar.simplertask.ui.dialogs.TaskDialogManager
import io.github.jwtiyar.simplertask.ui.fragments.TaskListFragment
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import javax.inject.Inject

/**
 * Delegate responsible for MainActivity UI setup and management.
 * Handles ViewPager, buttons, search UI, and basic UI interactions.
 */
@ActivityScoped
class MainUiDelegate @Inject constructor(
    private val notificationHelper: NotificationHelper,
    private val dialogManager: TaskDialogManager
) {
    private lateinit var activity: MainActivity
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: TaskViewModel

    lateinit var searchAdapter: TaskPagingAdapter
        private set
    private var currentTaskFilter: TaskViewModel.TaskFilter = TaskViewModel.TaskFilter.PENDING

    fun attach(activity: MainActivity, binding: ActivityMainBinding) {
        this.activity = activity
        this.binding = binding
        this.viewModel = ViewModelProvider(activity)[TaskViewModel::class.java]
        
        // Initialize searchAdapter here as it depends on viewModel and notificationHelper
        searchAdapter = createSearchAdapter()
    }

    fun setupViewPager() {
        val pagerAdapter = TasksPagerAdapter(activity)
        binding.viewPager.adapter = pagerAdapter

        // Link TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = activity.getString(R.string.tab_pending)
                    tab.setIcon(R.drawable.ic_pending_24dp)
                }
                1 -> {
                    tab.text = activity.getString(R.string.tab_completed)
                    tab.setIcon(R.drawable.ic_completed_24dp)
                }
            }
        }.attach()

        // Update currentTaskFilter when page changes
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentTaskFilter = when (position) {
                    0 -> TaskViewModel.TaskFilter.PENDING
                    1 -> TaskViewModel.TaskFilter.COMPLETED
                    else -> TaskViewModel.TaskFilter.PENDING
                }
            }
        })
    }

    fun setupButtons() {
        binding.fabAddTask.setOnClickListener { showAddTaskDialog() }
        binding.btnClearCompleted.setOnClickListener {
            val completedCount = viewModel.completedCount.value
            if (currentTaskFilter == TaskViewModel.TaskFilter.COMPLETED && completedCount > 0) {
                viewModel.clearCompletedTasks()
                viewModel.postToast(activity.getString(R.string.removed_completed_tasks, completedCount))
            } else if (currentTaskFilter == TaskViewModel.TaskFilter.PENDING) {
                viewModel.postToast(activity.getString(R.string.switch_to_completed_tab))
            } else {
                viewModel.postToast(activity.getString(R.string.no_completed_tasks))
            }
        }
        binding.btnResetTasks.setOnClickListener {
            try {
                viewModel.resetAllTasks()
                viewModel.postToast(activity.getString(R.string.all_tasks_reset))
            } catch (e: Exception) {
                Snackbar.make(binding.root, activity.getString(R.string.error_resetting_tasks, e.message), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    fun setupSearchBar() {
        binding.searchBar.setOnClickListener { binding.searchView.show() }
        
        binding.searchRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        binding.searchRecyclerView.adapter = searchAdapter

        binding.searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.searchTasks(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun createSearchAdapter(): TaskPagingAdapter {
        return TaskPagingAdapter(
            onTaskClick = { task ->
                viewModel.updateTask(task)
                val message = if (task.isCompleted) {
                    notificationHelper.cancelNotification(task)
                    activity.getString(R.string.task_completed, task.title)
                } else {
                    if (task.dueDateMillis != null) notificationHelper.scheduleNotification(task)
                    activity.getString(R.string.task_pending, task.title)
                }
                viewModel.postToast(message)
            },
            onEditClick = { task -> showEditTaskDialog(task) },
            onTaskAction = { task, action ->
                val updated = when (action) {
                    TaskAction.SAVE -> task.copy(isSaved = true)
                    TaskAction.UNSAVE -> task.copy(isSaved = false)
                    TaskAction.ARCHIVE -> task.copy(isArchived = true, isSaved = false)
                    TaskAction.UNARCHIVE -> task.copy(isArchived = false)
                }
                viewModel.updateTask(updated)
                val msgRes = when (action) {
                    TaskAction.SAVE -> R.string.task_saved
                    TaskAction.UNSAVE -> R.string.task_unsaved
                    TaskAction.ARCHIVE -> R.string.task_archived
                    TaskAction.UNARCHIVE -> R.string.task_unarchived
                }
                viewModel.postToast(activity.getString(msgRes))
            },
            onSwipeComplete = { task, _ ->
                viewModel.toggleTaskCompletion(task)
                viewModel.postSnackbar(
                    message = activity.getString(R.string.task_completed, task.title),
                    actionLabel = activity.getString(R.string.undo),
                    action = { viewModel.toggleTaskCompletion(task) }
                )
            },
            onSwipeDelete = { task, _ ->
                viewModel.deleteTask(task)
                viewModel.postToast(activity.getString(R.string.task_deleted, task.title))
            }
        )
    }

    private fun showEditTaskDialog(task: Task) {
        dialogManager.showEditTaskDialog(task) { updatedTask ->
            // Cancel old notification before updating
            notificationHelper.cancelNotification(task)

            // Update the task in database
            viewModel.updateTask(updatedTask)

            // Reschedule notification if the task has a due date and is not completed
            NotificationHelper.scheduleOrToggle(notificationHelper, updatedTask)
        }
    }

    private fun showAddTaskDialog() {
        dialogManager.showAddTaskDialog { task ->
            if (task.isRecurring()) {
                viewModel.addRecurringTask(
                    title = task.title,
                    description = task.description,
                    priority = task.priority,
                    dueDateMillis = task.dueDateMillis,
                    recurrenceType = task.recurrenceType!!,
                    recurrenceInterval = task.recurrenceInterval,
                    recurrenceEndDate = task.recurrenceEndDate,
                    categoryId = task.categoryId,
                    onTaskInserted = { insertedTask ->
                        // Schedule notification with the task that has the correct database ID
                        if (insertedTask.dueDateMillis != null) {
                            notificationHelper.scheduleNotification(insertedTask)
                        }
                    }
                )
            } else {
                viewModel.addTask(
                    title = task.title,
                    description = task.description,
                    priority = task.priority,
                    dueDateMillis = task.dueDateMillis,
                    categoryId = task.categoryId,
                    onTaskInserted = { insertedTask ->
                        // Schedule notification with the task that has the correct database ID
                        if (insertedTask.dueDateMillis != null) {
                            notificationHelper.scheduleNotification(insertedTask)
                        }
                    }
                )
            }
        }
    }

    fun updateUI(title: String) {
        binding.topAppBar.title = title
    }

    fun updateTabVisibility(visible: Boolean) {
        binding.tabLayout.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * ViewPager2 adapter for swipe navigation between Pending and Completed tabs.
     */
    private inner class TasksPagerAdapter(activity: AppCompatActivity) : androidx.viewpager2.adapter.FragmentStateAdapter(activity) {
        private val filters = listOf(
            TaskViewModel.TaskFilter.PENDING,
            TaskViewModel.TaskFilter.COMPLETED
        )

        override fun getItemCount(): Int = filters.size

        override fun createFragment(position: Int): androidx.fragment.app.Fragment {
            return TaskListFragment.newInstance(filters[position])
        }
    }
}

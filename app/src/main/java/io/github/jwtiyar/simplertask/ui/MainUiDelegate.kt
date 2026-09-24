package io.github.jwtiyar.simplertask.ui

import android.view.View
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.scopes.ActivityScoped
import io.github.jwtiyar.simplertask.MainActivity
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.model.TaskAction
import io.github.jwtiyar.simplertask.databinding.ActivityMainBinding
import io.github.jwtiyar.simplertask.ui.adapters.TaskPagingAdapter
import io.github.jwtiyar.simplertask.ui.dialogs.TaskDialogManager
import io.github.jwtiyar.simplertask.ui.fragments.TaskListFragment
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Delegate responsible for MainActivity UI setup and management.
 * Handles ViewPager, buttons, search UI, and basic UI interactions.
 */
@ActivityScoped
class MainUiDelegate @Inject constructor(
    private val dialogManager: TaskDialogManager
) {
    private lateinit var activity: MainActivity
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: TaskViewModel

    lateinit var searchAdapter: TaskPagingAdapter
        private set

    fun attach(activity: MainActivity, binding: ActivityMainBinding) {
        this.activity = activity
        this.binding = binding
        this.viewModel = ViewModelProvider(activity)[TaskViewModel::class.java]
        
        // Initialize searchAdapter after the activity and ViewModel are available.
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

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                activity.invalidateOptionsMenu()
            }
        })

        activity.lifecycleScope.launch {
            viewModel.pendingCount.collect { count ->
                val tab = binding.tabLayout.getTabAt(0)
                if (count > 0) {
                    val badge = tab?.orCreateBadge
                    badge?.isVisible = true
                    badge?.number = count
                } else {
                    tab?.badge?.isVisible = false
                }
            }
        }

        activity.lifecycleScope.launch {
            viewModel.completedCount.collect { count ->
                val tab = binding.tabLayout.getTabAt(1)
                if (count > 0) {
                    val badge = tab?.orCreateBadge
                    badge?.isVisible = true
                    badge?.number = count
                } else {
                    tab?.badge?.isVisible = false
                }
            }
        }
    }

    fun setupButtons() {
        binding.fabAddTask.setOnClickListener { showAddTaskDialog() }
    }

    fun confirmClearCompleted() {
        val completedCount = viewModel.completedCount.value
        if (completedCount == 0) {
            viewModel.postToast(activity.getString(R.string.no_completed_tasks))
            return
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setMessage(activity.getString(R.string.confirm_clear_completed, completedCount))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clear_completed) { _, _ -> viewModel.clearCompletedTasks() }
            .show()
    }

    fun confirmResetTasks() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setMessage(R.string.confirm_reset_tasks)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.reset_tasks) { _, _ -> viewModel.resetAllTasks() }
            .show()
    }

    fun setupSearchBar() {
        binding.searchBar.setOnClickListener { binding.searchView.show() }
        
        binding.searchRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        binding.searchRecyclerView.adapter = searchAdapter

        binding.searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty().trim()
                updateSearchStatus(query, androidx.paging.LoadState.Loading)
                viewModel.searchTasks(query)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.searchStatus.setOnClickListener { searchAdapter.retry() }

        updateSearchStatus(
            binding.searchView.editText.text?.toString().orEmpty(),
            androidx.paging.LoadState.Loading
        )
        activity.lifecycleScope.launch {
            searchAdapter.loadStateFlow.collect { states ->
                val query = binding.searchView.editText.text?.toString().orEmpty().trim()
                updateSearchStatus(query, states.refresh)
            }
        }
    }

    private fun updateSearchStatus(query: String, refresh: androidx.paging.LoadState) {
        val message = when {
            query.isEmpty() -> R.string.search_prompt
            refresh is androidx.paging.LoadState.Loading -> R.string.search_loading
            refresh is androidx.paging.LoadState.Error -> R.string.search_retry
            searchAdapter.itemCount == 0 -> R.string.search_no_results
            else -> null
        }
        binding.searchStatus.visibility = if (message == null) View.GONE else View.VISIBLE
        if (message != null) binding.searchStatus.setText(message)
        binding.searchStatus.isClickable = refresh is androidx.paging.LoadState.Error
        binding.searchStatus.isFocusable = binding.searchStatus.isClickable
    }

    private fun createSearchAdapter(): TaskPagingAdapter {
        return TaskPagingAdapter(
            onTaskClick = { task ->
                activity.lifecycleScope.launch {
                    viewModel.toggleTaskCompletion(task)
                }
            },
            onEditClick = { task -> showEditTaskDialog(task) },
            onTaskAction = { task, action ->
                if (action == TaskAction.DELETE) {
                    activity.lifecycleScope.launch {
                        if (viewModel.deleteTaskForUndo(task)) {
                            viewModel.postSnackbar(
                                activity.getString(R.string.task_deleted, task.title),
                                activity.getString(R.string.undo),
                                action = { viewModel.insertTask(task) }
                            )
                        }
                    }
                } else {
                    val updated = when (action) {
                    TaskAction.SAVE -> task.copy(isSaved = true)
                    TaskAction.UNSAVE -> task.copy(isSaved = false)
                    TaskAction.ARCHIVE -> task.copy(isArchived = true, isSaved = false)
                    TaskAction.UNARCHIVE -> task.copy(isArchived = false)
                    TaskAction.DELETE -> task
                    }
                    viewModel.updateTask(updated)
                    val msgRes = when (action) {
                    TaskAction.SAVE -> R.string.task_saved
                    TaskAction.UNSAVE -> R.string.task_unsaved
                    TaskAction.ARCHIVE -> R.string.task_archived
                    TaskAction.UNARCHIVE -> R.string.task_unarchived
                    TaskAction.DELETE -> R.string.delete
                    }
                    viewModel.postToast(activity.getString(msgRes))
                }
            },
            categories = { viewModel.categories.value }
        )
    }

    private fun showEditTaskDialog(task: Task) {
        activity.lifecycleScope.launch {
            val categories = viewModel.getCategoriesForEditor()
            dialogManager.showEditTaskDialog(task, categories) { updatedTask ->
                val saved = viewModel.saveTask(updatedTask)
                if (saved && updatedTask.dueDateMillis != null) requestReminderPermissions()
                saved
            }
        }
    }

    fun restoreDraft(savedState: Bundle?) {
        val draft = dialogManager.savedDraft(savedState) ?: return
        activity.lifecycleScope.launch {
            val categories = viewModel.getCategoriesForEditor()
            val id = draft.getInt("taskId")
            if (id == 0) {
                dialogManager.showAddTaskDialog(categories, draft) { task ->
                    val saved = viewModel.createTask(task)
                    if (saved && task.dueDateMillis != null) requestReminderPermissions()
                    saved
                }
            } else {
                val task = viewModel.getTaskForEditor(id) ?: return@launch
                dialogManager.showEditTaskDialog(task, categories, draft) { updated ->
                    val saved = viewModel.saveTask(updated)
                    if (saved && updated.dueDateMillis != null) requestReminderPermissions()
                    saved
                }
            }
        }
    }

    private fun requestReminderPermissions() {
        activity.requestReminderPermissions()
    }

    private fun showAddTaskDialog() {
        activity.lifecycleScope.launch {
            val categories = viewModel.getCategoriesForEditor()
            dialogManager.showAddTaskDialog(categories) { task ->
                val saved = viewModel.createTask(task)
                if (saved && task.dueDateMillis != null) requestReminderPermissions()
                saved
            }
        }
    }

    fun updateUI(title: String) {
        binding.topAppBar.title = title
    }

    fun updateTabVisibility(visible: Boolean) {
        binding.tabLayout.visibility = if (visible) View.VISIBLE else View.GONE
        binding.viewPager.isUserInputEnabled = visible
        activity.invalidateOptionsMenu()
    }

    fun isCompletedView(): Boolean =
        binding.tabLayout.visibility == View.VISIBLE && binding.viewPager.currentItem == 1

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

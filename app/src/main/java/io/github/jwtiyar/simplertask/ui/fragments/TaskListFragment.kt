package io.github.jwtiyar.simplertask.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.model.TaskAction
import io.github.jwtiyar.simplertask.databinding.FragmentTaskListBinding
import io.github.jwtiyar.simplertask.service.NotificationHelper
import io.github.jwtiyar.simplertask.ui.adapters.TaskPagingAdapter
import io.github.jwtiyar.simplertask.ui.adapters.TaskSwipeCallback
import io.github.jwtiyar.simplertask.ui.dialogs.TaskDialogManager
import io.github.jwtiyar.simplertask.utils.setupVertical
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import io.github.jwtiyar.simplertask.ui.UiEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Fragment displaying a list of tasks based on the filter type passed as argument.
 * Used by ViewPager2 to enable swipe navigation between Pending and Completed tabs.
 */
@AndroidEntryPoint
class TaskListFragment : Fragment() {

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!

    private val taskViewModel: TaskViewModel by activityViewModels()
    private lateinit var taskAdapter: TaskPagingAdapter
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var dialogManager: TaskDialogManager

    private var filterType: TaskViewModel.TaskFilter = TaskViewModel.TaskFilter.PENDING

    companion object {
        private const val ARG_FILTER_TYPE = "filter_type"

        fun newInstance(filter: TaskViewModel.TaskFilter): TaskListFragment {
            return TaskListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILTER_TYPE, filter.name)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ARG_FILTER_TYPE)?.let { filterName ->
            filterType = TaskViewModel.TaskFilter.valueOf(filterName)
        }
        notificationHelper = NotificationHelper(requireContext())
        dialogManager = TaskDialogManager(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskPagingAdapter(
            onTaskClick = { task ->
                taskViewModel.updateTask(task)
                val message = if (task.isCompleted) {
                    notificationHelper.cancelNotification(task)
                    getString(R.string.task_completed, task.title)
                } else {
                    if (task.dueDateMillis != null) notificationHelper.scheduleNotification(task)
                    getString(R.string.task_pending, task.title)
                }
                taskViewModel.postToast(message)
            },
            onEditClick = { task ->
                dialogManager.showEditTaskDialog(task) { updatedTask ->
                    // Cancel old notification before updating
                    notificationHelper.cancelNotification(task)

                    // Update the task in database
                    taskViewModel.updateTask(updatedTask)

                    // Reschedule notification if the task has a due date and is not completed
                    NotificationHelper.scheduleOrToggle(notificationHelper, updatedTask)
                }
            },
            onTaskAction = { task, action ->
                val updated = when (action) {
                    TaskAction.SAVE -> task.copy(isSaved = true)
                    TaskAction.UNSAVE -> task.copy(isSaved = false)
                    TaskAction.ARCHIVE -> task.copy(isArchived = true, isSaved = false)
                    TaskAction.UNARCHIVE -> task.copy(isArchived = false)
                }
                taskViewModel.updateTask(updated)
                val msgRes = when (action) {
                    TaskAction.SAVE -> R.string.task_saved
                    TaskAction.UNSAVE -> R.string.task_unsaved
                    TaskAction.ARCHIVE -> R.string.task_archived
                    TaskAction.UNARCHIVE -> R.string.task_unarchived
                }
                taskViewModel.postToast(getString(msgRes))
            },
            onSwipeComplete = { task, position ->
                // Handle swipe to complete
                taskViewModel.toggleTaskCompletion(task)
                taskViewModel.postToast(getString(R.string.task_completed, task.title))
            },
            onSwipeDelete = { task, position ->
                // Handle swipe to delete with confirmation
                showDeleteConfirmationDialog(task, position)
            }
        )

        binding.recyclerView.setupVertical(taskAdapter)

        // Setup swipe actions
        setupSwipeActions()

        // SwipeRefresh setup
        binding.swipeRefresh.setColorSchemeResources(
            R.color.primary,
            R.color.secondary,
            R.color.tertiary
        )

        var refreshStartTime = 0L
        val minShowTimeMs = 900L

        binding.swipeRefresh.setOnRefreshListener {
            refreshStartTime = System.currentTimeMillis()
            taskAdapter.refresh()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            taskAdapter.loadStateFlow.collect { loadStates: CombinedLoadStates ->
                val isLoading = loadStates.refresh is LoadState.Loading
                if (isLoading) {
                    if (!binding.swipeRefresh.isRefreshing) {
                        refreshStartTime = System.currentTimeMillis()
                        binding.swipeRefresh.isRefreshing = true
                    }
                } else {
                    if (binding.swipeRefresh.isRefreshing) {
                        val elapsed = System.currentTimeMillis() - refreshStartTime
                        val remaining = minShowTimeMs - elapsed
                        if (remaining > 0) {
                            binding.swipeRefresh.postDelayed({
                                binding.swipeRefresh.isRefreshing = false
                            }, remaining)
                        } else {
                            binding.swipeRefresh.isRefreshing = false
                        }
                    }
                }

                val errorState = loadStates.refresh as? LoadState.Error
                    ?: loadStates.append as? LoadState.Error
                    ?: loadStates.prepend as? LoadState.Error
                errorState?.let {
                    taskViewModel.postSnackbar(it.error.message ?: getString(R.string.error_generic))
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    taskViewModel.uiState.map { it.currentFilter }.distinctUntilChanged().collectLatest { globalFilter ->
                        // If global filter is a drawer-only item, everyone shows it.
                        // If global filter is PENDING/COMPLETED, we stay in tabbed mode.
                        val effectiveFilter = when (globalFilter) {
                            TaskViewModel.TaskFilter.SAVED,
                            TaskViewModel.TaskFilter.ARCHIVED,
                            TaskViewModel.TaskFilter.RECURRING,
                            TaskViewModel.TaskFilter.CATEGORY,
                            TaskViewModel.TaskFilter.ALL -> globalFilter
                            else -> filterType
                        }

                        taskViewModel.getPagedTasks(effectiveFilter).collectLatest { pagingData ->
                            taskAdapter.submitData(pagingData)
                        }
                    }
                }

                // Observe adapter load state to show/hide empty state
                launch {
                    taskAdapter.loadStateFlow.collectLatest { loadState ->
                        val isEmpty = loadState.refresh is androidx.paging.LoadState.NotLoading &&
                                    taskAdapter.itemCount == 0
                        binding.emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
                        binding.swipeRefresh.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    }
                }

                launch {
                    taskViewModel.events.collect { event ->
                        when (event) {
                            is UiEvent.RefreshList -> {
                                // taskViewModel.postToast("Refreshed") // Debug only if needed
                                taskAdapter.refresh()
                            }
                            else -> {} // Handled in Activity
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Fragment resumed - paging will handle data automatically
    }

    private fun setupSwipeActions() {
        val swipeCallback = TaskSwipeCallback(
            context = requireContext(),
            onSwipeComplete = { task, position ->
                taskViewModel.toggleTaskCompletion(task)
                taskViewModel.postToast(getString(R.string.task_completed, task.title))
            },
            onSwipeDelete = { task, position ->
                showDeleteConfirmationDialog(task, position)
            }
        )

        val itemTouchHelper = ItemTouchHelper(swipeCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    private fun showDeleteConfirmationDialog(task: Task, position: Int) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_task_title, task.title))
            .setMessage(R.string.delete_task_confirmation)
            .setPositiveButton(R.string.delete) { _, _ ->
                taskViewModel.deleteTask(task)
                taskViewModel.postToast(getString(R.string.task_deleted, task.title))
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                taskAdapter.notifyItemChanged(position)
            }
            .setOnCancelListener {
                taskAdapter.notifyItemChanged(position)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

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
import io.github.jwtiyar.simplertask.MainActivity
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.github.jwtiyar.simplertask.data.model.TaskAction
import io.github.jwtiyar.simplertask.databinding.FragmentTaskListBinding
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
import javax.inject.Inject

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
    private var swipeCallback: TaskSwipeCallback? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    
    @Inject
    lateinit var dialogManager: TaskDialogManager

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
        dialogManager.attach(requireActivity())
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
                viewLifecycleOwner.lifecycleScope.launch {
                    taskViewModel.toggleTaskCompletion(task)
                }
            },
            onEditClick = { task ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val categories = taskViewModel.getCategoriesForEditor()
                    dialogManager.showEditTaskDialog(task, categories) { updatedTask ->
                        val saved = taskViewModel.saveTask(updatedTask)
                        if (saved && updatedTask.dueDateMillis != null) {
                            (activity as? MainActivity)?.requestReminderPermissions()
                        }
                        saved
                    }
                }
            },
            onTaskAction = { task, action ->
                if (action == TaskAction.DELETE) {
                    deleteTaskWithUndo(task)
                } else {
                    val updated = when (action) {
                        TaskAction.SAVE -> task.copy(isSaved = true)
                        TaskAction.UNSAVE -> task.copy(isSaved = false)
                        TaskAction.ARCHIVE -> task.copy(isArchived = true, isSaved = false)
                        TaskAction.UNARCHIVE -> task.copy(isArchived = false)
                        TaskAction.DELETE -> task
                    }
                    taskViewModel.updateTask(updated)
                    val msgRes = when (action) {
                        TaskAction.SAVE -> R.string.task_saved
                        TaskAction.UNSAVE -> R.string.task_unsaved
                        TaskAction.ARCHIVE -> R.string.task_archived
                        TaskAction.UNARCHIVE -> R.string.task_unarchived
                        TaskAction.DELETE -> R.string.task_deleted
                    }
                    taskViewModel.postToast(getString(msgRes))
                }
            },
            categories = { taskViewModel.categories.value }
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

        binding.swipeRefresh.setOnRefreshListener {
            taskAdapter.refresh()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            taskAdapter.loadStateFlow.collect { loadStates: CombinedLoadStates ->
                val currentBinding = _binding ?: return@collect
                val isLoading = loadStates.refresh is LoadState.Loading
                if (isLoading) {
                    currentBinding.swipeRefresh.isRefreshing = true
                } else {
                    currentBinding.swipeRefresh.isRefreshing = false
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
                    taskViewModel.uiState.map { it.currentFilter to it.sortBy }.distinctUntilChanged().collectLatest { (globalFilter, _) ->
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

                        // Force ItemTouchHelper to refresh movement flags by re-attaching
                        itemTouchHelper?.attachToRecyclerView(null)
                        itemTouchHelper?.attachToRecyclerView(binding.recyclerView)

                        taskViewModel.getPagedTasks(effectiveFilter).collectLatest { pagingData ->
                            taskAdapter.submitData(pagingData)
                        }
                    }
                }

                // Observe adapter load state to show/hide empty state
                launch {
                    taskAdapter.loadStateFlow.collectLatest { loadState ->
                        val currentBinding = _binding ?: return@collectLatest
                        val isEmpty = loadState.refresh is androidx.paging.LoadState.NotLoading &&
                                    taskAdapter.itemCount == 0
                        currentBinding.emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
                        currentBinding.swipeRefresh.visibility = if (isEmpty) View.GONE else View.VISIBLE
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
        val callback = TaskSwipeCallback(
            context = requireContext(),
            filterProvider = {
                val globalFilter = taskViewModel.uiState.value.currentFilter
                when (globalFilter) {
                    TaskViewModel.TaskFilter.SAVED,
                    TaskViewModel.TaskFilter.ARCHIVED,
                    TaskViewModel.TaskFilter.RECURRING,
                    TaskViewModel.TaskFilter.CATEGORY,
                    TaskViewModel.TaskFilter.ALL -> globalFilter
                    else -> filterType
                }
            },
            onSwipeComplete = { task, position ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val wasCompleted = task.isCompleted
                    val nextTaskId = taskViewModel.toggleTaskCompletion(task)
                    
                    val message = if (!wasCompleted) 
                        getString(R.string.task_completed, task.title) 
                    else 
                        getString(R.string.task_pending, task.title)

                    taskViewModel.postSnackbar(
                        message = message,
                        actionLabel = getString(R.string.undo),
                        action = {
                            if (wasCompleted) taskViewModel.setTaskCompletion(task, true)
                            else taskViewModel.undoCompletion(task, nextTaskId)
                        }
                    )
                }
            },
            onSwipeDelete = { task, position ->
                deleteTaskWithUndo(task, position)
            }
        )
        this.swipeCallback = callback
        val helper = ItemTouchHelper(callback)
        this.itemTouchHelper = helper
        helper.attachToRecyclerView(binding.recyclerView)
    }

    private fun deleteTaskWithUndo(task: Task, position: Int? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (taskViewModel.deleteTaskForUndo(task)) {
                taskViewModel.postSnackbar(
                    message = getString(R.string.task_deleted, task.title),
                    actionLabel = getString(R.string.undo),
                    action = { taskViewModel.insertTask(task) }
                )
            } else position?.let(taskAdapter::notifyItemChanged)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

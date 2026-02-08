package io.github.jwtiyar.simplertask.ui

import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import com.google.android.material.search.SearchView
import io.github.jwtiyar.simplertask.MainActivity
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.databinding.ActivityMainBinding
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Delegate responsible for search functionality in MainActivity.
 * Handles paginated search results and search UI state.
 */
class SearchDelegate(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val viewModel: TaskViewModel,
    private val uiDelegate: MainUiDelegate
) {

    private val searchAdapter = uiDelegate.searchAdapter

    fun observeSearchResults() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe paginated search results
                launch {
                    viewModel.pagedSearchResults.collectLatest { pagingData: PagingData<io.github.jwtiyar.simplertask.data.local.entity.Task> ->
                        searchAdapter.submitData(pagingData)
                    }
                }

                // Handle search view state changes
                launch {
                    searchAdapter.loadStateFlow.collectLatest { loadStates ->
                        val isLoading = loadStates.refresh is LoadState.Loading
                        val error = loadStates.refresh as? LoadState.Error
                            ?: loadStates.append as? LoadState.Error
                            ?: loadStates.prepend as? LoadState.Error

                        // Show error if search fails
                        error?.let {
                            viewModel.postSnackbar(it.error.message ?: activity.getString(R.string.error_generic))
                        }
                    }
                }
            }
        }

        // Handle search view transitions
        binding.searchView.addTransitionListener { _, _, newState ->
            if (newState == SearchView.TransitionState.HIDDEN) {
                binding.searchView.editText.setText("")
                viewModel.searchTasks("")
                // Clear search results - will be handled by empty search query
            }
        }
    }
}
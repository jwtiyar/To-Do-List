package io.github.jwtiyar.simplertask.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.scopes.ActivityScoped
import io.github.jwtiyar.simplertask.MainActivity
import io.github.jwtiyar.simplertask.databinding.ActivityMainBinding
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Delegate responsible for search functionality in MainActivity.
 * Handles observation of search results and search UI state.
 */
@ActivityScoped
class SearchDelegate @Inject constructor(
    private val uiDelegate: MainUiDelegate
) {
    private lateinit var activity: MainActivity
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: TaskViewModel

    fun attach(activity: MainActivity, binding: ActivityMainBinding) {
        this.activity = activity
        this.binding = binding
        this.viewModel = ViewModelProvider(activity)[TaskViewModel::class.java]
    }

    fun observeSearchResults() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pagedSearchResults.collectLatest { pagingData ->
                    uiDelegate.searchAdapter.submitData(pagingData)
                }
            }
        }
    }
}
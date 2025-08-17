package io.github.jwtiyar.simplertask

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.paging.CombinedLoadStates
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import io.github.jwtiyar.simplertask.databinding.ActivityMainBinding
import io.github.jwtiyar.simplertask.dialogs.LanguageSelectionDialog
import io.github.jwtiyar.simplertask.TaskAction
import io.github.jwtiyar.simplertask.utils.LocaleManager
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import io.github.jwtiyar.simplertask.ui.UiEvent
import io.github.jwtiyar.simplertask.backup.BackupManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.search.SearchView
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import io.github.jwtiyar.simplertask.utils.requestPermissionCompat
import io.github.jwtiyar.simplertask.utils.setupVertical
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.content.res.Configuration

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    // Legacy list adapter kept for search results; main list now uses paging
    private lateinit var taskAdapter: TaskPagingAdapter
    private lateinit var searchAdapter: TaskAdapter
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var dialogManager: TaskDialogManager
    private lateinit var localeManager: LocaleManager
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var backupManager: BackupManager

    private var currentTaskFilter: TaskViewModel.TaskFilter = TaskViewModel.TaskFilter.PENDING

    // Activity result launchers for file operations
    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportBackupToUri(it) }
    }
    
    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importBackupFromUri(it) }
    }

    companion object {
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
        private const val MENU_ITEM_LANGUAGE = 1002
    }

    override fun attachBaseContext(newBase: Context) {
        val localeManager = LocaleManager(newBase)
        val context = localeManager.setAppLocale()
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply locale BEFORE calling super.onCreate() to ensure it's applied correctly
        localeManager = LocaleManager(applicationContext)
        localeManager.setAppLocale()
    super.onCreate(savedInstanceState)
    // Enable modern edge-to-edge rendering
    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)
        
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    setSupportActionBar(binding.topAppBar)

    notificationHelper = NotificationHelper(this)
    dialogManager = TaskDialogManager(this)
    backupManager = BackupManager(this)

    // Manual DI wiring (could be moved to a dedicated provider later)
    val database = TaskDatabase.getDatabase(this)
    val repository = io.github.jwtiyar.simplertask.repository.TaskRepository(database.taskDao())
    val factory = io.github.jwtiyar.simplertask.viewmodel.TaskViewModelFactory(repository)
    taskViewModel = ViewModelProvider(this, factory)[TaskViewModel::class.java]

    // Apply window insets to top app bar and scrolling content so they don't overlap system bars
    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val density = resources.displayMetrics.density
    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Slightly lower in landscape (closer to bottom) but not as drastic
    val baseFabBottomMarginDp = if (isLandscape) 96 else 112
        val baseFabBottomMarginPx = (baseFabBottomMarginDp * density).toInt()
        val fabDefaultHeightPx = (56 * density).toInt()
    val gapPx = (if (isLandscape) 16 else 24 * density).toInt() // modest gap in landscape

        // Top inset padding applied to AppBar parent, keep toolbar padding zero
        (binding.topAppBar.parent as? View)?.setPadding(0, systemBars.top, 0, 0)
        binding.topAppBar.setPadding(0, 0, 0, 0)

        val fabLp = (binding.fabAddTask.layoutParams as? android.view.ViewGroup.MarginLayoutParams)
        val fabHeight = binding.fabAddTask.height.takeIf { it > 0 } ?: binding.fabAddTask.measuredHeight.takeIf { it > 0 } ?: fabDefaultHeightPx

        // Set FAB bottom margin = system bars + base design margin so it sits higher above bottom buttons
        fabLp?.let { lp ->
            lp.bottomMargin = systemBars.bottom + baseFabBottomMarginPx
            binding.fabAddTask.layoutParams = lp
        }

        val requiredContentBottom = systemBars.bottom + baseFabBottomMarginPx + fabHeight + gapPx
        // Apply to scroll content child and recycler ensuring not to shrink existing padding
        (binding.nestedScrollView.getChildAt(0))?.let { child ->
            val current = child.paddingBottom
            if (current < requiredContentBottom) child.updatePadding(bottom = requiredContentBottom)
        }
        val currentRvPad = binding.recyclerView.paddingBottom
        if (currentRvPad < requiredContentBottom) binding.recyclerView.updatePadding(bottom = requiredContentBottom)

        insets
    }

    // Re-apply after first layout pass to capture real FAB height
    binding.fabAddTask.post { binding.root.requestApplyInsets() }

    observeViewModel()
    checkAndRequestPostNotificationPermission()
    checkAndRequestExactAlarmPermission()
    setupRecyclerView()
    setupTabLayout()
    setupButtons()
    setupSearchBar()
    setupNavigationDrawer()
    setupBackPressHandler()
    
    // Load initial tasks
    loadTasks()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe ViewModel data
                launch {
                    taskViewModel.pagedTasks.collectLatest { pagingData ->
                        taskAdapter.submitData(pagingData)
                    }
                }
                // search results
                launch {
                    taskViewModel.searchResults.collect { results ->
                        searchAdapter.updateTasks(results)
                    }
                }
                // one-off events (toasts / snackbars)
                launch {
                    taskViewModel.events.collect { event ->
                        when (event) {
                            is UiEvent.ShowToast -> {
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            }
                            is UiEvent.ShowSnackbar -> {
                                val sb = Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                                event.actionLabel?.let { label ->
                                    sb.setAction(label) { /* action placeholder */ }
                                }
                                sb.show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupSearchBar() {
        binding.searchBar.setOnClickListener { binding.searchView.show() }
        setupSearchView()
    }

    private fun setupSearchView() {
        searchAdapter = TaskAdapter(
            emptyList(),
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
            onEditClick = { task -> showEditTaskDialog(task) },
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
            }
        )
        binding.searchRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchRecyclerView.adapter = searchAdapter

        binding.searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                taskViewModel.searchTasks(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.searchView.addTransitionListener { _, _, newState ->
            if (newState == SearchView.TransitionState.HIDDEN) {
                binding.searchView.editText.setText("")
                taskViewModel.searchTasks("")
            }
        }
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val newFilter = when (tab?.position) {
                    0 -> TaskViewModel.TaskFilter.PENDING
                    1 -> TaskViewModel.TaskFilter.COMPLETED
                    else -> TaskViewModel.TaskFilter.PENDING
                }
                if (newFilter != currentTaskFilter) {
                    currentTaskFilter = newFilter
                    loadTasks()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadTasks() { 
        taskViewModel.loadTasks(currentTaskFilter) 
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
                showEditTaskDialog(task)
                binding.searchView.hide()
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
            }
        )
        
        // Setup main list RecyclerView with vertical layout & footer
        val concatAdapter = taskAdapter.withLoadStateFooter(TaskLoadStateAdapter { taskAdapter.retry() })
        
        binding.recyclerView.setupVertical(concatAdapter)
        
        // Add adapter observer
        concatAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                // Adapter data changed
            }
            
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                // Items inserted
            }
        })
        binding.recyclerView.setOnCreateContextMenuListener(null)
        binding.recyclerView.isLongClickable = false

        // Defensive: also disable long press context menu on parent scroll container
        binding.nestedScrollView.isLongClickable = false
        (binding.nestedScrollView.getChildAt(0) as? android.widget.LinearLayout)?.apply {
            setOnCreateContextMenuListener(null)
            isLongClickable = false
        }

        // Configure SwipeRefreshLayout colors (use prominent brand colors for visibility)
        binding.swipeRefresh.setColorSchemeResources(
            R.color.primary,
            R.color.secondary,
            R.color.tertiary
        )

        // Track refresh start to enforce a minimum spinner visibility for perceived smoothness
        var refreshStartTime = 0L
        val MIN_SHOW_TIME_MS = 900L

        // Swipe-to-refresh triggers refresh of paging source (record start time)
        binding.swipeRefresh.setOnRefreshListener {
            refreshStartTime = System.currentTimeMillis()
            taskAdapter.refresh()
        }

        // Observe load states to show/hide refresh indicator & surface errors
        lifecycleScope.launch {
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
                        val remaining = MIN_SHOW_TIME_MS - elapsed
                        if (remaining > 0) {
                            binding.swipeRefresh.postDelayed({ binding.swipeRefresh.isRefreshing = false }, remaining)
                        } else {
                            binding.swipeRefresh.isRefreshing = false
                        }
                    }
                }

                val errorState = loadStates.refresh as? LoadState.Error
                    ?: loadStates.append as? LoadState.Error
                    ?: loadStates.prepend as? LoadState.Error
                errorState?.let { taskViewModel.postSnackbar(it.error.message ?: getString(R.string.error_generic)) }
            }
        }
    }

    // Simple LoadStateAdapter for footer progress & retry
    private inner class TaskLoadStateAdapter(private val onRetry: () -> Unit) : LoadStateAdapter<LoadStateViewHolder>() {
        override fun onBindViewHolder(holder: LoadStateViewHolder, loadState: LoadState) = holder.bind(loadState)
        override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): LoadStateViewHolder {
            val progress = android.widget.ProgressBar(parent.context).apply { isIndeterminate = true }
            val retryButton = com.google.android.material.button.MaterialButton(parent.context).apply {
                text = parent.context.getString(R.string.retry)
                setOnClickListener { onRetry() }
                visibility = View.GONE
            }
            val container = android.widget.LinearLayout(parent.context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(24,24,24,24)
                addView(progress, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(retryButton, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            return LoadStateViewHolder(container, progress, retryButton)
        }
    }

    private class LoadStateViewHolder(view: View, private val progress: android.widget.ProgressBar, private val retryBtn: com.google.android.material.button.MaterialButton) : RecyclerView.ViewHolder(view) {
        fun bind(loadState: LoadState) {
            when (loadState) {
                is LoadState.Loading -> { progress.visibility = View.VISIBLE; retryBtn.visibility = View.GONE }
                is LoadState.Error -> { progress.visibility = View.GONE; retryBtn.visibility = View.VISIBLE }
                is LoadState.NotLoading -> { progress.visibility = View.GONE; retryBtn.visibility = View.GONE }
            }
        }
    }

    private fun showEditTaskDialog(task: Task) { dialogManager.showEditTaskDialog(task) { taskViewModel.updateTask(it) } }

    private fun setupButtons() {
        binding.fabAddTask.setOnClickListener { showAddTaskDialog() }
        binding.btnClearCompleted.setOnClickListener {
            val state = taskViewModel.uiState.value
            if (currentTaskFilter == TaskViewModel.TaskFilter.COMPLETED && state.tasks.any { it.isCompleted }) {
                state.tasks.filter { it.isCompleted }.forEach { notificationHelper.cancelNotification(it) }
                taskViewModel.clearCompletedTasks()
                taskViewModel.postToast(getString(R.string.removed_completed_tasks, state.tasks.count { it.isCompleted }))
            } else if (currentTaskFilter == TaskViewModel.TaskFilter.PENDING) {
                taskViewModel.postToast(getString(R.string.switch_to_completed_tab))
            } else {
                taskViewModel.postToast(getString(R.string.no_completed_tasks))
            }
        }
        binding.btnResetTasks.setOnClickListener {
            try {
                taskViewModel.resetAllTasks()
                taskViewModel.postToast(getString(R.string.all_tasks_reset))
            } catch (e: Exception) {
                Snackbar.make(binding.root, getString(R.string.error_resetting_tasks, e.message), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showAddTaskDialog() { 
        dialogManager.showAddTaskDialog { task -> 
            taskViewModel.addTask(task.title, task.description, task.priority, task.dueDateMillis)
            
            // Schedule notification if reminder is set
            if (task.dueDateMillis != null) {
                notificationHelper.scheduleNotification(task)
            }
            
            // Enhanced refresh and scroll to top for new task visibility
            lifecycleScope.launch {
                // Small delay to ensure DB operation completes
                kotlinx.coroutines.delay(300)
                
                // Refresh the adapter
                taskAdapter.refresh()
                
                // Scroll to top to show the new task
                binding.recyclerView.smoothScrollToPosition(0)
                
                // Additional fallback: if still not visible, force scroll after another delay
                kotlinx.coroutines.delay(500)
                binding.recyclerView.scrollToPosition(0)
            }
        } 
    }

    private fun checkAndRequestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionCompat(
                Manifest.permission.POST_NOTIFICATIONS,
                getString(R.string.notification_permission_needed),
                REQUEST_CODE_POST_NOTIFICATIONS
            )
        }
    }

    private fun checkAndRequestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.exact_alarm_permission_title))
                    .setMessage(getString(R.string.exact_alarm_permission_message))
                    .setPositiveButton(getString(R.string.button_open_settings)) { _, _ ->
                        Intent().apply {
                            action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            data = Uri.fromParts("package", packageName, null)
                        }.also {
                            try {
                                startActivity(it)
                            } catch (_: Exception) {
                                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                            }
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                        Snackbar.make(binding.root, getString(R.string.exact_alarm_not_granted), Snackbar.LENGTH_LONG).show()
                    }
                    .show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(binding.root, getString(R.string.notification_permission_granted), Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, getString(R.string.notification_permission_denied), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.add(Menu.NONE, MENU_ITEM_LANGUAGE, Menu.NONE, R.string.language)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_theme -> { showThemeSelectionDialog(); true }
        R.id.action_about -> { showAboutDialog(); true }
        R.id.action_export_backup -> { startExportBackup(); true }
        R.id.action_import_backup -> { startImportBackup(); true }
        MENU_ITEM_LANGUAGE -> { showLanguageSelectionDialog(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showLanguageSelectionDialog() { LanguageSelectionDialog(this) { taskViewModel.postToast(getString(R.string.language_changed)) }.show() }

    private fun showAboutDialog() { val v = layoutInflater.inflate(R.layout.dialog_about, null); AlertDialog.Builder(this).setView(v).setTitle(getString(R.string.dialog_about_title)).setPositiveButton(getString(R.string.button_ok), null).show() }

    private fun showThemeSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_theme_selection, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupTheme)
        val radioLight = dialogView.findViewById<RadioButton>(R.id.radioLight)
        val radioDark = dialogView.findViewById<RadioButton>(R.id.radioDark)
        val radioSystem = dialogView.findViewById<RadioButton>(R.id.radioSystem)

        // Set current selection
        when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> radioLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> radioDark.isChecked = true
            else -> radioSystem.isChecked = true
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle(getString(R.string.dialog_theme_title))
            .setPositiveButton(getString(R.string.button_ok)) { _, _ ->
                when (radioGroup.checkedRadioButtonId) {
                    R.id.radioLight -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        taskViewModel.postToast(getString(R.string.theme_light))
                    }
                    R.id.radioDark -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        taskViewModel.postToast(getString(R.string.theme_dark))
                    }
                    R.id.radioSystem -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        taskViewModel.postToast(getString(R.string.theme_system_default))
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupNavigationDrawer() {
        val drawerLayout = binding.drawerLayout
        val navigationView = binding.navigationView
        binding.topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        navigationView.setCheckedItem(R.id.nav_all_tasks)
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_all_tasks -> { currentTaskFilter = TaskViewModel.TaskFilter.PENDING; binding.tabLayout.getTabAt(0)?.select(); loadTasks(); updateUI(getString(R.string.nav_all_tasks)); navigationView.setCheckedItem(R.id.nav_all_tasks) }
                R.id.nav_saved_tasks -> { currentTaskFilter = TaskViewModel.TaskFilter.SAVED; loadTasks(); updateUI(getString(R.string.nav_saved_tasks)); navigationView.setCheckedItem(R.id.nav_saved_tasks) }
                R.id.nav_archive -> { currentTaskFilter = TaskViewModel.TaskFilter.ARCHIVED; loadTasks(); updateUI(getString(R.string.nav_archive)); navigationView.setCheckedItem(R.id.nav_archive) }
            }
            drawerLayout.closeDrawer(GravityCompat.START); true
        }
    }

    private fun updateUI(title: String) {
        binding.topAppBar.title = title
        binding.tabLayout.visibility = when (currentTaskFilter) {
            TaskViewModel.TaskFilter.PENDING, TaskViewModel.TaskFilter.COMPLETED -> View.VISIBLE
            else -> View.GONE
        }
    }

    private fun setupBackPressHandler() { onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { val drawer = binding.drawerLayout; if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START) else finish() } }) }
    
    // Backup and Restore Methods
    
    private fun startExportBackup() {
        val filename = backupManager.generateBackupFilename()
        exportBackupLauncher.launch(filename)
    }
    
    private fun startImportBackup() {
        importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
    }
    
    private fun exportBackupToUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val tasks = taskViewModel.getAllTasksForBackup()
                val backupJson = backupManager.exportTasks(tasks)
                backupManager.writeToUri(uri, backupJson)
                
                taskViewModel.postToast("Backup exported successfully (${tasks.size} tasks)")
            } catch (e: Exception) {
                taskViewModel.postSnackbar("Failed to export backup: ${e.message}")
            }
        }
    }
    
    private fun importBackupFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val backupContent = backupManager.readFromUri(uri)
                val metadata = backupManager.getBackupMetadata(backupContent)
                
                if (metadata == null) {
                    taskViewModel.postSnackbar("Invalid backup file format")
                    return@launch
                }
                
                showImportConfirmationDialog(backupContent, metadata)
                
            } catch (e: Exception) {
                taskViewModel.postSnackbar("Failed to read backup file: ${e.message}")
            }
        }
    }
    
    private fun showImportConfirmationDialog(backupContent: String, metadata: BackupManager.BackupMetadata) {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val createdAtString = if (metadata.createdAt > 0) {
            dateFormat.format(java.util.Date(metadata.createdAt))
        } else {
            "Unknown"
        }
        
        val message = """
            Backup Information:
            • Created: $createdAtString
            • Tasks: ${metadata.taskCount}
            • Version: ${metadata.version}
        """.trimIndent()
        
        val items = arrayOf(
            "Add to existing tasks (keep current tasks)", 
            "Replace all tasks (delete current tasks)"
        )
        var selectedOption = 0
        
        AlertDialog.Builder(this)
            .setTitle("Import Backup")
            .setMessage(message)
            .setSingleChoiceItems(items, 0) { _, which ->
                selectedOption = which
            }
            .setPositiveButton("Import") { _, _ ->
                performImport(backupContent, replaceExisting = selectedOption == 1)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performImport(backupContent: String, replaceExisting: Boolean) {
        lifecycleScope.launch {
            try {
                val tasks = backupManager.importTasks(backupContent)
                taskViewModel.importTasksFromBackup(tasks, replaceExisting)
            } catch (e: Exception) {
                taskViewModel.postSnackbar("Failed to import tasks: ${e.message}")
            }
        }
    }
}

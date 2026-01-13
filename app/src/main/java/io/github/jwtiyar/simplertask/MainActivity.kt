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
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import android.view.ViewGroup
import dagger.hilt.android.AndroidEntryPoint
import io.github.jwtiyar.simplertask.databinding.ActivityMainBinding
import io.github.jwtiyar.simplertask.data.model.TaskAction
import io.github.jwtiyar.simplertask.utils.LocaleManager
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import io.github.jwtiyar.simplertask.ui.UiEvent
import io.github.jwtiyar.simplertask.data.backup.BackupManager
import io.github.jwtiyar.simplertask.ui.adapters.TaskAdapter
import io.github.jwtiyar.simplertask.ui.fragments.TaskListFragment
import io.github.jwtiyar.simplertask.service.NotificationHelper
import io.github.jwtiyar.simplertask.ui.dialogs.TaskDialogManager
import io.github.jwtiyar.simplertask.data.local.entity.Task
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.search.SearchView
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import io.github.jwtiyar.simplertask.utils.requestPermissionCompat
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.github.jwtiyar.simplertask.utils.PermissionManager
import io.github.jwtiyar.simplertask.ui.MainActivityBackupDelegate
import android.content.res.Configuration

@AndroidEntryPoint
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var searchAdapter: TaskAdapter
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var dialogManager: TaskDialogManager
    private lateinit var localeManager: LocaleManager
    private val taskViewModel: TaskViewModel by viewModels()
    private lateinit var backupManager: BackupManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var backupDelegate: MainActivityBackupDelegate

    private var currentTaskFilter: TaskViewModel.TaskFilter = TaskViewModel.TaskFilter.PENDING

    // Activity result launchers for file operations
    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { backupDelegate.exportBackupToUri(it) }
    }
    
    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { backupDelegate.importBackupFromUri(it) }
    }

    companion object {
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    }

    override fun attachBaseContext(newBase: Context) {
        val localeManager = LocaleManager(newBase)
        val context = localeManager.setAppLocale()
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        localeManager = LocaleManager(applicationContext)
        localeManager.setAppLocale()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        notificationHelper = NotificationHelper(this)
        dialogManager = TaskDialogManager(this)
        backupManager = BackupManager(this)
        permissionManager = PermissionManager(this)
        backupDelegate = MainActivityBackupDelegate(this, taskViewModel, backupManager)

        // Apply window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val baseFabBottomMarginDp = if (isLandscape) 96 else 112
            val baseFabBottomMarginPx = (baseFabBottomMarginDp * density).toInt()

            (binding.topAppBar.parent as? View)?.setPadding(0, systemBars.top, 0, 0)
            binding.topAppBar.setPadding(0, 0, 0, 0)

            val fabLp = (binding.fabAddTask.layoutParams as? ViewGroup.MarginLayoutParams)
            fabLp?.let { lp ->
                lp.bottomMargin = systemBars.bottom + baseFabBottomMarginPx
                binding.fabAddTask.layoutParams = lp
            }
            insets
        }

        binding.fabAddTask.post { binding.root.requestApplyInsets() }

        observeViewModel()
        permissionManager.checkAndRequestPostNotificationPermission(REQUEST_CODE_POST_NOTIFICATIONS)
        permissionManager.checkAndRequestExactAlarmPermission(binding.root)
        setupViewPager()
        setupButtons()
        setupSearchBar()
        setupNavigationDrawer()
        setupBackPressHandler()
    }

    /**
     * ViewPager2 adapter for swipe navigation between Pending and Completed tabs.
     */
    private inner class TasksPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        private val filters = listOf(
            TaskViewModel.TaskFilter.PENDING,
            TaskViewModel.TaskFilter.COMPLETED
        )

        override fun getItemCount(): Int = filters.size

        override fun createFragment(position: Int): Fragment {
            return TaskListFragment.newInstance(filters[position])
        }
    }

    private fun setupViewPager() {
        val pagerAdapter = TasksPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        // Link TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = getString(R.string.tab_pending)
                    tab.setIcon(R.drawable.ic_pending_24dp)
                }
                1 -> {
                    tab.text = getString(R.string.tab_completed)
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

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                            is UiEvent.RefreshList -> {} // Handled by Fragment
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

    private fun showEditTaskDialog(task: Task) { 
        dialogManager.showEditTaskDialog(task) { taskViewModel.updateTask(it) } 
    }

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
            
            if (task.dueDateMillis != null) {
                notificationHelper.scheduleNotification(task)
            }
        } 
    }

    private fun startExportBackup() {
        val filename = backupManager.generateBackupFilename()
        exportBackupLauncher.launch(filename)
    }

    private fun startImportBackup() {
        importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
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
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_theme -> { showThemeSelectionDialog(); true }
        R.id.action_about -> { showAboutDialog(); true }
        R.id.action_export_backup -> { startExportBackup(); true }
        R.id.action_import_backup -> { startImportBackup(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showAboutDialog() { 
        val v = layoutInflater.inflate(R.layout.dialog_about, null)
        AlertDialog.Builder(this).setView(v).setTitle(getString(R.string.dialog_about_title)).setPositiveButton(getString(R.string.button_ok), null).show() 
    }

    private fun showThemeSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_theme_selection, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupTheme)
        val radioLight = dialogView.findViewById<RadioButton>(R.id.radioLight)
        val radioDark = dialogView.findViewById<RadioButton>(R.id.radioDark)
        val radioSystem = dialogView.findViewById<RadioButton>(R.id.radioSystem)

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
                R.id.nav_all_tasks -> { 
                    currentTaskFilter = TaskViewModel.TaskFilter.PENDING
                    binding.viewPager.currentItem = 0
                    binding.tabLayout.visibility = View.VISIBLE
                    updateUI(getString(R.string.nav_all_tasks))
                    navigationView.setCheckedItem(R.id.nav_all_tasks) 
                }
                R.id.nav_saved_tasks -> { 
                    currentTaskFilter = TaskViewModel.TaskFilter.SAVED
                    taskViewModel.loadTasks(TaskViewModel.TaskFilter.SAVED)
                    binding.tabLayout.visibility = View.GONE
                    updateUI(getString(R.string.nav_saved_tasks))
                    navigationView.setCheckedItem(R.id.nav_saved_tasks) 
                }
                R.id.nav_archive -> { 
                    currentTaskFilter = TaskViewModel.TaskFilter.ARCHIVED
                    taskViewModel.loadTasks(TaskViewModel.TaskFilter.ARCHIVED)
                    binding.tabLayout.visibility = View.GONE
                    updateUI(getString(R.string.nav_archive))
                    navigationView.setCheckedItem(R.id.nav_archive) 
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun updateUI(title: String) {
        binding.topAppBar.title = title
    }

    private fun setupBackPressHandler() { 
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { 
            override fun handleOnBackPressed() { 
                val drawer = binding.drawerLayout
                if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START) else finish() 
            } 
        }) 
    }
}

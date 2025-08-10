package com.example.simplertask

import androidx.appcompat.app.AppCompatDelegate
import android.view.Menu
import android.view.MenuItem
import android.widget.RadioGroup
import android.widget.RadioButton
// Removed broken import line
import androidx.core.view.GravityCompat
import androidx.activity.OnBackPressedCallback

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.provider.Settings
import android.view.View
import android.widget.Toast
import com.example.simplertask.dialogs.LanguageSelectionDialog
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.simplertask.databinding.ActivityMainBinding
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import com.google.android.material.button.MaterialButton
import com.example.simplertask.utils.LocaleManager
import java.util.Locale
import com.google.android.material.search.SearchView
import android.widget.EditText
import android.widget.TextView
import com.example.simplertask.viewmodel.TaskViewModel
import com.example.simplertask.viewmodel.TaskViewModelFactory
import com.example.simplertask.TaskRepositoryImpl
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var searchAdapter: TaskAdapter
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var dialogManager: TaskDialogManager
    private lateinit var localeManager: LocaleManager
    private lateinit var taskViewModel: TaskViewModel
    private var tasks: List<Task> = emptyList()
    private var loadTasksJob: Job? = null

    private var currentTaskFilter: TaskViewModel.TaskFilter = TaskViewModel.TaskFilter.PENDING
    // To store current filter

    companion object {
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
        private const val MENU_ITEM_LANGUAGE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localeManager = LocaleManager(applicationContext)
        localeManager.setAppLocale()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        notificationHelper = NotificationHelper(this)
        dialogManager = TaskDialogManager(this)
        val repository = TaskRepositoryImpl(applicationContext)
        val factory = TaskViewModelFactory(repository)
        taskViewModel = ViewModelProvider(this, factory).get(TaskViewModel::class.java)

        // Observe tasks from ViewModel
        lifecycleScope.launch {
            taskViewModel.uiState.collect { uiState ->
                tasks = uiState.tasks
                taskAdapter.updateTasks(tasks)
            }
        }

        checkAndRequestPostNotificationPermission()
        checkAndRequestExactAlarmPermission()

        setupRecyclerView()
        setupTabLayout()
        setupButtons()
        setupSearchBar()
        setupNavigationDrawer()
        setupBackPressHandler()
        // Optionally show theme dialog on first launch or via menu
    }

    private fun setupSearchBar() {
        // Connect SearchBar to SearchView
        binding.searchBar.setOnClickListener {
            binding.searchView.show()
        }

        // Setup SearchView
        setupSearchView()
    }

    private fun setupSearchView() {
        // Initialize search adapter for the search results
        searchAdapter = TaskAdapter(
            emptyList(),
            onTaskClick = { task ->
                // Use ViewModel for updating task
                taskViewModel.updateTask(task)
                lifecycleScope.launch(Dispatchers.Main) {
                    val message = if (task.isCompleted) {
                        notificationHelper.cancelNotification(task)
                        "Task completed: ${task.title}"
                    } else {
                        if (task.dueDateMillis != null) {
                            notificationHelper.scheduleNotification(task)
                        }
                        "Task marked as pending: ${task.title}"
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    // Update search results after task update
                    val currentQuery = binding.searchView.editText.text.toString()
                    if (currentQuery.isNotEmpty()) {
                        performSearch(currentQuery, searchAdapter)
                    }
                }
            },
            onEditClick = { task ->
                showEditTaskDialog(task)
                binding.searchView.hide()
            },
            onTaskAction = { task, action ->
                val updatedTask = when (action) {
                    "save" -> task.copy(isSaved = true)
                    "unsave" -> task.copy(isSaved = false)
                    "archive" -> task.copy(isArchived = true, isSaved = false)
                    "unarchive" -> task.copy(isArchived = false)
                    else -> task
                }
                taskViewModel.updateTask(updatedTask)
                lifecycleScope.launch(Dispatchers.Main) {
                    val message = when (action) {
                        "save" -> "Task saved"
                        "unsave" -> "Task removed from saved"
                        "archive" -> "Task archived"
                        "unarchive" -> "Task unarchived"
                        else -> ""
                    }
                    if (message.isNotEmpty()) {
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                    // Update search results after action
                    val currentQuery = binding.searchView.editText.text.toString()
                    if (currentQuery.isNotEmpty()) {
                        performSearch(currentQuery, searchAdapter)
                    }
                }
            }
        )

        // Setup search RecyclerView
        binding.searchRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchRecyclerView.adapter = searchAdapter

        // Setup search text listener
        binding.searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                performSearch(query, searchAdapter)
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Handle search view hide/show events
        binding.searchView.addTransitionListener { searchView, previousState, newState ->
            if (newState == SearchView.TransitionState.HIDDEN) {
                // Clear search when closing
                binding.searchView.editText.setText("")
                // Reload main tasks list to refresh any changes
                loadTasksFromDb()
            }
        }
    }

    private fun performSearch(query: String, searchAdapter: TaskAdapter) {
        if (query.isEmpty()) {
            searchAdapter.updateTasks(emptyList())
            return
        }
        // Use ViewModel to filter tasks
        lifecycleScope.launch {
            val allTasks = taskViewModel.uiState.value.tasks
            val filteredTasks = allTasks.filter { task ->
                task.title.contains(query, ignoreCase = true) ||
                        task.description.contains(query, ignoreCase = true)
            }
            searchAdapter.updateTasks(filteredTasks)
            if (filteredTasks.isEmpty() && query.isNotEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    "No tasks found for \"$query\"",
                    Toast.LENGTH_SHORT
                ).show()
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
                    loadTasksFromDb()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }


    private fun loadTasksFromDb() {
        taskViewModel.loadTasks(currentTaskFilter)
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            emptyList(),
            onTaskClick = { task ->
                // Use ViewModel for updating task
                taskViewModel.updateTask(task)
                lifecycleScope.launch(Dispatchers.Main) {
                    val message = if (task.isCompleted) {
                        notificationHelper.cancelNotification(task)
                        "Task completed: ${task.title}"
                    } else {
                        if (task.dueDateMillis != null) {
                            notificationHelper.scheduleNotification(task)
                        }
                        "Task marked as pending: ${task.title}"
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            },
            onEditClick = { task ->
                showEditTaskDialog(task)
            },
            onTaskAction = { task, action ->
                val updatedTask = when (action) {
                    "save" -> task.copy(isSaved = true)
                    "unsave" -> task.copy(isSaved = false)
                    "archive" -> task.copy(isArchived = true, isSaved = false)
                    "unarchive" -> task.copy(isArchived = false)
                    else -> task
                }
                taskViewModel.updateTask(updatedTask)
                lifecycleScope.launch(Dispatchers.Main) {
                    val message = when (action) {
                        "save" -> "Task saved"
                        "unsave" -> "Task removed from saved"
                        "archive" -> "Task archived"
                        "unarchive" -> "Task unarchived"
                        else -> ""
                    }
                    if (message.isNotEmpty()) {
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = taskAdapter
    }

    private fun showEditTaskDialog(task: Task) {
        dialogManager.showEditTaskDialog(task) { updatedTask ->
            taskViewModel.updateTask(updatedTask)
        }
    }

    private fun setupButtons() {
        binding.fabAddTask.setOnClickListener { showAddTaskDialog() }
        binding.btnClearCompleted.setOnClickListener {
            lifecycleScope.launch {
                if (currentTaskFilter == TaskViewModel.TaskFilter.COMPLETED && tasks.any { it.isCompleted }) {
                    tasks.filter { it.isCompleted }.forEach { notificationHelper.cancelNotification(it) }
                    taskViewModel.clearCompletedTasks()
                    loadTasksFromDb()
                    Toast.makeText(this@MainActivity, getString(R.string.removed_completed_tasks, tasks.count { it.isCompleted }), Toast.LENGTH_SHORT).show()
                } else if (currentTaskFilter == TaskViewModel.TaskFilter.PENDING) {
                    Toast.makeText(this@MainActivity, getString(R.string.switch_to_completed_tab), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.no_completed_tasks), Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnResetTasks.setOnClickListener {
            lifecycleScope.launch {
                try {
                    taskViewModel.resetAllTasks()
                    loadTasksFromDb()
                    Toast.makeText(this@MainActivity, getString(R.string.all_tasks_reset), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error resetting tasks", e)
                    Toast.makeText(this@MainActivity, getString(R.string.error_resetting_tasks, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAddTaskDialog() {
        dialogManager.showAddTaskDialog { newTask ->
            taskViewModel.addTask(newTask.title, newTask.description, newTask.priority, newTask.dueDateMillis)
        }
    }

    private fun checkAndRequestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is already granted
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.notification_permission_needed),
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(getString(R.string.button_grant)) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_CODE_POST_NOTIFICATIONS
                    )
                }.show()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_POST_NOTIFICATIONS
                )
            }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_theme -> {
                showThemeSelectionDialog()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            MENU_ITEM_LANGUAGE -> {
                showLanguageSelectionDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLanguageSelectionDialog() {
        LanguageSelectionDialog(this) {
            // The language setting is handled internally by LanguageSelectionDialog
            // which calls localeManager.setNewLocale() and activity.recreate()

            // Show a message that the language has been changed
            Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle(getString(R.string.dialog_about_title))
            .setPositiveButton(getString(R.string.button_ok), null)
            .show()
    }

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
                        Toast.makeText(this, getString(R.string.theme_light), Toast.LENGTH_SHORT).show()
                    }
                    R.id.radioDark -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        Toast.makeText(this, getString(R.string.theme_dark), Toast.LENGTH_SHORT).show()
                    }
                    R.id.radioSystem -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        Toast.makeText(this, getString(R.string.theme_system_default), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupNavigationDrawer() {
        val drawerLayout = binding.drawerLayout
        val navigationView = binding.navigationView

        // Set up the hamburger menu button to open the drawer
        binding.topAppBar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Set default checked item
        navigationView.setCheckedItem(R.id.nav_all_tasks)

        // Handle navigation menu item clicks
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_all_tasks -> {
                    currentTaskFilter = TaskViewModel.TaskFilter.PENDING
                    binding.tabLayout.getTabAt(0)?.select()
                    loadTasksFromDb()
                    updateUI(getString(R.string.nav_all_tasks))
                    navigationView.setCheckedItem(R.id.nav_all_tasks)
                }
                R.id.nav_saved_tasks -> {
                    currentTaskFilter = TaskViewModel.TaskFilter.SAVED
                    loadTasksFromDb()
                    updateUI(getString(R.string.nav_saved_tasks))
                    navigationView.setCheckedItem(R.id.nav_saved_tasks)
                }
                R.id.nav_archive -> {
                    currentTaskFilter = TaskViewModel.TaskFilter.ARCHIVED
                    loadTasksFromDb()
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
        when (currentTaskFilter) {
            TaskViewModel.TaskFilter.PENDING, TaskViewModel.TaskFilter.COMPLETED -> binding.tabLayout.visibility = View.VISIBLE
            else -> binding.tabLayout.visibility = View.GONE
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val drawerLayout = binding.drawerLayout
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })
    }
}

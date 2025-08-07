package com.example.simplertask

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
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.simplertask.databinding.ActivityMainBinding
import com.example.simplertask.dialogs.LanguageSelectionDialog
import com.example.simplertask.repository.TaskRepository
import com.example.simplertask.utils.LocaleManager
import com.example.simplertask.viewmodel.TaskViewModel
import com.example.simplertask.viewmodel.TaskViewModelFactory
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Refactored MainActivity using MVVM architecture pattern
 */
class MainActivityRefactored : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var searchAdapter: TaskAdapter
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var localeManager: LocaleManager
    
    // ViewModel initialization using factory
    private val viewModel: TaskViewModel by viewModels {
        val database = TaskDatabase.getDatabase(this)
        val repository = TaskRepository(database.taskDao())
        TaskViewModelFactory(repository)
    }
    
    companion object {
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
        private const val MENU_ITEM_LANGUAGE = 1002
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize locale manager and apply saved language
        localeManager = LocaleManager(applicationContext)
        localeManager.setAppLocale()
        
        // Setup view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        
        // Initialize helpers
        notificationHelper = NotificationHelper(this)
        
        // Setup UI components
        setupRecyclerView()
        setupTabLayout()
        setupButtons()
        setupSearchBar()
        setupNavigationDrawer()
        setupBackPressHandler()
        
        // Observe ViewModel state
        observeViewModel()
        
        // Check permissions
        checkAndRequestPostNotificationPermission()
        checkAndRequestExactAlarmPermission()
    }
    
    /**
     * Setup RecyclerView with adapter
     */
    private fun setupRecyclerView() {
        // Initialize main task adapter
        taskAdapter = TaskAdapter(
            tasks = emptyList(),
            onTaskClick = { task ->
                viewModel.toggleTaskCompletion(task)
                if (task.isCompleted) {
                    notificationHelper.cancelNotification(task)
                } else if (task.dueDateMillis != null) {
                    notificationHelper.scheduleNotification(task)
                }
            },
            onEditClick = { task ->
                showEditTaskDialog(task)
            },
            onTaskAction = { task, action ->
                when (action) {
                    "save" -> viewModel.toggleTaskSaved(task)
                    "unsave" -> viewModel.toggleTaskSaved(task)
                    "archive" -> viewModel.toggleTaskArchived(task)
                    "unarchive" -> viewModel.toggleTaskArchived(task)
                }
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivityRefactored)
            adapter = taskAdapter
        }
    }
    
    /**
     * Setup tab layout for filtering tasks
     */
    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val filter = when (tab?.position) {
                    0 -> TaskViewModel.TaskFilter.PENDING
                    1 -> TaskViewModel.TaskFilter.COMPLETED
                    else -> TaskViewModel.TaskFilter.PENDING
                }
                viewModel.loadTasks(filter)
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    /**
     * Setup button click listeners
     */
    private fun setupButtons() {
        binding.fabAddTask.setOnClickListener {
            showAddTaskDialog()
        }
        
        binding.btnClearCompleted.setOnClickListener {
            val currentFilter = viewModel.uiState.value.currentFilter
            if (currentFilter == TaskViewModel.TaskFilter.COMPLETED) {
                showClearCompletedConfirmation()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.switch_to_completed_tab),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        binding.btnResetTasks.setOnClickListener {
            showResetAllConfirmation()
        }
    }
    
    /**
     * Setup search functionality
     */
    private fun setupSearchBar() {
        // Set up search bar behavior
        binding.searchBar.inflateMenu(R.menu.search_menu)
        
        // Handle search text changes using TextWatcher with EditText from the SearchBar
        val searchEditText = binding.searchBar.findViewById<EditText>(com.google.android.material.R.id.search_src_text)
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.searchTasks(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }
    
    /**
     * Setup navigation drawer
     */
    private fun setupNavigationDrawer() {
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_all_tasks -> {
                    viewModel.loadTasks(TaskViewModel.TaskFilter.ALL)
                    binding.tabLayout.visibility = View.GONE
                }
                R.id.nav_saved_tasks -> {
                    viewModel.loadTasks(TaskViewModel.TaskFilter.SAVED)
                    binding.tabLayout.visibility = View.GONE
                }
                R.id.nav_archive -> {
                    viewModel.loadTasks(TaskViewModel.TaskFilter.ARCHIVED)
                    binding.tabLayout.visibility = View.GONE
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }
    
    /**
     * Setup back press handler
     */
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    binding.searchView.isShowing -> {
                        binding.searchView.hide()
                    }
                    else -> {
                        finish()
                    }
                }
            }
        })
    }
    
    /**
     * Observe ViewModel state and update UI accordingly
     */
    private fun observeViewModel() {
        // Observe UI state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        // Show error messages
                        state.errorMessage?.let { error ->
                            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG)
                                .setAction("Dismiss") { viewModel.clearError() }
                                .show()
                        }
                        
                        // Update empty state
                        updateEmptyState(state.tasks.isEmpty())
                    }
                }
                
                launch {
                    viewModel.currentTasks.collectLatest { tasks ->
                        taskAdapter.updateTasks(tasks)
                    }
                }
                
                launch {
                    viewModel.searchResults.collectLatest { results ->
                        searchAdapter.updateTasks(results)
                        // Update search empty state if needed - for now just show/hide results
                    }
                }
            }
        }
    }
    
    /**
     * Update empty state visibility
     */
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    /**
     * Show dialog to add new task
     */
    private fun showAddTaskDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.editTextTitle)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.editTextDescription)
        val reminderCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.checkBoxSetReminder)
        val priorityGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroupPriority)
        
        var selectedDateTime: Long? = null
        
        reminderCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showDateTimePicker { dateTime ->
                    selectedDateTime = dateTime
                }
            } else {
                selectedDateTime = null
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_task_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                
                if (title.isNotEmpty()) {
                    val priority = when (priorityGroup.checkedRadioButtonId) {
                        R.id.radioHigh -> Priority.HIGH
                        R.id.radioLow -> Priority.LOW
                        else -> Priority.MEDIUM
                    }
                    
                    viewModel.addTask(title, description, priority, selectedDateTime)
                    
                    if (selectedDateTime != null) {
                        Toast.makeText(this, getString(R.string.task_added_with_reminder), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.task_added_successfully), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.enter_task_title), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    /**
     * Show dialog to edit existing task
     */
    private fun showEditTaskDialog(task: Task) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.editTextTitle)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.editTextDescription)
        val priorityGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroupPriority)
        
        // Pre-fill with existing values
        titleInput.setText(task.title)
        descriptionInput.setText(task.description)
        
        when (task.priority) {
            Priority.HIGH -> priorityGroup.check(R.id.radioHigh)
            Priority.LOW -> priorityGroup.check(R.id.radioLow)
            else -> priorityGroup.check(R.id.radioMedium)
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_task_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.button_save)) { _, _ ->
                val updatedTask = task.copy(
                    title = titleInput.text.toString().trim(),
                    description = descriptionInput.text.toString().trim(),
                    priority = when (priorityGroup.checkedRadioButtonId) {
                        R.id.radioHigh -> Priority.HIGH
                        R.id.radioLow -> Priority.LOW
                        else -> Priority.MEDIUM
                    }
                )
                viewModel.updateTask(updatedTask)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    /**
     * Show date and time picker
     */
    private fun showDateTimePicker(onDateTimeSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        
                        if (calendar.timeInMillis > System.currentTimeMillis()) {
                            onDateTimeSelected(calendar.timeInMillis)
                        } else {
                            Toast.makeText(this, getString(R.string.reminder_time_past), Toast.LENGTH_LONG).show()
                        }
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    /**
     * Show confirmation dialog for clearing completed tasks
     */
    private fun showClearCompletedConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_completed))
            .setMessage("Are you sure you want to delete all completed tasks?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.clearCompletedTasks()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    /**
     * Show confirmation dialog for resetting all tasks
     */
    private fun showResetAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.reset_all))
            .setMessage("Are you sure you want to reset all tasks to pending?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.resetAllTasks()
                Toast.makeText(this, getString(R.string.all_tasks_reset), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_settings -> {
                showThemeDialog()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * Show sort options dialog
     */
    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_by_date),
            getString(R.string.sort_by_name),
            getString(R.string.sort_by_priority)
        )
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.menu_sort))
            .setItems(options) { _, which ->
                val sortBy = when (which) {
                    0 -> TaskViewModel.SortBy.DATE
                    1 -> TaskViewModel.SortBy.NAME
                    2 -> TaskViewModel.SortBy.PRIORITY
                    else -> TaskViewModel.SortBy.DATE
                }
                viewModel.updateSortBy(sortBy)
            }
            .show()
    }
    
    /**
     * Show theme selection dialog
     */
    private fun showThemeDialog() {
        val options = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system_default)
        )
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_theme_title))
            .setItems(options) { _, which ->
                val mode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    2 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
            }
            .show()
    }
    
    /**
     * Show about dialog
     */
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_about_title))
            .setMessage("""
                ${getString(R.string.about_title)}
                
                ${getString(R.string.about_developer)}
                ${getString(R.string.about_email)}
                ${getString(R.string.about_github)}
            """.trimIndent())
            .setPositiveButton(getString(R.string.button_ok), null)
            .show()
    }
    
    /**
     * Check and request notification permission
     */
    private fun checkAndRequestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_POST_NOTIFICATIONS
                )
            }
        }
    }
    
    /**
     * Check and request exact alarm permission
     */
    private fun checkAndRequestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.exact_alarm_permission_title))
                    .setMessage(getString(R.string.exact_alarm_permission_message))
                    .setPositiveButton(getString(R.string.button_grant)) { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
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
        when (requestCode) {
            REQUEST_CODE_POST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, getString(R.string.notification_permission_granted), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.notification_permission_denied), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

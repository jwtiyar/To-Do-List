
package com.example.simplertask

import androidx.appcompat.app.AppCompatDelegate
import android.view.Menu
import android.view.MenuItem
import android.widget.RadioGroup
import android.widget.RadioButton
import com.google.android.material.navigation.NavigationView
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

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var db: TaskDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var localeManager: LocaleManager
    private var tasks: List<Task> = emptyList()
    private var loadTasksJob: Job? = null

    private var currentTaskFilter: TaskFilter = TaskFilter.PENDING // To store current filter

    enum class TaskFilter {
        PENDING,
        COMPLETED,
        SAVED,
        ARCHIVED
    }

    companion object {
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
        private const val MENU_ITEM_LANGUAGE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localeManager = LocaleManager(applicationContext)
        // Apply saved language at startup
        localeManager.setAppLocale()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        db = TaskDatabase.getDatabase(this)
        taskDao = db.taskDao()
        notificationHelper = NotificationHelper(this)

        // Initial load will use default PENDING filter
        loadTasksFromDb()

        checkAndRequestPostNotificationPermission()
        checkAndRequestExactAlarmPermission()

        setupRecyclerView()
        setupTabLayout() // Call setup for TabLayout
        setupButtons()
        setupSearchBar()
        setupNavigationDrawer()
        setupBackPressHandler()
        // loadTasksFromDb() // Initial load will use default PENDING filter (already called above)

        // animateCardGrowth(binding.bottomActionCard)

    // Optionally show theme dialog on first launch or via menu

    }

    private fun setupSearchBar() {
        // TODO: Implement search functionality with Material SearchBar
        // The Material SearchBar component requires a different implementation approach
        // than the traditional EditText-based search
    }


    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val newFilter = when (tab?.position) {
                    0 -> TaskFilter.PENDING
                    1 -> TaskFilter.COMPLETED
                    else -> TaskFilter.PENDING
                }
                
                // Only load if filter actually changed
                if (newFilter != currentTaskFilter) {
                    currentTaskFilter = newFilter
                    loadTasksFromDb()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        // Ensure the first tab is selected by default if needed (though addOnTabSelectedListener should trigger for the first tab initially)
        // binding.tabLayout.getTabAt(0)?.select()
    }


    private fun loadTasksFromDb() {
        // Cancel the previous job
        loadTasksJob?.cancel()
        loadTasksJob = lifecycleScope.launch {
            try {
                Log.d("TaskLoad", "Loading tasks for filter: $currentTaskFilter")
                
                val taskList = when (currentTaskFilter) {
                    TaskFilter.PENDING -> {
                        Log.d("TaskLoad", "Loading pending tasks (not completed, not archived)")
                        taskDao.getPendingTasks().first()
                    }
                    TaskFilter.COMPLETED -> {
                        Log.d("TaskLoad", "Loading completed tasks (completed, not archived)")
                        taskDao.getCompletedTasks().first()
                    }
                    TaskFilter.SAVED -> {
                        Log.d("TaskLoad", "Loading saved tasks (saved, not archived)")
                        taskDao.getSavedTasks().first()
                    }
                    TaskFilter.ARCHIVED -> {
                        Log.d("TaskLoad", "Loading archived tasks (archived)")
                        taskDao.getArchivedTasks().first()
                    }
                }
                
                Log.d("TaskLoad", "Loaded ${taskList.size} tasks for $currentTaskFilter")
                tasks = taskList
                
                withContext(Dispatchers.Main) {
                    taskAdapter.updateTasks(tasks)
                }
                
            } catch (e: Exception) {
                // Handle cancellation gracefully
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("MainActivity", "Error loading tasks", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Error loading tasks: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun updateTaskInDatabaseAndUI(
        updatedTask: Task,
        shouldRemovePredicate: () -> Boolean = { false }
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Update in database
                taskDao.updateTask(updatedTask)
                
                withContext(Dispatchers.Main) {
                    val currentList = taskAdapter.getTasks().toMutableList()
                    val index = currentList.indexOfFirst { it.id == updatedTask.id }
                    
                    // Check if the task should be in the current filter
                    val shouldBeInCurrentView = shouldShowInCurrentFilter(updatedTask)
                    val shouldRemove = shouldRemovePredicate()
                    
                    if (index != -1) {
                        if (shouldRemove || !shouldBeInCurrentView) {
                            // Remove the task from the current list
                            currentList.removeAt(index)
                            taskAdapter.updateTasks(currentList)
                        } else {
                            // Update the task in place
                            currentList[index] = updatedTask
                            taskAdapter.updateTasks(currentList)
                        }
                    } else if (shouldBeInCurrentView) {
                        // Task should be in this filter but isn't (e.g., unarchived or uncompleted task)
                        currentList.add(updatedTask)
                        taskAdapter.updateTasks(currentList)
                    }
                    
                    // Log for debugging
                    Log.d("TaskUpdate", "Updated task: ${updatedTask.id}, " +
                            "completed: ${updatedTask.isCompleted}, " +
                            "archived: ${updatedTask.isArchived}, " +
                            "filter: $currentTaskFilter, " +
                            "inView: $shouldBeInCurrentView, " +
                            "removed: ${shouldRemove || !shouldBeInCurrentView}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("TaskUpdate", "Error updating task", e)
                    Toast.makeText(
                        this@MainActivity, 
                        "Error updating task: ${e.message}", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun shouldShowInCurrentFilter(task: Task): Boolean {
        // First check if the task is archived - archived tasks only show in ARCHIVED filter
        if (task.isArchived) {
            val show = currentTaskFilter == TaskFilter.ARCHIVED
            Log.d("TaskFilter", "Task ${task.id} is archived - showing in $currentTaskFilter: $show")
            return show
        }
        
        // For non-archived tasks, check the current filter
        val matchesFilter = when (currentTaskFilter) {
            TaskFilter.PENDING -> !task.isCompleted
            TaskFilter.COMPLETED -> task.isCompleted
            TaskFilter.SAVED -> task.isSaved
            TaskFilter.ARCHIVED -> false // Should never reach here for archived tasks (handled above)
        }
        
        Log.d("TaskFilter", "Task ${task.id} in $currentTaskFilter: $matchesFilter " +
                "(completed: ${task.isCompleted}, archived: ${task.isArchived}, saved: ${task.isSaved})")
        return matchesFilter
    }
    
    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            tasks,
            onTaskClick = { task ->
                lifecycleScope.launch {
                    try {
                        // Use the task with updated completion status from the adapter
                        Log.d("TaskClick", "Updating task ${task.id} with completion=${task.isCompleted}")
                        
                        // Update the task in the database and UI
                        updateTaskInDatabaseAndUI(task) {
                            // Should remove if:
                            // 1. Task is completed and we're in PENDING view
                            // 2. Task is uncompleted and we're in COMPLETED view
                            // 3. Task is archived and we're not in ARCHIVED view
                            (currentTaskFilter == TaskFilter.PENDING && task.isCompleted) ||
                            (currentTaskFilter == TaskFilter.COMPLETED && !task.isCompleted) ||
                            (currentTaskFilter != TaskFilter.ARCHIVED && task.isArchived)
                        }
                        
                        // Show toast message for completion state change
                        withContext(Dispatchers.Main) {
                            val message = if (task.isCompleted) {
                                notificationHelper.cancelNotification(task)
                                "Task completed: ${task.title}"
                            } else {
                                if (task.dueDateMillis != null) {
                                    notificationHelper.scheduleNotification(task)
                                }
                                "Task marked as pending: ${task.title}"
                            }
                            Toast.makeText(
                                this@MainActivity, 
                                message, 
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e("TaskClick", "Error toggling task completion", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity, 
                                "Error updating task: ${e.message}", 
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            onEditClick = { task ->
                // TODO: Implement edit task dialog
                Toast.makeText(this@MainActivity, "Edit task: ${task.title}", Toast.LENGTH_SHORT).show()
            },
            onTaskAction = { task, action ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val updatedTask = when (action) {
                            "save" -> task.copy(isSaved = true)
                            "unsave" -> task.copy(isSaved = false)
                            "archive" -> task.copy(isArchived = true, isSaved = false) // Unsave when archiving
                            "unarchive" -> task.copy(isArchived = false)
                            else -> task
                        }
                        
                        Log.d("TaskAction", "Action: $action on task ${task.id}")
                        
                        // Update in database
                        taskDao.updateTask(updatedTask)
                        
                        // Determine if task should be removed from current view
                        val shouldRemove = when {
                            // Remove if archived and not in ARCHIVED view
                            updatedTask.isArchived && currentTaskFilter != TaskFilter.ARCHIVED -> true
                            // Remove if unsaved and in SAVED view
                            !updatedTask.isSaved && currentTaskFilter == TaskFilter.SAVED -> true
                            // Remove if unarchived but doesn't match current filter
                            action == "unarchive" && !shouldShowInCurrentFilter(updatedTask) -> true
                            // Default to not removing
                            else -> false
                        }
                        
                        // Update UI on main thread
                        withContext(Dispatchers.Main) {
                            // Get current list from adapter
                            val currentList = taskAdapter.getTasks().toMutableList()
                            val index = currentList.indexOfFirst { it.id == updatedTask.id }
                            
                            if (shouldRemove) {
                                // Remove from current list if exists
                                if (index != -1) {
                                    currentList.removeAt(index)
                                    taskAdapter.updateTasks(currentList)
                                }
                            } else if (index != -1) {
                                // Update in place if exists
                                currentList[index] = updatedTask
                                taskAdapter.updateTasks(currentList)
                            } else if (shouldShowInCurrentFilter(updatedTask)) {
                                // Add if it should be in current view but isn't
                                currentList.add(updatedTask)
                                taskAdapter.updateTasks(currentList)
                            }
                            
                            // Show appropriate toast message
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
                        
                        // Task action handling complete
                        
                    } catch (e: Exception) {
                        Log.e("TaskAction", "Error handling task action", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity, 
                                "Error: ${e.message}", 
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = taskAdapter
    }

    private fun showEditTaskDialog(task: Task) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.editTextTitle)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.editTextDescription)
        val reminderCheckBox = dialogView.findViewById<MaterialCheckBox>(R.id.checkBoxSetReminder)
        val reminderLayout = dialogView.findViewById<View>(R.id.reminderLayout)
        val dateButton = dialogView.findViewById<MaterialButton>(R.id.btnDatePicker)
        val timeButton = dialogView.findViewById<MaterialButton>(R.id.btnTimePicker)
        val radioGroupPriority = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroupPriority)

        titleInput.setText(task.title)
        descriptionInput.setText(task.description)
        when (task.priority) {
            Priority.LOW -> radioGroupPriority.check(R.id.radioLow)
            Priority.MEDIUM -> radioGroupPriority.check(R.id.radioMedium)
            Priority.HIGH -> radioGroupPriority.check(R.id.radioHigh)
        }

        // Reminder and due date
        var selectedDate: LocalDateTime? = null
        if (task.dueDateMillis != null) {
            selectedDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(task.dueDateMillis!!), ZoneId.systemDefault())
            reminderCheckBox.isChecked = true
            reminderLayout.visibility = View.VISIBLE
            dateButton.text = selectedDate.toLocalDate().toString()
            timeButton.text = selectedDate.toLocalTime().toString()
        } else {
            reminderCheckBox.isChecked = false
            reminderLayout.visibility = View.GONE
        }

        reminderCheckBox.setOnCheckedChangeListener { _, isChecked ->
            reminderLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) selectedDate = null
        }
        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val currentTime = LocalTime.now()
                    selectedDate = LocalDateTime.of(year, month + 1, dayOfMonth, currentTime.hour, currentTime.minute)
                    dateButton.text = getString(R.string.date_format_string, month + 1, dayOfMonth, year)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        timeButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    selectedDate = selectedDate?.withHour(hourOfDay)?.withMinute(minute)
                        ?: LocalDateTime.now().withHour(hourOfDay).withMinute(minute)
                    timeButton.text = String.format(Locale.getDefault(), getString(R.string.time_format_string), hourOfDay, minute)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_task_title)
            .setView(dialogView)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val newTitle = titleInput.text.toString().trim()
                val newDescription = descriptionInput.text.toString().trim()
                val newPriority = when (radioGroupPriority.checkedRadioButtonId) {
                    R.id.radioLow -> Priority.LOW
                    R.id.radioMedium -> Priority.MEDIUM
                    R.id.radioHigh -> Priority.HIGH
                    else -> Priority.MEDIUM
                }
                val scheduledMillis = if (reminderCheckBox.isChecked && selectedDate != null) {
                    selectedDate!!.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } else null
                val updatedTask = task.copy(
                    title = newTitle,
                    description = newDescription,
                    priority = newPriority,
                    dueDateMillis = scheduledMillis
                )
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { taskDao.updateTask(updatedTask) }
                    loadTasksFromDb()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun setupButtons() {
        binding.fabAddTask.setOnClickListener {
            showAddTaskDialog()
        }
        binding.btnClearCompleted.setOnClickListener {
            lifecycleScope.launch {
                // Only attempt to clear if on the completed tab and there are completed tasks
                if (currentTaskFilter == TaskFilter.COMPLETED && tasks.any { it.isCompleted }) {
                    val completedTasksToClear = tasks.filter { it.isCompleted }
                    completedTasksToClear.forEach { task ->
                        notificationHelper.cancelNotification(task)
                    }
                    withContext(Dispatchers.IO) { taskDao.deleteCompletedTasks() }
                    loadTasksFromDb() // Refresh the list
                    Toast.makeText(this@MainActivity, getString(R.string.removed_completed_tasks, completedTasksToClear.size), Toast.LENGTH_SHORT).show()
                } else if (currentTaskFilter == TaskFilter.PENDING) {
                    Toast.makeText(this@MainActivity, getString(R.string.switch_to_completed_tab), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.no_completed_tasks), Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnResetTasks.setOnClickListener {
            lifecycleScope.launch {
                try {
                    // Collect all tasks from the Flow
                    taskDao.getAllTasks().collect { taskList ->
                        // Process each task in the list
                        taskList.forEach { task ->
                            if (task.isCompleted || task.dueDateMillis != null) {
                                // Create an updated task with isCompleted = false
                                val updatedTask = task.copy(isCompleted = false)
                                // Update in database
                                withContext(Dispatchers.IO) {
                                    taskDao.updateTask(updatedTask)
                                    // Re-schedule notification if needed
                                    if (updatedTask.dueDateMillis != null) {
                                        notificationHelper.scheduleNotification(updatedTask)
                                    }
                                }
                            }
                        }
                        // Refresh the current view
                        loadTasksFromDb()
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.all_tasks_reset),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error resetting tasks", e)
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.error_resetting_tasks, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showAddTaskDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.editTextTitle)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.editTextDescription)
        val reminderCheckBox = dialogView.findViewById<MaterialCheckBox>(R.id.checkBoxSetReminder)
        val reminderLayout = dialogView.findViewById<View>(R.id.reminderLayout)
        val dateButton = dialogView.findViewById<MaterialButton>(R.id.btnDatePicker)
        val timeButton = dialogView.findViewById<MaterialButton>(R.id.btnTimePicker)
        val radioGroupPriority = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroupPriority)

        var selectedDate: LocalDateTime? = null

        reminderCheckBox.setOnCheckedChangeListener { _, isChecked ->
            reminderLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) selectedDate = null
        }
        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val currentTime = LocalTime.now()
                    selectedDate = LocalDateTime.of(year, month + 1, dayOfMonth, currentTime.hour, currentTime.minute)
                    dateButton.text = getString(R.string.date_format_string, month + 1, dayOfMonth, year)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        timeButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    selectedDate = selectedDate?.withHour(hourOfDay)?.withMinute(minute)
                        ?: LocalDateTime.now().withHour(hourOfDay).withMinute(minute)
                    timeButton.text = String.format(Locale.getDefault(), getString(R.string.time_format_string), hourOfDay, minute)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_task_title)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                val priority = when (radioGroupPriority.checkedRadioButtonId) {
                    R.id.radioLow -> Priority.LOW
                    R.id.radioMedium -> Priority.MEDIUM
                    R.id.radioHigh -> Priority.HIGH
                    else -> Priority.MEDIUM
                }
                if (title.isNotEmpty()) {
                    val scheduledMillis = if (reminderCheckBox.isChecked && selectedDate != null) {
                        selectedDate!!.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } else null
                    if (scheduledMillis != null && scheduledMillis < System.currentTimeMillis()) {
                        Toast.makeText(this, getString(R.string.reminder_time_past), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val newTask = Task(
                        title = title,
                        description = description,
                        dueDateMillis = scheduledMillis,
                        isCompleted = false,
                        priority = priority
                    )
                    lifecycleScope.launch {
                        val newTaskId = withContext(Dispatchers.IO) { taskDao.insertTask(newTask) }
                        // Always reload tasks after insert to ensure UI is up-to-date
                        // Automatically select the Pending tab so new tasks are visible
                        binding.tabLayout.getTabAt(0)?.select()
                        loadTasksFromDb()
                        if (scheduledMillis != null && newTaskId > 0) {
                            val insertedTask = withContext(Dispatchers.IO) { taskDao.getTaskById(newTaskId) }
                            insertedTask?.let {
                                notificationHelper.scheduleNotification(it)
                                Toast.makeText(this@MainActivity, getString(R.string.task_added_with_reminder), Toast.LENGTH_SHORT).show()
                            }
                        } else if (newTaskId > 0) {
                            Toast.makeText(this@MainActivity, getString(R.string.task_added_successfully), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, getString(R.string.enter_task_title), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
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
                    currentTaskFilter = TaskFilter.PENDING
                    binding.tabLayout.getTabAt(0)?.select()
                    loadTasksFromDb()
                    updateUI(getString(R.string.nav_all_tasks))
                    navigationView.setCheckedItem(R.id.nav_all_tasks)
                }
                R.id.nav_saved_tasks -> {
                    currentTaskFilter = TaskFilter.SAVED
                    loadTasksFromDb()
                    updateUI(getString(R.string.nav_saved_tasks))
                    navigationView.setCheckedItem(R.id.nav_saved_tasks)
                }
                R.id.nav_archive -> {
                    currentTaskFilter = TaskFilter.ARCHIVED
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
        
        // Hide/show tabs based on the current filter
        when (currentTaskFilter) {
            TaskFilter.PENDING, TaskFilter.COMPLETED -> {
                binding.tabLayout.visibility = View.VISIBLE
            }
            TaskFilter.SAVED, TaskFilter.ARCHIVED -> {
                binding.tabLayout.visibility = View.GONE
            }
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

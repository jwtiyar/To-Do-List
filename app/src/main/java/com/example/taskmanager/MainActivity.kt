package com.example.taskmanager

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
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taskmanager.databinding.ActivityMainBinding
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Calendar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var db: TaskDatabase
    private lateinit var taskDao: TaskDao
    private val tasks = mutableListOf<Task>()

    companion object {
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = TaskDatabase.getDatabase(this)
        taskDao = db.taskDao()
        notificationHelper = NotificationHelper(this)

        checkAndRequestPostNotificationPermission()
        checkAndRequestExactAlarmPermission()

        setupRecyclerView()
        setupButtons()
        loadTasksFromDb()
    }

    private fun loadTasksFromDb() {
        lifecycleScope.launch {
            val dbTasks = withContext(Dispatchers.IO) { taskDao.getAllTasks() }
            tasks.clear()
            tasks.addAll(dbTasks)
            taskAdapter.notifyDataSetChanged()
        }
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(tasks) { task ->
            // Handle task completion
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { taskDao.updateTask(task) }
            }
            if (task.isCompleted) {
                Toast.makeText(this, "Task completed: ${task.title}", Toast.LENGTH_SHORT).show()
                notificationHelper.cancelNotification(task)
            }
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = taskAdapter
        }
    }

    private fun setupButtons() {
        binding.btnAddTask.setOnClickListener {
            showAddTaskDialog()
        }
        binding.btnClearCompleted.setOnClickListener {
            lifecycleScope.launch {
                val completedCount = tasks.count { it.isCompleted }
                if (completedCount > 0) {
                    tasks.filter { it.isCompleted }.forEach { task ->
                        notificationHelper.cancelNotification(task)
                    }
                    withContext(Dispatchers.IO) { taskDao.deleteCompletedTasks() }
                    loadTasksFromDb()
                    Toast.makeText(this@MainActivity, "Removed $completedCount completed tasks", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "No completed tasks to remove", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnResetTasks.setOnClickListener {
            lifecycleScope.launch {
                tasks.forEach { task ->
                    task.isCompleted = false
                    if (task.scheduledTimeMillis != null) {
                        notificationHelper.scheduleNotification(task)
                    }
                    withContext(Dispatchers.IO) { taskDao.updateTask(task) }
                }
                loadTasksFromDb()
                Toast.makeText(this@MainActivity, "All tasks reset", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddTaskDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.editTextTitle)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.editTextDescription)
        val reminderCheckBox = dialogView.findViewById<MaterialCheckBox>(R.id.checkBoxSetReminder)
        val reminderLayout = dialogView.findViewById<View>(R.id.reminderLayout)
        val dateButton = dialogView.findViewById<View>(R.id.btnDatePicker)
        val timeButton = dialogView.findViewById<View>(R.id.btnTimePicker)

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
                    dateButton.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDatePicker).text =
                        "${month + 1}/${dayOfMonth}/${year}"
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
                    timeButton.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTimePicker).text =
                        String.format("%02d:%02d", hourOfDay, minute)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add New Task")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                if (title.isNotEmpty()) {
                    val scheduledMillis = if (reminderCheckBox.isChecked && selectedDate != null) {
                        selectedDate!!.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } else null
                    val newTask = Task(
                        title = title,
                        description = description,
                        scheduledTimeMillis = scheduledMillis
                    )
                    lifecycleScope.launch {
                        val newTaskId = withContext(Dispatchers.IO) { taskDao.insertTask(newTask) }
                        loadTasksFromDb() // Refresh the list in the UI
                        if (scheduledMillis != null && newTaskId > 0) {
                            // Re-fetch task to get the complete object with the generated ID
                            val insertedTask = withContext(Dispatchers.IO) { taskDao.getTaskById(newTaskId) }
                            insertedTask?.let {
                                notificationHelper.scheduleNotification(it)
                                Toast.makeText(this@MainActivity, "Task added with reminder", Toast.LENGTH_SHORT).show()
                            } ?: run {
                                Toast.makeText(this@MainActivity, "Error scheduling reminder for task", Toast.LENGTH_SHORT).show()
                            }
                        } else if (newTaskId > 0) {
                            Toast.makeText(this@MainActivity, "Task added successfully", Toast.LENGTH_SHORT).show()
                        } else {
                             Toast.makeText(this@MainActivity, "Error adding task to database", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Please enter a task title", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
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
                    binding.root, // Using root view of the binding
                    "This app needs permission to post notifications for task reminders.",
                    Snackbar.LENGTH_INDEFINITE
                ).setAction("Grant") {
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
                    .setTitle("Exact Alarm Permission Needed")
                    .setMessage("To ensure timely task reminders, this app needs permission to schedule exact alarms. Please grant this permission in the app settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        Intent().apply {
                            action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            data = Uri.fromParts("package", packageName, null)
                        }.also {
                            try {
                                startActivity(it)
                            } catch (e: Exception) {
                                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                            }
                        }
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        Snackbar.make(binding.root, "Exact alarm permission not granted. Reminders may be less precise.", Snackbar.LENGTH_LONG).show()
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
                Snackbar.make(binding.root, "Notification permission granted.", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, "Notification permission denied. Task reminders might not work.", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}

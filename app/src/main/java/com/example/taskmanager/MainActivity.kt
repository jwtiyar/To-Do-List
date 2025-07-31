package com.example.taskmanager

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taskmanager.databinding.ActivityMainBinding
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var notificationHelper: NotificationHelper
    private val tasks = mutableListOf<Task>()
    private var taskIdCounter = 1
    
    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        notificationHelper = NotificationHelper(this)
        setupRecyclerView()
        setupButtons()
        requestNotificationPermission()
    }
    
    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(tasks) { task ->
            // Handle task completion
            if (task.isCompleted) {
                Toast.makeText(this, "Task completed: ${task.title}", Toast.LENGTH_SHORT).show()
                // Cancel notification if task is completed
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
            val completedCount = tasks.count { it.isCompleted }
            if (completedCount > 0) {
                // Cancel notifications for completed tasks
                tasks.filter { it.isCompleted }.forEach { task ->
                    notificationHelper.cancelNotification(task)
                }
                taskAdapter.removeCompletedTasks()
                Toast.makeText(this, "Removed $completedCount completed tasks", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No completed tasks to remove", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.btnResetTasks.setOnClickListener {
            tasks.forEach { task ->
                task.isCompleted = false
                // Reschedule notifications for reset tasks
                if (task.scheduledTime != null) {
                    notificationHelper.scheduleNotification(task)
                }
            }
            taskAdapter.notifyDataSetChanged()
            Toast.makeText(this, "All tasks reset", Toast.LENGTH_SHORT).show()
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
        
        // Show/hide reminder layout based on checkbox
        reminderCheckBox.setOnCheckedChangeListener { _, isChecked ->
            reminderLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) selectedDate = null
        }
        
        // Date picker
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
        
        // Time picker
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
                    val newTask = Task(
                        id = taskIdCounter++,
                        title = title,
                        description = description,
                        scheduledTime = if (reminderCheckBox.isChecked) selectedDate else null
                    )
                    
                    tasks.add(newTask)
                    taskAdapter.notifyItemInserted(tasks.size - 1)
                    
                    // Schedule notification if reminder is set
                    if (newTask.scheduledTime != null) {
                        notificationHelper.scheduleNotification(newTask)
                        Toast.makeText(this, "Task added with reminder", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Task added successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please enter a task title", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 
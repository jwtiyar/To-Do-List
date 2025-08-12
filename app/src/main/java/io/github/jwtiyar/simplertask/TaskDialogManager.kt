package io.github.jwtiyar.simplertask

import android.content.Context
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RadioGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.button.MaterialButton
import android.view.View
import io.github.jwtiyar.simplertask.Task
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.Priority
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import androidx.fragment.app.FragmentActivity
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class TaskDialogManager(private val context: FragmentActivity) {
    
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    fun showAddTaskDialog(onTaskAdded: (Task) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_task, null)
        val titleInput = view.findViewById<EditText>(R.id.editTextTitle)
        val descInput = view.findViewById<EditText>(R.id.editTextDescription)
        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupPriority)
        val checkReminder = view.findViewById<MaterialCheckBox>(R.id.checkBoxSetReminder)
        val reminderLayout = view.findViewById<View>(R.id.reminderLayout)
        val btnDate = view.findViewById<MaterialButton>(R.id.btnDatePicker)
        val btnTime = view.findViewById<MaterialButton>(R.id.btnTimePicker)

        // Set default priority to MEDIUM
        radioGroup.check(R.id.radioMedium)

        var dueDateMillis: Long? = null
        var selectedDate: Long? = null
        var selectedHour: Int = 9
        var selectedMinute: Int = 0
        
        fun updateDueDateMillis() {
            selectedDate?.let { dateMillis ->
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = dateMillis
                calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                calendar.set(Calendar.MINUTE, selectedMinute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                dueDateMillis = calendar.timeInMillis
            }
        }
        
        fun updateButtonTexts() {
            selectedDate?.let { 
                btnDate.text = dateFormat.format(Date(it))
            }
            btnTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
        }
        
        checkReminder.setOnCheckedChangeListener { _, checked ->
            reminderLayout.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) {
                dueDateMillis = null
                selectedDate = null
            }
        }
        
        // Date picker implementation
        btnDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(selectedDate ?: MaterialDatePicker.todayInUtcMilliseconds())
                .build()
            
            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedDate = selection
                updateDueDateMillis()
                updateButtonTexts()
            }
            
            datePicker.show(context.supportFragmentManager, "DATE_PICKER")
        }
        
        // Time picker implementation
        btnTime.setOnClickListener {
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText("Select time")
                .build()
            
            timePicker.addOnPositiveButtonClickListener {
                selectedHour = timePicker.hour
                selectedMinute = timePicker.minute
                updateDueDateMillis()
                updateButtonTexts()
            }
            
            timePicker.show(context.supportFragmentManager, "TIME_PICKER")
        }
        
        // Initialize button texts
        updateButtonTexts()

        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_add_task_title)
            .setView(view)
            .setPositiveButton(R.string.add) { _, _ ->
                val title = titleInput.text?.toString()?.trim().orEmpty()
                if (title.isNotBlank()) {
                    val desc = descInput.text?.toString()?.trim().orEmpty()
                    val priority = when (radioGroup.checkedRadioButtonId) {
                        R.id.radioHigh -> Priority.HIGH
                        R.id.radioMedium -> Priority.MEDIUM
                        else -> Priority.LOW
                    }
                    val task = Task(id = 0, title = title, description = desc, priority = priority, dueDateMillis = dueDateMillis)
                    onTaskAdded(task)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showEditTaskDialog(task: Task, onTaskUpdated: (Task) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_task, null)
        val titleInput = view.findViewById<EditText>(R.id.editTextTitle)
        val descInput = view.findViewById<EditText>(R.id.editTextDescription)
        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupPriority)
        val checkReminder = view.findViewById<MaterialCheckBox>(R.id.checkBoxSetReminder)
        val reminderLayout = view.findViewById<View>(R.id.reminderLayout)
        val btnDate = view.findViewById<MaterialButton>(R.id.btnDatePicker)
        val btnTime = view.findViewById<MaterialButton>(R.id.btnTimePicker)

        titleInput.setText(task.title)
        descInput.setText(task.description)
        when (task.priority) {
            Priority.HIGH -> radioGroup.check(R.id.radioHigh)
            Priority.MEDIUM -> radioGroup.check(R.id.radioMedium)
            Priority.LOW -> radioGroup.check(R.id.radioLow)
        }
        var dueDateMillis: Long? = task.dueDateMillis
        var selectedDate: Long? = null
        var selectedHour: Int = 9
        var selectedMinute: Int = 0
        
        // Initialize from existing task
        if (dueDateMillis != null) {
            checkReminder.isChecked = true
            reminderLayout.visibility = View.VISIBLE
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dueDateMillis!!
            selectedDate = dueDateMillis
            selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
            selectedMinute = calendar.get(Calendar.MINUTE)
        }
        
        fun updateDueDateMillis() {
            selectedDate?.let { dateMillis ->
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = dateMillis
                calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                calendar.set(Calendar.MINUTE, selectedMinute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                dueDateMillis = calendar.timeInMillis
            }
        }
        
        fun updateButtonTexts() {
            selectedDate?.let { 
                btnDate.text = dateFormat.format(Date(it))
            }
            btnTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
        }
        
        checkReminder.setOnCheckedChangeListener { _, checked ->
            reminderLayout.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) {
                dueDateMillis = null
                selectedDate = null
            }
        }
        
        // Date picker implementation
        btnDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(selectedDate ?: MaterialDatePicker.todayInUtcMilliseconds())
                .build()
            
            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedDate = selection
                updateDueDateMillis()
                updateButtonTexts()
            }
            
            datePicker.show(context.supportFragmentManager, "DATE_PICKER")
        }
        
        // Time picker implementation
        btnTime.setOnClickListener {
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText("Select time")
                .build()
            
            timePicker.addOnPositiveButtonClickListener {
                selectedHour = timePicker.hour
                selectedMinute = timePicker.minute
                updateDueDateMillis()
                updateButtonTexts()
            }
            
            timePicker.show(context.supportFragmentManager, "TIME_PICKER")
        }
        
        // Initialize button texts
        updateButtonTexts()

        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_edit_task_title)
            .setView(view)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val updated = task.copy(
                    title = titleInput.text?.toString()?.trim().orEmpty(),
                    description = descInput.text?.toString()?.trim().orEmpty(),
                    priority = when (radioGroup.checkedRadioButtonId) {
                        R.id.radioHigh -> Priority.HIGH
                        R.id.radioMedium -> Priority.MEDIUM
                        else -> Priority.LOW
                    },
                    dueDateMillis = dueDateMillis
                )
                onTaskUpdated(updated)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

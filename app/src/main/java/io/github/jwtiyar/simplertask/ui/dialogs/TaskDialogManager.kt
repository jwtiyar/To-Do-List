package io.github.jwtiyar.simplertask.ui.dialogs

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.scopes.ActivityScoped
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType
import io.github.jwtiyar.simplertask.data.local.entity.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@ActivityScoped
class TaskDialogManager @Inject constructor() {
    
    private lateinit var activity: FragmentActivity
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    fun attach(activity: FragmentActivity) {
        this.activity = activity
    }
    
    fun showAddTaskDialog(onTaskAdded: (Task) -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_add_task, null)
        val titleInput = view.findViewById<EditText>(R.id.editTextTitle)
        val descInput = view.findViewById<EditText>(R.id.editTextDescription)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupPriority)
        val switchReminder = view.findViewById<MaterialSwitch>(R.id.switchReminder)
        val reminderDetailsLayout = view.findViewById<View>(R.id.reminderDetailsLayout)
        val btnDate = view.findViewById<MaterialButton>(R.id.btnDatePicker)
        val btnTime = view.findViewById<MaterialButton>(R.id.btnTimePicker)

        // Recurrence UI elements
        val switchRecurring = view.findViewById<MaterialSwitch>(R.id.switchRecurring)
        val recurrenceDetailsLayout = view.findViewById<View>(R.id.recurrenceDetailsLayout)
        val editRecurrenceInterval = view.findViewById<EditText>(R.id.editRecurrenceInterval)
        val spinnerRecurrenceType = view.findViewById<Spinner>(R.id.spinnerRecurrenceType)
        val radioGroupRecurrenceEnd = view.findViewById<RadioGroup>(R.id.radioGroupRecurrenceEnd)

        // Category selection
        val spinnerCategory = view.findViewById<AutoCompleteTextView>(R.id.spinnerCategory)
        val categoryNames = arrayOf("No Category", "Work", "Personal", "Health", "Learning", "Shopping", "Home")
        val categoryAdapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, categoryNames)
        spinnerCategory.setAdapter(categoryAdapter)
        spinnerCategory.setText("No Category", false)

        // Set default priority to MEDIUM
        chipGroup.check(R.id.chipMedium)

        var dueDateMillis: Long? = null
        var selectedDate: Long? = null
        var selectedHour: Int = 9
        var selectedMinute: Int = 0

        // Recurrence variables
        var recurrenceType: RecurrenceType? = null
        var recurrenceInterval: Int = 1
        var recurrenceEndDate: Long? = null
        val selectedEndDate: Long? = null

        // Setup recurrence type spinner
        val recurrenceTypes = arrayOf(
            activity.getString(R.string.recurrence_daily),
            activity.getString(R.string.recurrence_weekly),
            activity.getString(R.string.recurrence_monthly)
        )
        val spinnerAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, recurrenceTypes)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRecurrenceType.adapter = spinnerAdapter
        spinnerRecurrenceType.setSelection(0) // Default to daily
        
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
            } ?: run {
                btnDate.setText(R.string.select_date)
            }
            btnTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
        }
        
        switchReminder.setOnCheckedChangeListener { _, checked ->
            reminderDetailsLayout.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) {
                dueDateMillis = null
                selectedDate = null
            } else if (selectedDate == null) {
                selectedDate = MaterialDatePicker.todayInUtcMilliseconds()
                updateDueDateMillis()
                updateButtonTexts()
            }
        }
        
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
            datePicker.show(activity.supportFragmentManager, "DATE_PICKER")
        }
        
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
            timePicker.show(activity.supportFragmentManager, "TIME_PICKER")
        }

        updateButtonTexts()

        // Recurrence setup
        switchRecurring.setOnCheckedChangeListener { _, checked ->
            recurrenceDetailsLayout.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) {
                recurrenceType = null
                recurrenceInterval = 1
                recurrenceEndDate = null
            }
        }

        AlertDialog.Builder(activity)
            .setView(view)
            .setPositiveButton(R.string.add) { _, _ ->
                val title = titleInput.text?.toString()?.trim().orEmpty()
                if (title.isNotBlank()) {
                    val desc = descInput.text?.toString()?.trim().orEmpty()
                    val priority = when (chipGroup.checkedChipId) {
                        R.id.chipHigh -> Priority.HIGH
                        R.id.chipMedium -> Priority.MEDIUM
                        else -> Priority.LOW
                    }


                    // Handle recurrence settings
                    if (switchRecurring.isChecked) {
                        recurrenceType = when (spinnerRecurrenceType.selectedItemPosition) {
                            0 -> RecurrenceType.DAILY
                            1 -> RecurrenceType.WEEKLY
                            2 -> RecurrenceType.MONTHLY
                            else -> RecurrenceType.DAILY
                        }
                        recurrenceInterval = editRecurrenceInterval.text?.toString()?.toIntOrNull() ?: 1
                        recurrenceEndDate = if (radioGroupRecurrenceEnd.checkedRadioButtonId == R.id.radioEndDate) {
                            selectedEndDate
                        } else null
                    }

                    // Handle category selection
                    val selectedCategory = when (spinnerCategory.text.toString()) {
                        "Work" -> 1
                        "Personal" -> 2
                        "Health" -> 3
                        "Learning" -> 4
                        "Shopping" -> 5
                        "Home" -> 6
                        else -> null
                    }

                    // Create the task with recurrence and category settings
                    val task = if (recurrenceType != null) {
                        Task(
                            id = 0,
                            title = title,
                            description = desc,
                            priority = priority,
                            dueDateMillis = dueDateMillis,
                            recurrenceType = recurrenceType,
                            recurrenceInterval = recurrenceInterval,
                            recurrenceEndDate = recurrenceEndDate,
                            categoryId = selectedCategory
                        )
                    } else {
                        Task(id = 0, title = title, description = desc, priority = priority, dueDateMillis = dueDateMillis, categoryId = selectedCategory)
                    }

                    onTaskAdded(task)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showEditTaskDialog(task: Task, onTaskUpdated: (Task) -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_add_task, null)
        val titleInput = view.findViewById<EditText>(R.id.editTextTitle)
        val descInput = view.findViewById<EditText>(R.id.editTextDescription)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupPriority)
        val switchReminder = view.findViewById<MaterialSwitch>(R.id.switchReminder)
        val reminderDetailsLayout = view.findViewById<View>(R.id.reminderDetailsLayout)
        val btnDate = view.findViewById<MaterialButton>(R.id.btnDatePicker)
        val btnTime = view.findViewById<MaterialButton>(R.id.btnTimePicker)
        val dialogTitle = view.findViewById<TextView>(R.id.dialogTitle)

        dialogTitle.setText(R.string.dialog_edit_task_title)
        titleInput.setText(task.title)
        descInput.setText(task.description)
        
        when (task.priority) {
            Priority.HIGH -> chipGroup.check(R.id.chipHigh)
            Priority.MEDIUM -> chipGroup.check(R.id.chipMedium)
            Priority.LOW -> chipGroup.check(R.id.chipLow)
        }
        
        var dueDateMillis: Long? = task.dueDateMillis
        var selectedDate: Long? = null
        var selectedHour: Int = 9
        var selectedMinute: Int = 0
        
        if (dueDateMillis != null) {
            switchReminder.isChecked = true
            reminderDetailsLayout.visibility = View.VISIBLE
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dueDateMillis!!
            selectedDate = calendar.timeInMillis
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
            } ?: run {
                btnDate.setText(R.string.select_date)
            }
            btnTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
        }
        
        switchReminder.setOnCheckedChangeListener { _, checked ->
            reminderDetailsLayout.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) {
                dueDateMillis = null
                selectedDate = null
            } else if (selectedDate == null) {
                selectedDate = MaterialDatePicker.todayInUtcMilliseconds()
                updateDueDateMillis()
                updateButtonTexts()
            }
        }
        
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
            datePicker.show(activity.supportFragmentManager, "DATE_PICKER")
        }
        
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
            timePicker.show(activity.supportFragmentManager, "TIME_PICKER")
        }
        
        updateButtonTexts()

        AlertDialog.Builder(activity)
            .setView(view)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val updated = task.copy(
                    title = titleInput.text?.toString()?.trim().orEmpty(),
                    description = descInput.text?.toString()?.trim().orEmpty(),
                    priority = when (chipGroup.checkedChipId) {
                        R.id.chipHigh -> Priority.HIGH
                        R.id.chipMedium -> Priority.MEDIUM
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
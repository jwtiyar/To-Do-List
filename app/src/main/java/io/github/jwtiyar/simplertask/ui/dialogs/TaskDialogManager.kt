package io.github.jwtiyar.simplertask.ui.dialogs

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        val spinnerRecurrenceType = view.findViewById<AutoCompleteTextView>(R.id.spinnerRecurrenceType)
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
        var selectedRecurrenceEndDate: Long? = null

        // Setup recurrence type spinner
        val recurrenceTypes = arrayOf(
            activity.getString(R.string.recurrence_daily),
            activity.getString(R.string.recurrence_weekly),
            activity.getString(R.string.recurrence_monthly)
        )
        val spinnerAdapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, recurrenceTypes)
        spinnerRecurrenceType.setAdapter(spinnerAdapter)
        spinnerRecurrenceType.setText(recurrenceTypes[0], false) // Default to daily
        
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
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
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
                selectedRecurrenceEndDate = null
            }
        }

        val btnRecurrenceEndDate = view.findViewById<MaterialButton>(R.id.btnRecurrenceEndDate)
        btnRecurrenceEndDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select recurrence end date")
                .setSelection(selectedRecurrenceEndDate ?: MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedRecurrenceEndDate = selection
                btnRecurrenceEndDate.text = dateFormat.format(Date(selection))
            }
            datePicker.show(activity.supportFragmentManager, "RECURRENCE_DATE_PICKER")
        }

        val radioNever = view.findViewById<MaterialRadioButton>(R.id.radioNever)
        val radioEndDate = view.findViewById<MaterialRadioButton>(R.id.radioEndDate)
        val tvOnDateLabel = view.findViewById<TextView>(R.id.tvOnDateLabel)

        fun updateRecurrenceEndStates(isNever: Boolean) {
            radioNever.isChecked = isNever
            radioEndDate.isChecked = !isNever
            btnRecurrenceEndDate.isEnabled = !isNever
        }

        radioNever.setOnClickListener { updateRecurrenceEndStates(true) }
        radioEndDate.setOnClickListener { updateRecurrenceEndStates(false) }
        tvOnDateLabel.setOnClickListener { updateRecurrenceEndStates(false) }

        // Initial state
        updateRecurrenceEndStates(true)

        MaterialAlertDialogBuilder(activity)
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
                        recurrenceType = when (spinnerRecurrenceType.text.toString()) {
                            activity.getString(R.string.recurrence_daily) -> RecurrenceType.DAILY
                            activity.getString(R.string.recurrence_weekly) -> RecurrenceType.WEEKLY
                            activity.getString(R.string.recurrence_monthly) -> RecurrenceType.MONTHLY
                            else -> RecurrenceType.DAILY
                        }
                        recurrenceInterval = editRecurrenceInterval.text?.toString()?.toIntOrNull() ?: 1
                        recurrenceEndDate = if (radioEndDate.isChecked) {
                            selectedRecurrenceEndDate
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

        // Recurrence UI elements
        val switchRecurring = view.findViewById<MaterialSwitch>(R.id.switchRecurring)
        val recurrenceDetailsLayout = view.findViewById<View>(R.id.recurrenceDetailsLayout)
        val editRecurrenceInterval = view.findViewById<EditText>(R.id.editRecurrenceInterval)
        val spinnerRecurrenceType = view.findViewById<AutoCompleteTextView>(R.id.spinnerRecurrenceType)
        val btnRecurrenceEndDate = view.findViewById<MaterialButton>(R.id.btnRecurrenceEndDate)
        val radioNever = view.findViewById<MaterialRadioButton>(R.id.radioNever)
        val radioEndDate = view.findViewById<MaterialRadioButton>(R.id.radioEndDate)
        val tvOnDateLabel = view.findViewById<TextView>(R.id.tvOnDateLabel)

        // Category selection
        val spinnerCategory = view.findViewById<AutoCompleteTextView>(R.id.spinnerCategory)
        val categoryNames = arrayOf("No Category", "Work", "Personal", "Health", "Learning", "Shopping", "Home")
        val categoryAdapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, categoryNames)
        spinnerCategory.setAdapter(categoryAdapter)
        
        // Setup initial values
        dialogTitle.setText(R.string.dialog_edit_task_title)
        titleInput.setText(task.title)
        descInput.setText(task.description)
        
        when (task.priority) {
            Priority.HIGH -> chipGroup.check(R.id.chipHigh)
            Priority.MEDIUM -> chipGroup.check(R.id.chipMedium)
            Priority.LOW -> chipGroup.check(R.id.chipLow)
        }

        // Set category
        val categoryName = when (task.categoryId) {
            1 -> "Work"
            2 -> "Personal"
            3 -> "Health"
            4 -> "Learning"
            5 -> "Shopping"
            6 -> "Home"
            else -> "No Category"
        }
        spinnerCategory.setText(categoryName, false)
        
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

        // Recurrence initial setup
        var recurrenceType: RecurrenceType? = task.recurrenceType
        var recurrenceInterval: Int = task.recurrenceInterval
        var selectedRecurrenceEndDate: Long? = task.recurrenceEndDate
        
        if (recurrenceType != null) {
            switchRecurring.isChecked = true
            recurrenceDetailsLayout.visibility = View.VISIBLE
            editRecurrenceInterval.setText(recurrenceInterval.toString())
            
            val recurrenceTypes = arrayOf(
                activity.getString(R.string.recurrence_daily),
                activity.getString(R.string.recurrence_weekly),
                activity.getString(R.string.recurrence_monthly)
            )
            val spinnerAdapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, recurrenceTypes)
            spinnerRecurrenceType.setAdapter(spinnerAdapter)
            
            val typeText = when (recurrenceType) {
                RecurrenceType.DAILY -> recurrenceTypes[0]
                RecurrenceType.WEEKLY -> recurrenceTypes[1]
                RecurrenceType.MONTHLY -> recurrenceTypes[2]
                else -> recurrenceTypes[0]
            }
            spinnerRecurrenceType.setText(typeText, false)

            if (selectedRecurrenceEndDate != null) {
                radioEndDate.isChecked = true
                radioNever.isChecked = false
                btnRecurrenceEndDate.isEnabled = true
                btnRecurrenceEndDate.text = dateFormat.format(Date(selectedRecurrenceEndDate))
            } else {
                radioEndDate.isChecked = false
                radioNever.isChecked = true
                btnRecurrenceEndDate.isEnabled = false
            }
        } else {
            // Setup adapter even if not recurring so it's ready if toggled
            val recurrenceTypes = arrayOf(
                activity.getString(R.string.recurrence_daily),
                activity.getString(R.string.recurrence_weekly),
                activity.getString(R.string.recurrence_monthly)
            )
            val spinnerAdapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, recurrenceTypes)
            spinnerRecurrenceType.setAdapter(spinnerAdapter)
            spinnerRecurrenceType.setText(recurrenceTypes[0], false)
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
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
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

        // Recurrence interaction logic
        switchRecurring.setOnCheckedChangeListener { _, checked ->
            recurrenceDetailsLayout.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) {
                // Reset all recurrence state when switch is turned off
                selectedRecurrenceEndDate = null
                radioNever.isChecked = true
                radioEndDate.isChecked = false
                btnRecurrenceEndDate.isEnabled = false
                btnRecurrenceEndDate.setText(R.string.select_date)
            }
        }

        btnRecurrenceEndDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select recurrence end date")
                .setSelection(selectedRecurrenceEndDate ?: MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedRecurrenceEndDate = selection
                btnRecurrenceEndDate.text = dateFormat.format(Date(selection))
            }
            datePicker.show(activity.supportFragmentManager, "RECURRENCE_DATE_PICKER")
        }

        fun updateRecurrenceEndStates(isNever: Boolean) {
            radioNever.isChecked = isNever
            radioEndDate.isChecked = !isNever
            btnRecurrenceEndDate.isEnabled = !isNever
        }

        radioNever.setOnClickListener {
            updateRecurrenceEndStates(true)
            selectedRecurrenceEndDate = null
        }
        radioEndDate.setOnClickListener { updateRecurrenceEndStates(false) }
        tvOnDateLabel.setOnClickListener { updateRecurrenceEndStates(false) }
        
        updateButtonTexts()

        MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val title = titleInput.text?.toString()?.trim().orEmpty()
                if (title.isNotBlank()) {
                    val desc = descInput.text?.toString()?.trim().orEmpty()
                    val priority = when (chipGroup.checkedChipId) {
                        R.id.chipHigh -> Priority.HIGH
                        R.id.chipMedium -> Priority.MEDIUM
                        else -> Priority.LOW
                    }

                    // Handle recurring
                    var finalRecurrenceType: RecurrenceType? = null
                    var finalRecurrenceInterval = 1
                    var finalRecurrenceEndDate: Long? = null
                    
                    if (switchRecurring.isChecked) {
                        finalRecurrenceType = when (spinnerRecurrenceType.text.toString()) {
                            activity.getString(R.string.recurrence_daily) -> RecurrenceType.DAILY
                            activity.getString(R.string.recurrence_weekly) -> RecurrenceType.WEEKLY
                            activity.getString(R.string.recurrence_monthly) -> RecurrenceType.MONTHLY
                            else -> RecurrenceType.DAILY
                        }
                        finalRecurrenceInterval = editRecurrenceInterval.text?.toString()?.toIntOrNull() ?: 1
                        finalRecurrenceEndDate = if (radioEndDate.isChecked) {
                            selectedRecurrenceEndDate
                        } else null
                    }

                    // Handle category
                    val selectedCategory = when (spinnerCategory.text.toString()) {
                        "Work" -> 1
                        "Personal" -> 2
                        "Health" -> 3
                        "Learning" -> 4
                        "Shopping" -> 5
                        "Home" -> 6
                        else -> null
                    }

                    val updated = task.copy(
                        title = title,
                        description = desc,
                        priority = priority,
                        dueDateMillis = dueDateMillis,
                        recurrenceType = finalRecurrenceType,
                        recurrenceInterval = finalRecurrenceInterval,
                        recurrenceEndDate = finalRecurrenceEndDate,
                        categoryId = selectedCategory
                    )
                    onTaskUpdated(updated)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
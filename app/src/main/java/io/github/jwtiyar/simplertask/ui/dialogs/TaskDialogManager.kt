package io.github.jwtiyar.simplertask.ui.dialogs

import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskDialogManager(private val context: FragmentActivity) {
    
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    fun showAddTaskDialog(onTaskAdded: (Task) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_task, null)
        val titleInput = view.findViewById<EditText>(R.id.editTextTitle)
        val descInput = view.findViewById<EditText>(R.id.editTextDescription)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupPriority)
        val switchReminder = view.findViewById<MaterialSwitch>(R.id.switchReminder)
        val reminderDetailsLayout = view.findViewById<View>(R.id.reminderDetailsLayout)
        val btnDate = view.findViewById<MaterialButton>(R.id.btnDatePicker)
        val btnTime = view.findViewById<MaterialButton>(R.id.btnTimePicker)

        // Set default priority to MEDIUM
        chipGroup.check(R.id.chipMedium)

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
            datePicker.show(context.supportFragmentManager, "DATE_PICKER")
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
            timePicker.show(context.supportFragmentManager, "TIME_PICKER")
        }
        
        updateButtonTexts()

        AlertDialog.Builder(context)
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
            datePicker.show(context.supportFragmentManager, "DATE_PICKER")
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
            timePicker.show(context.supportFragmentManager, "TIME_PICKER")
        }
        
        updateButtonTexts()

        AlertDialog.Builder(context)
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

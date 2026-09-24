package io.github.jwtiyar.simplertask.ui.dialogs

import android.view.LayoutInflater
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.animation.ValueAnimator
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.scopes.ActivityScoped
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.Category
import io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType
import io.github.jwtiyar.simplertask.data.local.entity.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.launch

@ActivityScoped
class TaskDialogManager @Inject constructor() {
    
    private lateinit var activity: FragmentActivity
    private var activeDraft: (() -> Bundle)? = null

    /** Guards against rapid double taps opening two sheets at once. */
    private var sheetOpen = false

    fun saveDraft(outState: Bundle) {
        activeDraft?.invoke()?.let { outState.putBundle("task_editor_draft", it) }
    }

    fun savedDraft(state: Bundle?): Bundle? = state?.getBundle("task_editor_draft")

    /** True while an editor sheet is on screen; callers skip a second open. */
    fun isEditorOpen(): Boolean = sheetOpen

    private fun draft(view: View, taskId: Int, selectedDate: Long?, hour: Int, minute: Int,
                      recurrenceEndDate: Long?): Bundle = Bundle().apply {
        putInt("taskId", taskId)
        putString("title", view.findViewById<EditText>(R.id.editTextTitle).text.toString())
        putString("description", view.findViewById<EditText>(R.id.editTextDescription).text.toString())
        putInt("priority", view.findViewById<ChipGroup>(R.id.chipGroupPriority).checkedChipId)
        putBoolean("reminder", view.findViewById<MaterialSwitch>(R.id.switchReminder).isChecked)
        selectedDate?.let { putLong("selectedDate", it) }
        putInt("hour", hour)
        putInt("minute", minute)
        putBoolean("recurring", view.findViewById<MaterialSwitch>(R.id.switchRecurring).isChecked)
        putString("recurrenceType", view.findViewById<AutoCompleteTextView>(R.id.spinnerRecurrenceType).text.toString())
        putString("interval", view.findViewById<EditText>(R.id.editRecurrenceInterval).text.toString())
        putBoolean("endDateSelected", view.findViewById<MaterialRadioButton>(R.id.radioEndDate).isChecked)
        recurrenceEndDate?.let { putLong("recurrenceEndDate", it) }
        putString("category", view.findViewById<AutoCompleteTextView>(R.id.spinnerCategory).text.toString())
        putBoolean("expanded", view.findViewById<View>(R.id.advancedTaskOptions).visibility == View.VISIBLE)
    }

    private fun restoreFields(view: View, state: Bundle) {
        view.findViewById<EditText>(R.id.editTextTitle).setText(state.getString("title"))
        view.findViewById<EditText>(R.id.editTextDescription).setText(state.getString("description"))
        view.findViewById<ChipGroup>(R.id.chipGroupPriority).check(state.getInt("priority", R.id.chipMedium))
        view.findViewById<AutoCompleteTextView>(R.id.spinnerCategory).setText(state.getString("category"), false)
        view.findViewById<AutoCompleteTextView>(R.id.spinnerRecurrenceType).setText(state.getString("recurrenceType"), false)
        view.findViewById<EditText>(R.id.editRecurrenceInterval).setText(state.getString("interval"))
        setupAdvancedOptions(view, state.getBoolean("expanded"))
    }

    private fun protectedDialog(view: View, currentDraft: () -> Bundle): BottomSheetDialog =
        object : BottomSheetDialog(activity) {
            private var original: Bundle? = null

            override fun onStart() {
                super.onStart()
                original = currentDraft()
            }

            override fun cancel() {
                val before = original
                val after = currentDraft()
                if (before == null || before.keySet().all { key ->
                        when (key) {
                            "taskId", "priority", "hour", "minute" -> before.getInt(key) == after.getInt(key)
                            "reminder", "recurring", "endDateSelected", "expanded" -> before.getBoolean(key) == after.getBoolean(key)
                            "selectedDate", "recurrenceEndDate" -> before.containsKey(key) == after.containsKey(key) &&
                                before.getLong(key) == after.getLong(key)
                            else -> before.getString(key) == after.getString(key)
                        }
                    }) {
                    super.cancel()
                } else {
                    MaterialAlertDialogBuilder(activity)
                        .setMessage(R.string.discard_task_changes)
                        .setNegativeButton(R.string.keep_editing, null)
                        .setPositiveButton(R.string.discard_changes) { _, _ -> super.cancel() }
                        .show()
                }
            }
        }.apply { setContentView(view) }
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun attach(activity: FragmentActivity) {
        this.activity = activity
    }

    private fun setupAdvancedOptions(view: View, expanded: Boolean) {
        val options = view.findViewById<View>(R.id.advancedTaskOptions)
        val button = view.findViewById<MaterialButton>(R.id.btnMoreOptions)
        options.visibility = if (expanded) View.VISIBLE else View.GONE
        button.setText(if (expanded) R.string.fewer_task_options else R.string.more_task_options)
        button.setOnClickListener {
            if (ValueAnimator.areAnimatorsEnabled()) {
                TransitionManager.beginDelayedTransition(view as ViewGroup, AutoTransition().apply { duration = 180 })
            }
            val nowExpanded = options.visibility != View.VISIBLE
            options.visibility = if (nowExpanded) View.VISIBLE else View.GONE
            button.setText(if (nowExpanded) R.string.fewer_task_options else R.string.more_task_options)
        }
    }

    private fun submitTask(
        dialog: BottomSheetDialog,
        saveButton: MaterialButton,
        task: Task,
        onSave: suspend (Task) -> Boolean
    ) {
        if (!saveButton.isEnabled) return
        saveButton.isEnabled = false
        activity.lifecycleScope.launch {
            try {
                if (onSave(task)) dialog.dismiss()
            } finally {
                saveButton.isEnabled = true
            }
        }
    }
    
    fun showAddTaskDialog(categories: List<Category>, initial: Bundle? = null, onTaskAdded: suspend (Task) -> Boolean) {
        if (sheetOpen) return
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_add_task, null)
        setupAdvancedOptions(view, false)
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
        val noCategory = activity.getString(R.string.no_category)
        val categoryNames = listOf(noCategory) + categories.map { it.name }
        val categoryAdapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, categoryNames)
        spinnerCategory.setAdapter(categoryAdapter)
        spinnerCategory.setText(noCategory, false)

        // Set default priority to MEDIUM
        chipGroup.check(R.id.chipMedium)

        var dueDateMillis: Long? = null
        var selectedDate: Long? = null
        val nowCalendar = Calendar.getInstance()
        var selectedHour: Int = nowCalendar.get(Calendar.HOUR_OF_DAY)
        var selectedMinute: Int = nowCalendar.get(Calendar.MINUTE)

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
                // MaterialDatePicker returns UTC midnight. We must extract the YMD in UTC
                // and then apply it to a local Calendar to avoid timezone shifts to yesterday.
                val utcCalendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                utcCalendar.timeInMillis = dateMillis
                
                val localCalendar = Calendar.getInstance()
                localCalendar.set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
                localCalendar.set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
                localCalendar.set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))
                localCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                localCalendar.set(Calendar.MINUTE, selectedMinute)
                localCalendar.set(Calendar.SECOND, 0)
                localCalendar.set(Calendar.MILLISECOND, 0)
                
                dueDateMillis = localCalendar.timeInMillis
            }
        }
        
        fun updateButtonTexts() {
            selectedDate?.let { 
                btnDate.text = dateFormat.format(Date(it))
            } ?: run {
                btnDate.setText(R.string.select_date)
            }
            btnTime.text = android.text.format.DateFormat.getTimeFormat(activity).format(
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                }.time
            )
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
                .setTitleText(R.string.select_date)
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
                .setTimeFormat(if (android.text.format.DateFormat.is24HourFormat(activity)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText(R.string.select_time)
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
                .setTitleText(R.string.select_end_date)
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

        fun applyNever() {
            btnRecurrenceEndDate.isEnabled = false
            selectedRecurrenceEndDate = null
        }
        fun applyOnDate() {
            btnRecurrenceEndDate.isEnabled = true
        }

        radioGroupRecurrenceEnd.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioNever -> applyNever()
                R.id.radioEndDate -> applyOnDate()
            }
        }

        // Initial state
        radioNever.isChecked = true
        applyNever()

        val dialog = protectedDialog(view) { draft(view, 0, selectedDate, selectedHour, selectedMinute, selectedRecurrenceEndDate) }
        view.findViewById<MaterialButton>(R.id.btnCancelTask).setOnClickListener { dialog.cancel() }
        view.findViewById<MaterialButton>(R.id.btnSaveTask).setOnClickListener {
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
                        val interval = editRecurrenceInterval.text?.toString()?.toIntOrNull()
                        if (interval == null || interval < 1) {
                            editRecurrenceInterval.error = activity.getString(R.string.recurrence_interval_error)
                            editRecurrenceInterval.requestFocus()
                            return@setOnClickListener
                        }
                        recurrenceInterval = interval
                        recurrenceEndDate = if (radioEndDate.isChecked) {
                            selectedRecurrenceEndDate
                        } else null
                    }

                    // Handle category selection
                    val selectedCategory = categories.firstOrNull { it.name == spinnerCategory.text.toString() }?.id

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

                    submitTask(dialog, view.findViewById(R.id.btnSaveTask), task, onTaskAdded)
                } else {
                    titleInput.error = activity.getString(R.string.task_title_required)
                    titleInput.requestFocus()
                }
        }
        initial?.let { state ->
            restoreFields(view, state)
            selectedHour = state.getInt("hour", selectedHour)
            selectedMinute = state.getInt("minute", selectedMinute)
            selectedDate = if (state.containsKey("selectedDate")) state.getLong("selectedDate") else null
            switchReminder.isChecked = state.getBoolean("reminder")
            if (switchReminder.isChecked) updateDueDateMillis()
            updateButtonTexts()
            switchRecurring.isChecked = state.getBoolean("recurring")
            selectedRecurrenceEndDate = if (state.containsKey("recurrenceEndDate")) state.getLong("recurrenceEndDate") else null
            if (state.getBoolean("endDateSelected")) {
                radioEndDate.isChecked = true
                selectedRecurrenceEndDate?.let { btnRecurrenceEndDate.text = dateFormat.format(Date(it)) }
            }
        }
        activeDraft = { draft(view, 0, selectedDate, selectedHour, selectedMinute, selectedRecurrenceEndDate) }
        dialog.setOnDismissListener {
            activeDraft = null
            sheetOpen = false
        }
        sheetOpen = true
        dialog.show()
    }

    fun showEditTaskDialog(task: Task, categories: List<Category>, initial: Bundle? = null, onTaskUpdated: suspend (Task) -> Boolean) {
        if (sheetOpen) return
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_add_task, null)
        setupAdvancedOptions(view, task.categoryId != null || task.dueDateMillis != null ||
            task.recurrenceType != null || task.priority != Priority.MEDIUM)
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
        val radioGroupRecurrenceEnd = view.findViewById<RadioGroup>(R.id.radioGroupRecurrenceEnd)


        // Category selection
        val spinnerCategory = view.findViewById<AutoCompleteTextView>(R.id.spinnerCategory)
        val noCategory = activity.getString(R.string.no_category)
        val categoryNames = listOf(noCategory) + categories.map { it.name }
        val categoryAdapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, categoryNames)
        spinnerCategory.setAdapter(categoryAdapter)
        
        // Setup initial values
        dialogTitle.setText(R.string.dialog_edit_task_title)
        view.findViewById<MaterialButton>(R.id.btnSaveTask).setText(R.string.button_save)
        titleInput.setText(task.title)
        descInput.setText(task.description)
        
        when (task.priority) {
            Priority.HIGH -> chipGroup.check(R.id.chipHigh)
            Priority.MEDIUM -> chipGroup.check(R.id.chipMedium)
            Priority.LOW -> chipGroup.check(R.id.chipLow)
        }

        // Set category
        val categoryName = categories.firstOrNull { it.id == task.categoryId }?.name ?: noCategory
        spinnerCategory.setText(categoryName, false)
        
        var dueDateMillis: Long? = task.dueDateMillis
        var selectedDate: Long? = null
        val nowCalendar = Calendar.getInstance()
        var selectedHour: Int = nowCalendar.get(Calendar.HOUR_OF_DAY)
        var selectedMinute: Int = nowCalendar.get(Calendar.MINUTE)
        
        if (dueDateMillis != null) {
            switchReminder.isChecked = true
            reminderDetailsLayout.visibility = View.VISIBLE
            
            // Extract local time components
            val localCal = Calendar.getInstance()
            localCal.timeInMillis = dueDateMillis!!
            selectedHour = localCal.get(Calendar.HOUR_OF_DAY)
            selectedMinute = localCal.get(Calendar.MINUTE)
            
            // Construct UTC midnight timestamp for selectedDate
            val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            utcCal.set(Calendar.YEAR, localCal.get(Calendar.YEAR))
            utcCal.set(Calendar.MONTH, localCal.get(Calendar.MONTH))
            utcCal.set(Calendar.DAY_OF_MONTH, localCal.get(Calendar.DAY_OF_MONTH))
            utcCal.set(Calendar.HOUR_OF_DAY, 0)
            utcCal.set(Calendar.MINUTE, 0)
            utcCal.set(Calendar.SECOND, 0)
            utcCal.set(Calendar.MILLISECOND, 0)
            selectedDate = utcCal.timeInMillis
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
                // MaterialDatePicker returns UTC midnight. We must extract the YMD in UTC
                // and then apply it to a local Calendar to avoid timezone shifts to yesterday.
                val utcCalendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                utcCalendar.timeInMillis = dateMillis
                
                val localCalendar = Calendar.getInstance()
                localCalendar.set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
                localCalendar.set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
                localCalendar.set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))
                localCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                localCalendar.set(Calendar.MINUTE, selectedMinute)
                localCalendar.set(Calendar.SECOND, 0)
                localCalendar.set(Calendar.MILLISECOND, 0)
                
                dueDateMillis = localCalendar.timeInMillis
            }
        }
        
        fun updateButtonTexts() {
            selectedDate?.let { 
                btnDate.text = dateFormat.format(Date(it))
            } ?: run {
                btnDate.setText(R.string.select_date)
            }
            btnTime.text = android.text.format.DateFormat.getTimeFormat(activity).format(
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                }.time
            )
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
                .setTitleText(R.string.select_date)
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
                .setTimeFormat(if (android.text.format.DateFormat.is24HourFormat(activity)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText(R.string.select_time)
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
                .setTitleText(R.string.select_end_date)
                .setSelection(selectedRecurrenceEndDate ?: MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedRecurrenceEndDate = selection
                btnRecurrenceEndDate.text = dateFormat.format(Date(selection))
            }
            datePicker.show(activity.supportFragmentManager, "RECURRENCE_DATE_PICKER")
        }


        radioGroupRecurrenceEnd.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioNever -> {
                    btnRecurrenceEndDate.isEnabled = false
                    selectedRecurrenceEndDate = null
                }
                R.id.radioEndDate -> btnRecurrenceEndDate.isEnabled = true
            }
        }

        updateButtonTexts()

        val dialog = protectedDialog(view) { draft(view, task.id, selectedDate, selectedHour, selectedMinute, selectedRecurrenceEndDate) }
        view.findViewById<MaterialButton>(R.id.btnCancelTask).setOnClickListener { dialog.cancel() }
        view.findViewById<MaterialButton>(R.id.btnSaveTask).setOnClickListener {
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
                        val interval = editRecurrenceInterval.text?.toString()?.toIntOrNull()
                        if (interval == null || interval < 1) {
                            editRecurrenceInterval.error = activity.getString(R.string.recurrence_interval_error)
                            editRecurrenceInterval.requestFocus()
                            return@setOnClickListener
                        }
                        finalRecurrenceInterval = interval
                        finalRecurrenceEndDate = if (radioEndDate.isChecked) {
                            selectedRecurrenceEndDate
                        } else null
                    }

                    // Handle category
                    val selectedCategory = categories.firstOrNull { it.name == spinnerCategory.text.toString() }?.id

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
                    submitTask(dialog, view.findViewById(R.id.btnSaveTask), updated, onTaskUpdated)
                } else {
                    titleInput.error = activity.getString(R.string.task_title_required)
                    titleInput.requestFocus()
                }
        }
        initial?.let { state ->
            restoreFields(view, state)
            selectedHour = state.getInt("hour", selectedHour)
            selectedMinute = state.getInt("minute", selectedMinute)
            selectedDate = if (state.containsKey("selectedDate")) state.getLong("selectedDate") else null
            switchReminder.isChecked = state.getBoolean("reminder")
            if (switchReminder.isChecked) updateDueDateMillis()
            updateButtonTexts()
            switchRecurring.isChecked = state.getBoolean("recurring")
            selectedRecurrenceEndDate = if (state.containsKey("recurrenceEndDate")) state.getLong("recurrenceEndDate") else null
            if (state.getBoolean("endDateSelected")) {
                radioEndDate.isChecked = true
                selectedRecurrenceEndDate?.let { btnRecurrenceEndDate.text = dateFormat.format(Date(it)) }
            }
        }
        activeDraft = { draft(view, task.id, selectedDate, selectedHour, selectedMinute, selectedRecurrenceEndDate) }
        dialog.setOnDismissListener {
            activeDraft = null
            sheetOpen = false
        }
        sheetOpen = true
        dialog.show()
    }
}

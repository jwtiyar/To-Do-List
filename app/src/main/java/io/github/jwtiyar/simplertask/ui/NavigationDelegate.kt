package io.github.jwtiyar.simplertask.ui

import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.scopes.ActivityScoped
import io.github.jwtiyar.simplertask.MainActivity
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.databinding.ActivityMainBinding
import io.github.jwtiyar.simplertask.service.NotificationHelper
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel
import javax.inject.Inject

/**
 * Delegate responsible for navigation drawer and menu functionality in MainActivity.
 * Handles drawer navigation, theme selection, and menu item actions.
 */
@ActivityScoped
class NavigationDelegate @Inject constructor(
    private val backupDelegate: MainActivityBackupDelegate,
    private val notificationHelper: NotificationHelper,
    private val uiDelegate: MainUiDelegate
) {
    private var activity: MainActivity? = null
    private var binding: ActivityMainBinding? = null
    private lateinit var viewModel: TaskViewModel

    private var exportBackupLauncher: ActivityResultLauncher<String>? = null
    private var importBackupLauncher: ActivityResultLauncher<Array<String>>? = null

    fun attach(activity: MainActivity, binding: ActivityMainBinding) {
        this.activity = activity
        this.binding = binding
        this.viewModel = ViewModelProvider(activity)[TaskViewModel::class.java]

        // Register launchers during attach (which is called during onCreate)
        exportBackupLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let { backupDelegate.exportBackupToUri(it) }
        }

        importBackupLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { backupDelegate.importBackupFromUri(it) }
        }
    }

    fun setupNavigationDrawer() {
        val currentBinding = binding ?: return
        val drawerLayout = currentBinding.drawerLayout
        val navigationView = currentBinding.navigationView
        currentBinding.topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        navigationView.setCheckedItem(R.id.nav_all_tasks)
        navigationView.setNavigationItemSelectedListener { item ->
            handleNavigationItemSelected(item)
            true
        }
    }

    private fun handleNavigationItemSelected(item: MenuItem): Boolean {
        val currentBinding = binding ?: return true
        val drawerLayout = currentBinding.drawerLayout
        val navigationView = currentBinding.navigationView
        val currentActivity = activity ?: return true

        when (item.itemId) {
            R.id.nav_all_tasks -> {
                viewModel.loadTasks(TaskViewModel.TaskFilter.PENDING)
                currentBinding.viewPager.currentItem = 0
                uiDelegate.updateTabVisibility(true)
                uiDelegate.updateUI(currentActivity.getString(R.string.nav_all_tasks))
                navigationView.setCheckedItem(R.id.nav_all_tasks)
            }
            R.id.nav_saved_tasks -> {
                viewModel.loadTasks(TaskViewModel.TaskFilter.SAVED)
                uiDelegate.updateTabVisibility(false)
                uiDelegate.updateUI(currentActivity.getString(R.string.nav_saved_tasks))
                navigationView.setCheckedItem(R.id.nav_saved_tasks)
            }
            R.id.nav_archive -> {
                viewModel.loadTasks(TaskViewModel.TaskFilter.ARCHIVED)
                uiDelegate.updateTabVisibility(false)
                uiDelegate.updateUI(currentActivity.getString(R.string.nav_archive))
                navigationView.setCheckedItem(R.id.nav_archive)
            }
            R.id.nav_recurring -> {
                viewModel.loadTasks(TaskViewModel.TaskFilter.RECURRING)
                uiDelegate.updateTabVisibility(false)
                uiDelegate.updateUI(currentActivity.getString(R.string.nav_recurring_tasks))
                navigationView.setCheckedItem(R.id.nav_recurring)
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    fun showThemeSelectionDialog() {
        val currentActivity = activity ?: return
        val dialogView = currentActivity.layoutInflater.inflate(R.layout.dialog_theme_selection, null)
        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroupTheme)
        val radioLight = dialogView.findViewById<android.widget.RadioButton>(R.id.radioLight)
        val radioDark = dialogView.findViewById<android.widget.RadioButton>(R.id.radioDark)
        val radioSystem = dialogView.findViewById<android.widget.RadioButton>(R.id.radioSystem)

        when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> radioLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> radioDark.isChecked = true
            else -> radioSystem.isChecked = true
        }

        AlertDialog.Builder(currentActivity)
            .setView(dialogView)
            .setTitle(currentActivity.getString(R.string.dialog_theme_title))
            .setPositiveButton(currentActivity.getString(R.string.button_ok)) { _, _ ->
                when (radioGroup.checkedRadioButtonId) {
                    R.id.radioLight -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        viewModel.postToast(currentActivity.getString(R.string.theme_light))
                    }
                    R.id.radioDark -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        viewModel.postToast(currentActivity.getString(R.string.theme_dark))
                    }
                    R.id.radioSystem -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        viewModel.postToast(currentActivity.getString(R.string.theme_system_default))
                    }
                }
            }
            .setNegativeButton(currentActivity.getString(R.string.cancel), null)
            .show()
    }

    fun showAboutDialog() {
        val currentActivity = activity ?: return
        val v = currentActivity.layoutInflater.inflate(R.layout.dialog_about, null)
        AlertDialog.Builder(currentActivity).setView(v).setTitle(currentActivity.getString(R.string.dialog_about_title)).setPositiveButton(currentActivity.getString(R.string.button_ok), null).show()
    }

    fun startExportBackup() {
        val filename = backupDelegate.generateBackupFilename()
        exportBackupLauncher?.launch(filename)
    }

    fun startImportBackup() {
        importBackupLauncher?.launch(arrayOf("application/json", "text/plain"))
    }

    fun handleNotificationSettings() {
        notificationHelper.openNotificationSettings()
    }
}

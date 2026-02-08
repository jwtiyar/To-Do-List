package io.github.jwtiyar.simplertask.ui

import android.content.Intent
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import com.google.android.material.snackbar.Snackbar
import io.github.jwtiyar.simplertask.MainActivity
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.databinding.ActivityMainBinding
import io.github.jwtiyar.simplertask.service.NotificationHelper
import io.github.jwtiyar.simplertask.ui.MainActivityBackupDelegate
import io.github.jwtiyar.simplertask.viewmodel.TaskViewModel

/**
 * Delegate responsible for navigation drawer and menu functionality in MainActivity.
 * Handles drawer navigation, theme selection, and menu item actions.
 */
class NavigationDelegate(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val viewModel: TaskViewModel,
    private val backupDelegate: MainActivityBackupDelegate,
    private val notificationHelper: NotificationHelper,
    private val uiDelegate: MainUiDelegate
) {

    // Activity result launchers for file operations
    private val exportBackupLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { backupDelegate.exportBackupToUri(it) }
    }

    private val importBackupLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { backupDelegate.importBackupFromUri(it) }
    }

    fun setupNavigationDrawer() {
        val drawerLayout = binding.drawerLayout
        val navigationView = binding.navigationView
        binding.topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        navigationView.setCheckedItem(R.id.nav_all_tasks)
        navigationView.setNavigationItemSelectedListener { item ->
            handleNavigationItemSelected(item)
            true
        }
    }

    private fun handleNavigationItemSelected(item: MenuItem): Boolean {
        val drawerLayout = binding.drawerLayout
        val navigationView = binding.navigationView

        when (item.itemId) {
            R.id.nav_all_tasks -> {
                viewModel.loadTasks(TaskViewModel.TaskFilter.PENDING)
                binding.viewPager.currentItem = 0
                uiDelegate.updateTabVisibility(true)
                uiDelegate.updateUI(activity.getString(R.string.nav_all_tasks))
                navigationView.setCheckedItem(R.id.nav_all_tasks)
            }
            R.id.nav_saved_tasks -> {
                viewModel.loadTasks(TaskViewModel.TaskFilter.SAVED)
                uiDelegate.updateTabVisibility(false)
                uiDelegate.updateUI(activity.getString(R.string.nav_saved_tasks))
                navigationView.setCheckedItem(R.id.nav_saved_tasks)
            }
            R.id.nav_archive -> {
                viewModel.loadTasks(TaskViewModel.TaskFilter.ARCHIVED)
                uiDelegate.updateTabVisibility(false)
                uiDelegate.updateUI(activity.getString(R.string.nav_archive))
                navigationView.setCheckedItem(R.id.nav_archive)
            }
            R.id.nav_recurring -> {
                viewModel.loadTasks(TaskViewModel.TaskFilter.RECURRING)
                uiDelegate.updateTabVisibility(false)
                uiDelegate.updateUI(activity.getString(R.string.nav_recurring_tasks))
                navigationView.setCheckedItem(R.id.nav_recurring)
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    fun showThemeSelectionDialog() {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_theme_selection, null)
        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroupTheme)
        val radioLight = dialogView.findViewById<android.widget.RadioButton>(R.id.radioLight)
        val radioDark = dialogView.findViewById<android.widget.RadioButton>(R.id.radioDark)
        val radioSystem = dialogView.findViewById<android.widget.RadioButton>(R.id.radioSystem)

        when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> radioLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> radioDark.isChecked = true
            else -> radioSystem.isChecked = true
        }

        AlertDialog.Builder(activity)
            .setView(dialogView)
            .setTitle(activity.getString(R.string.dialog_theme_title))
            .setPositiveButton(activity.getString(R.string.button_ok)) { _, _ ->
                when (radioGroup.checkedRadioButtonId) {
                    R.id.radioLight -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        viewModel.postToast(activity.getString(R.string.theme_light))
                    }
                    R.id.radioDark -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        viewModel.postToast(activity.getString(R.string.theme_dark))
                    }
                    R.id.radioSystem -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        viewModel.postToast(activity.getString(R.string.theme_system_default))
                    }
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    fun showAboutDialog() {
        val v = activity.layoutInflater.inflate(R.layout.dialog_about, null)
        AlertDialog.Builder(activity).setView(v).setTitle(activity.getString(R.string.dialog_about_title)).setPositiveButton(activity.getString(R.string.button_ok), null).show()
    }

    fun startExportBackup() {
        val filename = backupDelegate.generateBackupFilename()
        exportBackupLauncher.launch(filename)
    }

    fun startImportBackup() {
        importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
    }

    fun handleNotificationSettings() {
        notificationHelper.openNotificationSettings()
    }
}
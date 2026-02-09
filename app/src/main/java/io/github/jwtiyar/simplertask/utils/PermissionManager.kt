package io.github.jwtiyar.simplertask.utils

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.scopes.ActivityScoped
import io.github.jwtiyar.simplertask.R
import javax.inject.Inject

/**
 * Manages runtime permissions and system settings requests for the app.
 */
@ActivityScoped
class PermissionManager @Inject constructor() {
    private var activity: AppCompatActivity? = null

    fun attach(activity: AppCompatActivity) {
        this.activity = activity
    }

    fun checkAndRequestPostNotificationPermission(requestCode: Int) {
        val currentActivity = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentActivity.requestPermissionCompat(
                Manifest.permission.POST_NOTIFICATIONS,
                currentActivity.getString(R.string.notification_permission_needed),
                requestCode
            )
        }
    }

    fun checkAndRequestExactAlarmPermission(rootView: android.view.View) {
        val currentActivity = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = currentActivity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(currentActivity)
                    .setTitle(currentActivity.getString(R.string.exact_alarm_permission_title))
                    .setMessage(currentActivity.getString(R.string.exact_alarm_permission_message))
                    .setPositiveButton(currentActivity.getString(R.string.button_open_settings)) { _, _ ->
                        Intent().apply {
                            action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            data = Uri.fromParts("package", currentActivity.packageName, null)
                        }.also {
                            try {
                                currentActivity.startActivity(it)
                            } catch (_: Exception) {
                                currentActivity.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", currentActivity.packageName, null)
                                    )
                                )
                            }
                        }
                    }
                    .setNegativeButton(currentActivity.getString(R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                        Snackbar.make(
                            rootView,
                            currentActivity.getString(R.string.exact_alarm_not_granted),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    .show()
            }
        }
    }
}

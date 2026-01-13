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
import io.github.jwtiyar.simplertask.R

/**
 * Manages runtime permissions and system settings requests for the app.
 */
class PermissionManager(private val activity: AppCompatActivity) {

    fun checkAndRequestPostNotificationPermission(requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissionCompat(
                Manifest.permission.POST_NOTIFICATIONS,
                activity.getString(R.string.notification_permission_needed),
                requestCode
            )
        }
    }

    fun checkAndRequestExactAlarmPermission(rootView: android.view.View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.exact_alarm_permission_title))
                    .setMessage(activity.getString(R.string.exact_alarm_permission_message))
                    .setPositiveButton(activity.getString(R.string.button_open_settings)) { _, _ ->
                        Intent().apply {
                            action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            data = Uri.fromParts("package", activity.packageName, null)
                        }.also {
                            try {
                                activity.startActivity(it)
                            } catch (_: Exception) {
                                activity.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", activity.packageName, null)
                                    )
                                )
                            }
                        }
                    }
                    .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                        Snackbar.make(
                            rootView,
                            activity.getString(R.string.exact_alarm_not_granted),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    .show()
            }
        }
    }
}

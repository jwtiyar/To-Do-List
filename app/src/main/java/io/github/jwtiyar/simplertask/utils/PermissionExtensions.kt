package io.github.jwtiyar.simplertask.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import android.content.pm.PackageManager
import io.github.jwtiyar.simplertask.R

// Centralized permission helper extension for activities.
fun AppCompatActivity.requestPermissionCompat(
    permission: String,
    rationale: String? = null,
    requestCode: Int,
    onGranted: () -> Unit = {},
    onDenied: () -> Unit = {}
) {
    if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
        onGranted()
    } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission) && rationale != null) {
        Snackbar.make(findViewById(android.R.id.content), rationale, Snackbar.LENGTH_INDEFINITE)
            .setAction(getString(R.string.button_grant)) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
            }.show()
    } else {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }
}

package com.justspeak.keyboard.audio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * RECORD_AUDIO is a dangerous permission. Request it from [com.justspeak.keyboard.OnboardingActivity],
 * not from the IME window — InputMethodService cannot reliably show the system
 * permission dialog on all API levels / OEMs.
 */
object MicPermission {
    const val PERMISSION: String = Manifest.permission.RECORD_AUDIO
    const val REQUEST_CODE: Int = 4101

    fun isGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun request(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(PERMISSION),
            REQUEST_CODE,
        )
    }

    /**
     * Opens application details so the user can re-enable RECORD_AUDIO
     * when the IME cannot show a permission dialog.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}


package com.shaketolight

import android.Manifest
import android.app.Activity
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat

class QuickSettingsCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val enabled = prefs.getBoolean(PREF_ENABLED, false)
        val service = Intent(this, ShakeTorchService::class.java)
        if (enabled) {
            startService(service.setAction(ShakeTorchService.ACTION_STOP))
            prefs.edit().putBoolean(PREF_ENABLED, false).apply()
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            try {
                ContextCompat.startForegroundService(this, service.setAction(ShakeTorchService.ACTION_START))
                prefs.edit().putBoolean(PREF_ENABLED, true).apply()
            } catch (_: RuntimeException) {
                prefs.edit().putBoolean(PREF_ENABLED, false).apply()
            }
        } else {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}

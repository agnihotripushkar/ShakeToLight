package com.shaketolight

import android.accessibilityservice.AccessibilityService
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import kotlin.math.sqrt

/** Shake detection service using motion sensor. This service does not inspect accessibility events or window content. */
class ShakeAccessibilityService : AccessibilityService(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var cameraManager: CameraManager
    private var flashCameraId: String? = null
    private var torchOn = false
    private var lastPeakAt = 0L
    private var firstPeakAt = 0L
    private var lastShakeAt = 0L
    private var peakCount = 0

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == flashCameraId) torchOn = enabled
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        flashCameraId = cameraManager.cameraIdList.firstOrNull { id ->
            runCatching { cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }.getOrDefault(false)
        }
        cameraManager.registerTorchCallback(mainExecutor, torchCallback)
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, true).apply()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = SystemClock.elapsedRealtime()
        if (magnitude > SHAKE_THRESHOLD && now - lastPeakAt > PEAK_GAP_MS) {
            lastPeakAt = now
            if (now - firstPeakAt > SHAKE_WINDOW_MS) { firstPeakAt = now; peakCount = 0 }
            peakCount++
            if (peakCount >= 2 && now - lastShakeAt > TOGGLE_COOLDOWN_MS) {
                lastShakeAt = now; firstPeakAt = 0; peakCount = 0
                flashCameraId?.let { id -> runCatching { cameraManager.setTorchMode(id, !torchOn) } }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopListening()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    private fun stopListening() {
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        if (::cameraManager.isInitialized) {
            runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
            if (torchOn) runCatching { flashCameraId?.let { cameraManager.setTorchMode(it, false) } }
        }
        torchOn = false
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, false).apply()
    }

    companion object {
        private const val SHAKE_THRESHOLD = 23.0
        private const val PEAK_GAP_MS = 130L
        private const val SHAKE_WINDOW_MS = 900L
        private const val TOGGLE_COOLDOWN_MS = 1_200L
    }
}

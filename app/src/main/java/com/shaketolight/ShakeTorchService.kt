package com.shaketolight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.math.sqrt

class ShakeTorchService : Service(), SensorEventListener {
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

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        flashCameraId = cameraManager.cameraIdList.firstOrNull { id ->
            runCatching { cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }.getOrDefault(false)
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            persist(false)
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null || flashCameraId == null) {
            persist(false)
            stopSelf()
            return START_NOT_STICKY
        }
        cameraManager.registerTorchCallback(mainExecutor, torchCallback)
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        persist(true)
        return START_STICKY
    }

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

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
        if (torchOn) runCatching { flashCameraId?.let { cameraManager.setTorchMode(it, false) } }
        torchOn = false
        persist(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun persist(enabled: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, enabled).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Shake detection", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while shake-to-toggle is enabled"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ShakeTorchService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle("Shake to Light is active")
            .setContentText("Shake your phone to toggle the flashlight")
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.shaketolight.action.START"
        const val ACTION_STOP = "com.shaketolight.action.STOP"
        private const val CHANNEL_ID = "shake_listener"
        private const val NOTIFICATION_ID = 41
        private const val SHAKE_THRESHOLD = 23.0
        private const val PEAK_GAP_MS = 130L
        private const val SHAKE_WINDOW_MS = 900L
        private const val TOGGLE_COOLDOWN_MS = 1_200L
    }
}

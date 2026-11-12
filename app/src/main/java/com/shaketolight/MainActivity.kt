package com.shaketolight

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

internal const val PREFS = "shake_to_light"
internal const val PREF_ENABLED = "enabled"

class MainActivity : ComponentActivity() {
    private var shakeEnabled by mutableStateOf(false)
    private var pendingEnable = false

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val cameraOk = results[Manifest.permission.CAMERA] == true || hasPermission(Manifest.permission.CAMERA)
        if (pendingEnable && cameraOk) startShakeService()
        else if (pendingEnable) shakeEnabled = false
        pendingEnable = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shakeEnabled = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false)
        if (shakeEnabled && hasPermission(Manifest.permission.CAMERA)) startShakeService()
        setContent {
            ShakeTheme {
                ShakeScreen(
                    enabled = shakeEnabled,
                    hasFlash = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) &&
                        packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER),
                    onAddTile = ::requestQuickSettingsTile,
                    onToggle = ::onToggleShake
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        shakeEnabled = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false)
    }

    private fun hasPermission(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun onToggleShake(enabled: Boolean) {
        if (!enabled) {
            startService(Intent(this, ShakeTorchService::class.java).setAction(ShakeTorchService.ACTION_STOP))
            persistEnabled(false)
            return
        }
        val missing = buildList {
            if (!hasPermission(Manifest.permission.CAMERA)) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isEmpty()) startShakeService() else {
            pendingEnable = true
            permissionRequest.launch(missing.toTypedArray())
        }
    }

    private fun startShakeService() {
        ContextCompat.startForegroundService(this, Intent(this, ShakeTorchService::class.java).setAction(ShakeTorchService.ACTION_START))
        persistEnabled(true)
    }

    private fun persistEnabled(enabled: Boolean) {
        shakeEnabled = enabled
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, enabled).apply()
    }

    private fun requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, FlashlightQuickSettingsTile::class.java),
                getString(R.string.tile_name), Icon.createWithResource(this, R.drawable.ic_shake), mainExecutor
            ) { result ->
                val message = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Shake to Light tile added"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Tile is already added"
                    else -> "Tile not added"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun ShakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF6750A4), onPrimary = Color.White, primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D), secondaryContainer = Color(0xFFE8DEF8),
        background = Color(0xFFF8F6FC), surface = Color(0xFFFFFBFE), onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EC), onSurfaceVariant = Color(0xFF49454F)
    ), content = content)
}

@Composable
private fun ShakeScreen(enabled: Boolean, hasFlash: Boolean, onAddTile: () -> Unit, onToggle: (Boolean) -> Unit) {
    val bg by animateColorAsState(if (enabled) Color(0xFFF3EEFF) else Color(0xFFF8F6FC), label = "background")
    Surface(Modifier.fillMaxSize(), color = bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEADDFF)), contentAlignment = Alignment.Center) { Text("✦", color = Color(0xFF6750A4), fontSize = 24.sp) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("SHAKE TO LIGHT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Your flashlight, one shake away", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.weight(0.7f))
            Box(Modifier.size(184.dp).clip(CircleShape).background(if (enabled) Color(0xFFEADDFF) else Color(0xFFEEEAF2)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp).clip(CircleShape).background(if (enabled) Color(0xFF6750A4) else Color(0xFF625B71)), contentAlignment = Alignment.Center) { Text(if (enabled) "✦" else "☼", fontSize = 58.sp, color = Color.White) }
            }
            Spacer(Modifier.height(28.dp))
            Text(if (enabled) "Ready when you are" else "Light up in a shake", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(if (!hasFlash) "This device needs an accelerometer and a camera flash."
                else if (enabled) "Shake your phone to switch the flashlight on or off."
                else "Enable shake control, then shake your phone to toggle the flashlight.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(30.dp))
            Button(onClick = { onToggle(!enabled) }, enabled = hasFlash, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(20.dp)) {
                Text(if (enabled) "Turn off shake control" else "Enable shake control", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onAddTile, modifier = Modifier.padding(top = 4.dp)) { Text("Add Quick Settings tile") }
            Spacer(Modifier.height(18.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("HOW IT WORKS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    InfoRow("〰", "Shake to toggle", "A brisk shake turns the flash on or off.")
                    InfoRow("◉", "Background listening", "An ongoing notification appears while shake control is on.")
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Requires camera access and an active notification while listening", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun InfoRow(icon: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Text(icon, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

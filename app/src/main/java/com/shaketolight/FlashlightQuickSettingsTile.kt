package com.shaketolight

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class FlashlightQuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val command = Intent(this, QuickSettingsCommandActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 71, command, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(command)
        }
        qsTile?.let { tile ->
            tile.state = if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_ENABLED, false)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            label = getString(R.string.tile_name)
            icon = Icon.createWithResource(this@FlashlightQuickSettingsTile, R.drawable.ic_shake)
            contentDescription = "Shake detection"
            state = if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_ENABLED, false)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}

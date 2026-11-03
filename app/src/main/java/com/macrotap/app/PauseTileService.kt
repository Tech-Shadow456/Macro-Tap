package com.macrotap.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class PauseTileService : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        if (MacroAccessibilityService.instance == null) {
            val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
        } else {
            PauseManager.pause(this)
        }
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 400)
    }

    private fun refresh() {
        val t = qsTile ?: return
        val on = MacroAccessibilityService.instance != null
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = if (on) "Pause Drivex" else "Paused: tap to resume"
        t.updateTile()
    }
}

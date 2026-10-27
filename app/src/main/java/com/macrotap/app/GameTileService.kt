package com.macrotap.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class GameTileService : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        if (!Settings.canDrawOverlays(this)) {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
            return
        }
        val i = Intent(this, PanelService::class.java)
        if (PanelService.running) stopService(i) else startForegroundService(i)
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 500)
    }

    private fun refresh() {
        val t = qsTile ?: return
        t.state = if (PanelService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Game Panel"
        t.updateTile()
    }
}

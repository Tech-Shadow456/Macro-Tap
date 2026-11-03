package com.macrotap.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings

object PauseManager {
    private const val NOTIF_ID = 9
    private fun prefs(c: Context) = c.getSharedPreferences("pause", Context.MODE_PRIVATE)

    fun pause(c: Context) {
        val app = c.applicationContext
        prefs(app).edit().putBoolean("paused", true).apply()
        try {
            app.startService(Intent(app, FloatingService::class.java).setAction(FloatingService.HIDE_ALL))
        } catch (e: Exception) {
        }
        try {
            app.stopService(Intent(app, PanelService::class.java))
        } catch (e: Exception) {
        }
        try {
            app.stopService(Intent(app, HudService::class.java))
        } catch (e: Exception) {
        }
        MacroAccessibilityService.instance?.pauseService()
        notify(app)
    }

    private fun notify(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("macrotap", "Drivex Play", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(
            c, 5, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(c, "macrotap")
            .setContentTitle("Drivex Play is paused")
            .setContentText("Safe to pay now. Tap to turn Accessibility back on when you're done.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pi)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    fun onAccessibilityResumed(c: Context) {
        prefs(c).edit().putBoolean("paused", false).apply()
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
    }
}

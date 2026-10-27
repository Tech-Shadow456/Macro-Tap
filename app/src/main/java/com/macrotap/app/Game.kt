package com.macrotap.app

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.provider.Settings

object Game {
    private fun prefs(c: Context) = c.getSharedPreferences("game", Context.MODE_PRIVATE)
    private fun nm(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun hasDndAccess(c: Context): Boolean = nm(c).isNotificationPolicyAccessGranted

    fun notifBlocked(c: Context): Boolean {
        if (!hasDndAccess(c)) return false
        val f = nm(c).currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL &&
            f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    fun setNotifBlocked(c: Context, on: Boolean): Boolean {
        if (!hasDndAccess(c)) return false
        nm(c).setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_ALARMS
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return true
    }

    fun callsBlocked(c: Context): Boolean = prefs(c).getBoolean("block_calls", false)
    fun setCallsBlocked(c: Context, on: Boolean) = prefs(c).edit().putBoolean("block_calls", on).apply()
    fun gameMode(c: Context): Boolean = prefs(c).getBoolean("gamemode", false)
    fun setGameMode(c: Context, on: Boolean) = prefs(c).edit().putBoolean("gamemode", on).apply()

    fun hasCallRole(c: Context): Boolean {
        val rm = c.getSystemService(Context.ROLE_SERVICE) as RoleManager
        return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun getBrightness(c: Context): Int = try {
        Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (e: Exception) {
        128
    }

    fun setBrightness(c: Context, v: Int) {
        try {
            Settings.System.putInt(
                c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceIn(1, 255))
        } catch (e: Exception) {
        }
    }

    fun isAuto(c: Context): Boolean = try {
        Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    } catch (e: Exception) {
        false
    }

    fun setAuto(c: Context, on: Boolean) {
        try {
            Settings.System.putInt(
                c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        } catch (e: Exception) {
        }
    }
}

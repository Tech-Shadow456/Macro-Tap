package com.macrotap.app

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast

object Alerts {
    private fun prefs(c: Context) = c.getSharedPreferences("alerts", Context.MODE_PRIVATE)
    private var last = 0L

    fun enabled(c: Context): Boolean = prefs(c).getBoolean("on", true)
    fun setEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean("on", v).apply()
    fun limit(c: Context): Int = prefs(c).getInt("limit", 43)
    fun setLimit(c: Context, v: Int) = prefs(c).edit().putInt("limit", v).apply()

    fun buzz(c: Context) {
        try {
            val v = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200), -1))
        } catch (e: Exception) {
        }
    }

    fun check(c: Context, temp: Float) {
        if (!enabled(c)) return
        if (temp < limit(c) && Stats.thermalLevel(c) < 3) return
        val now = System.currentTimeMillis()
        if (now - last < 90_000L) return
        last = now
        buzz(c)
        Toast.makeText(
            c, "Phone is hot (${temp.toInt()}°C). Lower brightness or take a short break.",
            Toast.LENGTH_LONG
        ).show()
    }
}

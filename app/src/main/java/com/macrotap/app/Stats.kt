package com.macrotap.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import java.io.File

object Stats {
    private fun battery(c: Context): Intent? =
        c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun batteryTemp(c: Context): Float =
        (battery(c)?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

    fun batteryPct(c: Context): Int {
        val i = battery(c)
        val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (l < 0) 0 else l * 100 / s
    }

    fun charging(c: Context): Boolean {
        val st = battery(c)?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun mem(c: Context): ActivityManager.MemoryInfo {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi
    }

    fun ramPct(c: Context): Int {
        val m = mem(c)
        if (m.totalMem == 0L) return 0
        return (100 - m.availMem * 100 / m.totalMem).toInt()
    }

    fun ramText(c: Context): String {
        val m = mem(c)
        val g = 1073741824.0
        return String.format("%.1f / %.1f GB", (m.totalMem - m.availMem) / g, m.totalMem / g)
    }

    fun thermalLevel(c: Context): Int {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.currentThermalStatus
    }

    fun thermal(c: Context): String = when (thermalLevel(c)) {
        0 -> "Normal"
        1 -> "Light"
        2 -> "Moderate"
        3 -> "Severe"
        4 -> "Critical"
        5 -> "Emergency"
        else -> "Shutdown"
    }

    fun cpuFreqs(): List<Int> {
        val out = mutableListOf<Int>()
        val n = Runtime.getRuntime().availableProcessors()
        for (i in 0 until n) {
            try {
                val s = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim()
                out.add(s.toInt() / 1000)
            } catch (e: Exception) {
            }
        }
        return out
    }
}

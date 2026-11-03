package com.macrotap.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UsageTimer {
    const val DAILY_LIMIT_MS = 60 * 60 * 1000L
    private fun prefs(c: Context) = c.getSharedPreferences("usage", Context.MODE_PRIVATE)
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun usedToday(c: Context): Long {
        val p = prefs(c)
        if (p.getString("day", "") != today()) {
            p.edit().putString("day", today()).putLong("ms", 0L).apply()
            return 0L
        }
        return p.getLong("ms", 0L)
    }

    fun remainingMs(c: Context): Long {
        if (License.isPro(c)) return Long.MAX_VALUE
        return (DAILY_LIMIT_MS - usedToday(c)).coerceAtLeast(0L)
    }

    fun remainingMinutes(c: Context): Int = (remainingMs(c) / 60000L).toInt()

    fun hasTimeLeft(c: Context): Boolean = License.isPro(c) || remainingMs(c) > 0L

    private fun addUsed(c: Context, ms: Long) {
        if (License.isPro(c)) return
        val p = prefs(c)
        if (p.getString("day", "") != today()) {
            p.edit().putString("day", today()).putLong("ms", ms.coerceAtLeast(0L)).apply()
        } else {
            val cur = p.getLong("ms", 0L)
            p.edit().putLong("ms", (cur + ms).coerceAtLeast(0L)).apply()
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var onOut: (() -> Unit)? = null
    private val tick = object : Runnable {
        override fun run() {
            val c = ctx ?: return
            if (License.isPro(c)) return
            addUsed(c, 1000L)
            if (remainingMs(c) <= 0L) {
                onOut?.invoke()
                return
            }
            h.postDelayed(this, 1000L)
        }
    }

    fun start(c: Context, onRunOut: () -> Unit) {
        ctx = c.applicationContext
        onOut = onRunOut
        if (License.isPro(c)) return
        if (!hasTimeLeft(c)) {
            onRunOut()
            return
        }
        h.removeCallbacks(tick)
        h.postDelayed(tick, 1000L)
    }

    fun stop() {
        h.removeCallbacks(tick)
    }
}

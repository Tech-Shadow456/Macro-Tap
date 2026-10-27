package com.macrotap.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

object Boost {
    private fun avail(c: Context): Long {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem
    }

    fun run(c: Context, done: (Int) -> Unit) {
        val app = c.applicationContext
        val before = avail(app)
        Thread {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            for (a in app.packageManager.queryIntentActivities(i, 0)) {
                val pkg = a.activityInfo.packageName
                if (pkg != app.packageName) {
                    try { am.killBackgroundProcesses(pkg) } catch (e: Exception) {}
                }
            }
            try { Thread.sleep(800) } catch (e: Exception) {}
            val mb = ((avail(app) - before) / 1048576L).toInt().coerceAtLeast(0)
            Handler(Looper.getMainLooper()).post { done(mb) }
        }.start()
    }
}

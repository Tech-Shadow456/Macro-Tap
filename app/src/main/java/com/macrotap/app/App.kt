package com.macrotap.app

import android.app.Application
import android.util.Log

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val w = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(w))
                getSharedPreferences("crash", MODE_PRIVATE).edit()
                    .putString("last", w.toString()).apply()
            } catch (x: Exception) {
            }
            old?.uncaughtException(t, e)
        }
        try {
            Themes.apply(this)
        } catch (e: Exception) {
            Log.e("DrivexPlay", "theme apply failed", e)
        }
    }
}

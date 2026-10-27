package com.macrotap.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

class HudService : Service() {
    companion object {
        var running = false
    }

    private lateinit var wm: WindowManager
    private var tv: TextView? = null
    private val h = Handler(Looper.getMainLooper())
    private var frames = 0
    private var fps = 0

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frames++
            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            fps = frames
            frames = 0
            update()
            h.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (tv == null) {
            createHud()
            running = true
            frames = 0
            Choreographer.getInstance().postFrameCallback(frameCb)
            h.post(tick)
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("macrotap", "Drivex Play", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "macrotap")
            .setContentTitle("Drivex Play monitor is on")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(3, n)
        }
    }

    private fun update() {
        val t = Stats.batteryTemp(this)
        val ts = String.format("%.0f", t)
        val ram = Stats.ramPct(this)
        val b = Stats.batteryPct(this)
        tv?.text = "$fps Hz  |  $ts°C  |  RAM $ram%  |  🔋$b%"
        tv?.setTextColor(if (t >= 43f) C.DANGER else C.ACCENT)
    }

    private fun createHud() {
        val v = TextView(this)
        v.textSize = 12f
        v.typeface = Typeface.MONOSPACE
        v.setTextColor(C.ACCENT)
        v.setPadding(dp(12), dp(6), dp(12), dp(6))
        v.background = rounded(android.graphics.Color.parseColor("#CC0B0E14"), dp(14).toFloat(), C.LINE)
        v.text = "..."
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = 20
        p.y = 60
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = sx + (e.rawX - tx).toInt()
                    p.y = sy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(v, p)
                }
            }
            true
        }
        wm.addView(v, p)
        tv = v
    }

    override fun onDestroy() {
        running = false
        h.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(frameCb)
        tv?.let { wm.removeView(it) }
        tv = null
        super.onDestroy()
    }
}

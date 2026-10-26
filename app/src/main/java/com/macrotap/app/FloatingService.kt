package com.macrotap.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Toast
import kotlin.math.abs

class FloatingService : Service() {
    private var wm: WindowManager? = null
    private var btn: ShapeButtonView? = null
    private var lp: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        val shape = intent?.getIntExtra("shape", 0) ?: 0
        val sizeDp = intent?.getIntExtra("size", 80) ?: 80
        val px = (sizeDp * resources.displayMetrics.density).toInt()
        if (btn == null) createButton(px)
        btn!!.shape = shape
        btn!!.invalidate()
        lp!!.width = px
        lp!!.height = px
        wm!!.updateViewLayout(btn, lp)
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("macrotap", "MacroTap", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "macrotap")
            .setContentTitle("MacroTap is running")
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun createButton(px: Int) {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = ShapeButtonView(this)
        val p = WindowManager.LayoutParams(
            px, px,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = 100
        p.y = 300
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    if (moved) {
                        p.x = sx + dx
                        p.y = sy + dy
                        wm!!.updateViewLayout(v, p)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) Toast.makeText(this, "Tapped", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        wm!!.addView(v, p)
        btn = v
        lp = p
    }

    override fun onDestroy() {
        btn?.let { wm?.removeView(it) }
        btn = null
        super.onDestroy()
    }
}

package com.macrotap.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlin.math.abs

class PanelService : Service() {
    companion object {
        var running = false
    }

    private lateinit var wm: WindowManager
    private val h = Handler(Looper.getMainLooper())
    private var handle: View? = null
    private var panel: View? = null
    private var stats: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (handle == null) createHandle()
        running = true
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("macrotap", "Drivex Play", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "macrotap")
            .setContentTitle("Drivex Play game panel is on")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(4, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(4, n)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun createHandle() {
        val v = View(this)
        v.background = rounded(Color.parseColor("#CC00E5A0"), dp(8).toFloat())
        val p = WindowManager.LayoutParams(
            dp(14), dp(72), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = 0
        p.y = dp(160)
        var sy = 0
        var ty = 0f
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sy = p.y; ty = e.rawY; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
val dy = (e.rawY - ty).toInt()
                    if (abs(dy) > 12) moved = true
                    if (moved) {
                        p.y = sy + dy
                        wm.updateViewLayout(v, p)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (panel == null) openPanel() else closePanel()
                    }
                }
            }
            true
        }
        wm.addView(v, p)
        handle = v
    }

    private fun openPanel() {
        if (panel != null) return
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), dp(14), dp(14), dp(14))

        text(col, "Drivex Play", 20f, C.TEXT, true)
        val st = text(col, "...", 12f, C.ACCENT, false, 2)
        st.typeface = Typeface.MONOSPACE
        stats = st

        var swN: Switch? = null
        swN = switchRow(col, "Block notifications", "", Game.notifBlocked(this)) { v ->
            if (!Game.setNotifBlocked(this, v)) {
                toast("Allow Do Not Disturb in Drivex Play first")
                swN?.isChecked = false
            }
        }
        var swC: Switch? = null
        swC = switchRow(col, "Block calls", "", Game.callsBlocked(this)) { v ->
            if (v && !Game.hasCallRole(this)) {
                toast("Set up call blocking in Drivex Play first")
                swC?.isChecked = false
            } else {
                Game.setCallsBlocked(this, v)
            }
        }
        switchRow(col, "Floating monitor", "", HudService.running) { v ->
            if (v) startForegroundService(Intent(this, HudService::class.java))
            else stopService(Intent(this, HudService::class.java))
        }

        if (android.provider.Settings.System.canWrite(this)) {
            slider(col, "Brightness", 1, 255, Game.getBrightness(this), { "${it * 100 / 255}%" }) {
                Game.setBrightness(this, it)
            }
        } else {
            text(col, "Brightness: grant it in Drivex Play > Home", 12f, C.SUB, false, 12)
        }
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        slider(
            col, "Media volume", 0, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            am.getStreamVolume(AudioManager.STREAM_MUSIC), { it.toString() }
        ) {
            try { am.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) } catch (e: SecurityException) {}
        }

        val macros = Store.all(this)
        if (macros.isNotEmpty()) {
            text(col, "Macro buttons", 14f, C.SUB, true, 14)
            for (m in macros) {
                switchRow(col, m.name, "", FloatingService.shown.contains(m.id)) { v ->
                    startForegroundService(
                        Intent(this, FloatingService::class.java)
                            .setAction(if (v) FloatingService.SHOW else FloatingService.HIDE)
                            .putExtra("id", m.id)
                    )
                }
            }
        }

        val r = row(col)
        button(r, "⚡ Boost", true, true) {
            toast("Boosting...")
            Boost.run(this) { mb -> toast("Freed about $mb MB") }
        }
        button(r, "● Rec", false, true) {
            closePanel()
            startActivity(Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        button(r, "■ Stop", false, true) {
            startService(Intent(this, RecorderService::class.java).setAction("STOP"))
        }
        button(col, "Close", false) { closePanel() }

        val sv = ScrollView(this)
        sv.background = rounded(Color.parseColor("#F20B0E14"), dp(18).toFloat(), C.LINE)
        sv.addView(col)

        val w = dp(300)
        col.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val screenH = resources.displayMetrics.heightPixels
        val height = minOf(col.measuredHeight + dp(4), (screenH * 0.9f).toInt())
        val p = WindowManager.LayoutParams(
            w, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = dp(20)
        p.y = (screenH - height) / 2
        wm.addView(sv, p)
        panel = sv
        tick.run()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (panel == null) return
            val t = Stats.batteryTemp(this@PanelService)
            val hz = wm.defaultDisplay.refreshRate
            stats?.text = "${hz.toInt()} Hz | ${t.toInt()}°C | RAM ${Stats.ramPct(this@PanelService)}% | 🔋${Stats.batteryPct(this@PanelService)}%"
            stats?.setTextColor(if (t >= 43f) C.DANGER else C.ACCENT)
            h.postDelayed(this, 1000)
        }
    }

    private fun closePanel() {
        h.removeCallbacks(tick)
        panel?.let { wm.removeView(it) }
        panel = null
        stats = null
    }

    override fun onDestroy() {
        running = false
        closePanel()
        handle?.let { wm.removeView(it) }
        handle = null
        super.onDestroy()
    }
}

package com.macrotap.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import kotlin.math.abs
import kotlin.math.hypot

class FloatingService : Service() {
    companion object {
        const val SHOW = "show"
        const val HIDE = "hide"
        const val RECORD = "record"
        val shown = mutableSetOf<Long>()
    }

    private lateinit var wm: WindowManager
    private val views = HashMap<Long, ShapeButtonView>()
    private var panel: View? = null
    private var addBtn: Button? = null
    private var capture: View? = null
    private var rec: MutableList<Step>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        val id = intent?.getLongExtra("id", 0L) ?: 0L
        when (intent?.action) {
            SHOW -> show(id)
            HIDE -> hide(id)
            RECORD -> startRecording()
        }
        idleCheck()
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("macrotap", "MacroTap", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "macrotap")
            .setContentTitle("MacroTap buttons are active")
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun idleCheck() {
        if (views.isEmpty() && panel == null && capture == null) stopSelf()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun overlayParams(w: Int, h: Int, flags: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
        )
    }

    // ---------- floating macro buttons ----------

    private fun removeButton(id: Long) {
        views.remove(id)?.let { wm.removeView(it) }
        shown.remove(id)
    }

    private fun hide(id: Long) {
        removeButton(id)
    }

    private fun show(id: Long) {
        val m = Store.get(this, id) ?: return
        removeButton(id)
        val px = (m.size * resources.displayMetrics.density).toInt()
        val v = ShapeButtonView(this)
        v.shape = m.shape
        v.color = m.color
        v.label = m.name.take(2)
        val p = overlayParams(
            px, px,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = m.px
        p.y = m.py
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        var ok = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ok = v.contains(e.x, e.y)
                    if (ok) {
                        sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (ok) {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 15 || abs(dy) > 15) moved = true
                        if (moved) {
                            p.x = sx + dx
                            p.y = sy + dy
                            wm.updateViewLayout(v, p)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (ok) {
                        if (moved) Store.move(this, id, p.x, p.y) else runMacro(id)
                    }
                }
            }
            ok
        }
        wm.addView(v, p)
        views[id] = v
        shown.add(id)
    }

    private fun runMacro(id: Long) {
        val m = Store.get(this, id) ?: return
        val s = MacroAccessibilityService.instance
        if (s == null) {
            Toast.makeText(this, "Turn on MacroTap in Accessibility settings first", Toast.LENGTH_LONG).show()
        } else {
            s.toggle(m)
        }
    }

    // ---------- recording a new macro ----------

    private fun startRecording() {
        if (panel != null) return
        rec = mutableListOf()
        val l = LinearLayout(this)
        l.orientation = LinearLayout.HORIZONTAL
        l.setBackgroundColor(Color.parseColor("#CC222222"))
        l.setPadding(16, 16, 16, 16)
        val add = Button(this)
        add.text = "+ Step (0)"
        add.setOnClickListener { beginCapture() }
        val done = Button(this)
        done.text = "Done"
        done.setOnClickListener { finishRecording() }
        val cancel = Button(this)
        cancel.text = "Cancel"
        cancel.setOnClickListener {
            closeRecorderUi()
            idleCheck()
        }
        l.addView(add)
        l.addView(done)
        l.addView(cancel)
        val p = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.y = 100
        wm.addView(l, p)
        panel = l
        addBtn = add
        toast("Open your target app, then tap + Step")
    }

    private fun beginCapture() {
        if (capture != null) return
        val f = FrameLayout(this)
        f.setBackgroundColor(0x55000000)
        val t = TextView(this)
        t.text = "Touch where this step should happen\n(tap = tap, drag = swipe)"
        t.setTextColor(Color.WHITE)
        t.textSize = 18f
        t.gravity = Gravity.CENTER
        f.addView(
            t,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            )
        )
        var dx = 0f
        var dy = 0f
        f.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX
                    dy = e.rawY
                }
                MotionEvent.ACTION_UP -> {
                    val ux = e.rawX
                    val uy = e.rawY
                    val step = if (hypot(ux - dx, uy - dy) > 40f) {
                        Step(1, dx, dy, ux, uy, 100)
                    } else {
                        Step(0, dx, dy, dx, dy, 100)
                    }
                    rec?.add(step)
                    addBtn?.text = "+ Step (${rec?.size ?: 0})"
                    endCapture()
                }
            }
            true
        }
        val p = overlayParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        wm.addView(f, p)
        capture = f
    }

    private fun endCapture() {
        capture?.let { wm.removeView(it) }
        capture = null
    }

    private fun closeRecorderUi() {
        endCapture()
        panel?.let { wm.removeView(it) }
        panel = null
        addBtn = null
        rec = null
    }

    private fun finishRecording() {
        val steps = rec ?: return
        if (steps.isEmpty()) {
            toast("Add at least one step first")
            return
        }
        val n = Store.all(this).size
        val m = Macro(
            System.currentTimeMillis(), "Macro ${n + 1}", 0, 80, Color.parseColor("#E64A19"),
            1, 100 + 140 * (n % 5), 400, steps
        )
        Store.put(this, m)
        closeRecorderUi()
        show(m.id)
        toast("Saved. Open MacroTap to edit delays, shape and size")
        idleCheck()
    }

    override fun onDestroy() {
        closeRecorderUi()
        for (v in views.values) wm.removeView(v)
        views.clear()
        shown.clear()
        super.onDestroy()
    }
}

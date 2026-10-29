package com.macrotap.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private var tab = 0
    private val h = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null
    private val names = listOf("Home", "Macros", "Tools", "Monitor", "Record")
    private val icons = listOf("🏠", "⚡", "🎛", "📊", "🎥")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.BG
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(C.BG)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
        nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(C.CARD)
        nav.setPadding(0, dp(8), 0, dp(8))
        root.addView(nav, LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onPause() {
        super.onPause()
        stopTicker()
    }

    private fun stopTicker() {
        ticker?.let { h.removeCallbacks(it) }
        ticker = null
    }

    private fun buildNav() {
        nav.removeAllViews()
        for (i in names.indices) {
            val t = TextView(this)
            t.text = icons[i] + "\n" + names[i]
            t.gravity = Gravity.CENTER
            t.textSize = 11f
            t.setTextColor(if (i == tab) C.ACCENT else C.SUB)
            t.setOnClickListener {
                tab = i
                content.removeAllViews()
                render()
            }
            nav.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
    }

    private fun render() {
        stopTicker()
        val old = content.getChildAt(0) as? ScrollView
        val y = old?.scrollY ?: 0
        buildNav()
        content.removeAllViews()
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(20), dp(16), dp(24))
        sv.addView(col)
        content.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
        when (tab) {
            0 -> home(col)
            1 -> macros(col)
            2 -> tools(col)
            3 -> monitor(col)
            else -> recorder(col)
        }
        sv.post { sv.scrollTo(0, y) }
    }

    private fun svc(action: String, id: Long = 0L) {
        startForegroundService(
            Intent(this, FloatingService::class.java).setAction(action).putExtra("id", id)
        )
    }

    private fun overlayIntent() =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ================= HOME =================
    private fun home(col: LinearLayout) {
        text(col, "Drivex Play", 30f, C.TEXT, true)
        text(col, "Game assistant", 14f, C.SUB)

        val gp = card(col)
        switchRow(gp, "Game Panel", "Edge handle over any app. Also in Quick Settings.", PanelService.running) { v ->
            if (v) {
                if (Settings.canDrawOverlays(this)) startForegroundService(Intent(this, PanelService::class.java))
                else toast("Allow overlay permission first")
            } else {
                stopService(Intent(this, PanelService::class.java))
            }
            h.postDelayed({ render() }, 300)
        }
        button(gp, "⚡ Boost now") {
            Boost.run(this) { mb -> toast("Boost done: freed about $mb MB") }
        }

        val g = card(col)
        switchRow(
            g, "Game Mode",
            "Boost, block notifications and calls, show the live monitor",
            Game.gameMode(this)
        ) { gameMode(it) }

        val p = card(col)
        text(p, "Setup", 18f, C.TEXT, true)
        permRow(p, "Display over other apps", Settings.canDrawOverlays(this)) {
            startActivity(overlayIntent())
        }
        permRow(p, "Accessibility (macro taps)", MacroAccessibilityService.instance != null) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        permRow(p, "Do Not Disturb access", Game.hasDndAccess(this)) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        permRow(p, "Change brightness", Settings.System.canWrite(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        permRow(p, "Call blocking", Game.hasCallRole(this)) { requestCallRole() }
        permRow(
            p, "Keep running in background",
            (getSystemService(POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(packageName)
        ) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }

        text(col, "No internet permission: nothing leaves your phone.", 12f, C.SUB, false, 16)
    }

    private fun gameMode(on: Boolean) {
        val notes = ArrayList<String>()
        if (on) {
            if (!Game.setNotifBlocked(this, true)) notes.add("Allow Do Not Disturb access to block notifications")
            if (Game.hasCallRole(this)) {
                Game.setCallsBlocked(this, true)
            } else {
                notes.add("Set Drivex Play as call screening app to block calls")
            }
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, HudService::class.java))
                startForegroundService(Intent(this, PanelService::class.java))
            } else {
                notes.add("Allow overlay permission to show the monitor")
            }
            Boost.run(this) { mb -> toast("Boost done: freed about $mb MB") }
        } else {
            Game.setNotifBlocked(this, false)
            Game.setCallsBlocked(this, false)
            stopService(Intent(this, HudService::class.java))
        }
        Game.setGameMode(this, on)
        if (notes.isNotEmpty()) toast(notes.joinToString("\n"))
        h.postDelayed({ render() }, 300)
    }

    private fun requestCallRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            toast("Call blocking is not available on this phone")
            return
        }
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 7)
    }

    // ================= MACROS =================
    private fun macros(col: LinearLayout) {
        text(col, "Macros", 28f, C.TEXT, true)
        text(col, "Floating buttons that tap for you", 14f, C.SUB)

        if (MacroAccessibilityService.instance == null) {
            val w = card(col)
            text(w, "Accessibility is off", 16f, C.WARN, true)
            text(w, "Macros need it to perform taps.", 13f, C.SUB, false, 4)
            button(w, "Open settings", false) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        button(col, "+  Record new macro") {
            if (!Settings.canDrawOverlays(this)) {
                toast("Allow overlay permission first (Home tab)")
            } else {
                svc(FloatingService.RECORD)
                moveTaskToBack(true)
            }
        }

        val list = Store.all(this)
        if (list.isEmpty()) {
            text(col, "No macros yet.", 14f, C.SUB, false, 16)
        }
        val shapes = listOf("Circle", "Square", "Triangle")
        for (m in list) {
            val c = card(col)
            text(c, m.name, 18f, C.TEXT, true)
            val rep = if (m.repeat == 0) "forever" else "${m.repeat}x"
            text(c, "${m.steps.size} steps · repeat $rep · ${shapes[m.shape.coerceIn(0, 2)]}", 13f, C.SUB, false, 2)
            val r = row(c)
            val on = FloatingService.shown.contains(m.id)
            button(r, if (on) "Hide" else "Show", true, true) {
                svc(if (on) FloatingService.HIDE else FloatingService.SHOW, m.id)
                h.postDelayed({ render() }, 400)
            }
            button(r, "Edit", false, true) { edit(m) }
            button(r, "Delete", false, true) { confirmDelete(m) }
        }

        val tip = card(col)
        text(tip, "Tip", 14f, C.ACCENT, true)
        text(
            tip,
            "Floating buttons are locked. Hold one for 5 seconds until it buzzes, drag it, then let go to lock it again.",
            13f, C.SUB, false, 4
        )
    }

    private fun confirmDelete(m: Macro) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${m.name}?")
            .setPositiveButton("Delete") { _, _ ->
                svc(FloatingService.HIDE, m.id)
                Store.delete(this, m.id)
                h.postDelayed({ render() }, 400)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun edit(m: Macro) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(40, 20, 40, 0)

        val name = EditText(this)
        name.setText(m.name)
        name.hint = "Name"
        box.addView(name)

        val rep = EditText(this)
        rep.inputType = InputType.TYPE_CLASS_NUMBER
        rep.setText(m.repeat.toString())
        rep.hint = "Repeat count (0 = forever)"
        box.addView(rep)

        val shapeSp = Spinner(this)
        shapeSp.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, listOf("Circle", "Square", "Triangle")
        )
        shapeSp.setSelection(m.shape)
        box.addView(shapeSp)

        val colors = listOf(
            "Green" to "#00E5A0", "Orange" to "#E64A19", "Blue" to "#1976D2",
            "Purple" to "#7B1FA2", "Red" to "#D32F2F", "Black" to "#212121"
        )
        val colorSp = Spinner(this)
        colorSp.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, colors.map { it.first }
        )
        val ci = colors.indexOfFirst { Color.parseColor(it.second) == m.color }
        colorSp.setSelection(if (ci >= 0) ci else 0)
        box.addView(colorSp)

        val sizeLabel = TextView(this)
        sizeLabel.text = "Size: ${m.size}"
        box.addView(sizeLabel)
        val bar = SeekBar(this)
        bar.max = 160
        bar.progress = m.size - 40
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                sizeLabel.text = "Size: ${p + 40}"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(bar)

        val opLabel = TextView(this)
        opLabel.text = "Opacity: ${m.opacity}%"
        box.addView(opLabel)
        val opBar = SeekBar(this)
        opBar.max = 80
        opBar.progress = (m.opacity - 20).coerceIn(0, 80)
        opBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                opLabel.text = "Opacity: ${p + 20}%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(opBar)

        val hdr = TextView(this)
        hdr.text = "Steps (wait in ms after each step)"
        hdr.setPadding(0, 24, 0, 8)
        box.addView(hdr)

        val rows = mutableListOf<Pair<Step, EditText>>()
        for ((i, s) in m.steps.withIndex()) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            val t = TextView(this)
            val kind = if (s.type == 1) "Swipe" else "Tap"
            t.text = "${i + 1}. $kind (${s.x.toInt()}, ${s.y.toInt()})"
            r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val d = EditText(this)
            d.inputType = InputType.TYPE_CLASS_NUMBER
            d.setText(s.delay.toString())
            r.addView(d, LinearLayout.LayoutParams(220, ViewGroup.LayoutParams.WRAP_CONTENT))
            val x = Button(this)
            x.text = "✕"
            x.setOnClickListener {
                rows.removeAll { it.first === s }
                box.removeView(r)
            }
            r.addView(x)
            box.addView(r)
            rows.add(Pair(s, d))
        }

        val sv = ScrollView(this)
        sv.addView(box)
        AlertDialog.Builder(this)
            .setTitle("Edit macro")
            .setView(sv)
            .setPositiveButton("Save") { _, _ ->
                m.name = name.text.toString().ifBlank { m.name }
                m.repeat = rep.text.toString().toIntOrNull() ?: 1
                m.shape = shapeSp.selectedItemPosition
                m.color = Color.parseColor(colors[colorSp.selectedItemPosition].second)
                m.size = bar.progress + 40
                m.opacity = opBar.progress + 20
                val newSteps = mutableListOf<Step>()
                for ((s, e) in rows) {
                    s.delay = e.text.toString().toLongOrNull() ?: 100L
                    newSteps.add(s)
                }
                m.steps = newSteps
                Store.put(this, m)
                if (FloatingService.shown.contains(m.id)) svc(FloatingService.SHOW, m.id)
                h.postDelayed({ render() }, 400)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================= TOOLS =================
    private fun tools(col: LinearLayout) {
        text(col, "Tools", 28f, C.TEXT, true)
        text(col, "Control your phone while gaming", 14f, C.SUB)

        val n = card(col)
        text(n, "Interruptions", 18f, C.TEXT, true)
        switchRow(n, "Block notifications", "Do Not Disturb, alarms still ring", Game.notifBlocked(this)) { v ->
            if (!Game.setNotifBlocked(this, v)) {
                toast("Allow Do Not Disturb access first")
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                h.postDelayed({ render() }, 300)
            }
        }
        switchRow(n, "Block incoming calls", "Rejects calls automatically", Game.callsBlocked(this)) { v ->
            if (v && !Game.hasCallRole(this)) {
                requestCallRole()
            } else {
                Game.setCallsBlocked(this, v)
            }
        }

        val b = card(col)
        text(b, "Brightness", 18f, C.TEXT, true)
        if (!Settings.System.canWrite(this)) {
            text(b, "Needs permission to change system settings.", 13f, C.SUB, false, 4)
            button(b, "Grant permission", false) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
        } else {
            switchRow(b, "Auto brightness", "", Game.isAuto(this)) { Game.setAuto(this, it) }
            slider(b, "Level", 1, 255, Game.getBrightness(this), { "${it * 100 / 255}%" }) {
                Game.setBrightness(this, it)
            }
        }

        val s = card(col)
        text(s, "Sound", 18f, C.TEXT, true)
        volSlider(s, "Media", AudioManager.STREAM_MUSIC)
        volSlider(s, "Ringtone", AudioManager.STREAM_RING)
        volSlider(s, "Alarm", AudioManager.STREAM_ALARM)
    }

    private fun volSlider(parent: LinearLayout, t: String, stream: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        slider(parent, t, 0, am.getStreamMaxVolume(stream), am.getStreamVolume(stream), { it.toString() }) { v ->
            try {
                am.setStreamVolume(stream, v, 0)
            } catch (e: SecurityException) {
            }
        }
    }

    // ================= MONITOR =================
    private fun monitor(col: LinearLayout) {
        text(col, "Monitor", 28f, C.TEXT, true)
        text(col, "Phone health, live", 14f, C.SUB)

        val c = card(col)
        val health = text(c, "", 24f, C.ACCENT, true)
        val det = text(c, "", 14f, C.TEXT, false, 12)
        det.setLineSpacing(0f, 1.35f)

        val upd = object : Runnable {
            override fun run() {
                val ctx = this@MainActivity
                val t = Stats.batteryTemp(ctx)
                val lvl = Stats.thermalLevel(ctx)
                val hot = t >= 43f || lvl >= 3
                val warm = t >= 39f || lvl >= 2
                health.text = if (hot) "Hot: take a break" else if (warm) "Warm" else "Healthy"
                health.setTextColor(if (hot) C.DANGER else if (warm) C.WARN else C.ACCENT)
                val f = Stats.cpuFreqs()
                val cpu = if (f.isEmpty()) "not readable on this phone" else f.joinToString(" / ")
                val hz = windowManager.defaultDisplay.refreshRate
                val chg = if (Stats.charging(ctx)) " (charging)" else ""
                det.text = "Battery temp: ${String.format("%.1f", t)} °C\n" +
                    "Thermal state: ${Stats.thermal(ctx)}\n" +
                    "Battery: ${Stats.batteryPct(ctx)}%$chg\n" +
                    "RAM: ${Stats.ramText(ctx)} (${Stats.ramPct(ctx)}%)\n" +
                    "Screen refresh: ${String.format("%.0f", hz)} Hz\n" +
                    "CPU MHz per core: $cpu"
                h.postDelayed(this, 1000)
            }
        }
        ticker = upd
        upd.run()

        val m = card(col)
        switchRow(
            m, "Floating monitor", "Hz, temperature, RAM and battery over any app",
            HudService.running
        ) { v ->
            if (v) {
                if (Settings.canDrawOverlays(this)) {
                    startForegroundService(Intent(this, HudService::class.java))
                } else {
                    toast("Allow overlay permission first (Home tab)")
                    h.postDelayed({ render() }, 300)
                }
            } else {
                stopService(Intent(this, HudService::class.java))
            }
        }
        text(
            m,
            "The Hz value is your screen's live frame rate. Android does not let apps read another app's FPS without root or Shizuku.",
            12f, C.SUB, false, 10
        )
    }

    // ================= RECORD =================
    private fun recorder(col: LinearLayout) {
        text(col, "Record", 28f, C.TEXT, true)
        text(col, "Screen + system audio", 14f, C.SUB)
        val c = card(col)
        text(c, "Videos are saved in Movies/DrivexPlay.", 13f, C.SUB)
        button(c, "Start recording") { startRec() }
        button(c, "Stop recording", false) {
            startService(Intent(this, RecorderService::class.java).setAction("STOP"))
        }
        text(c, "Some apps block their audio from being captured.", 12f, C.SUB, false, 12)
    }

    private fun startRec() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            toast("Allow, then tap Start recording again")
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), 42)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, RecorderService::class.java)
            i.putExtra("code", resultCode)
            i.putExtra("data", data)
            startForegroundService(i)
            moveTaskToBack(true)
        }
        if (requestCode == 7) {
            if (Game.hasCallRole(this)) Game.setCallsBlocked(this, true)
            render()
        }
    }
}

package com.macrotap.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(40, 80, 40, 80)
        sv.addView(list)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun label(t: String, size: Float = 16f): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setPadding(0, 24, 0, 8)
        list.addView(v)
        return v
    }

    private fun btn(parent: LinearLayout, t: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.setOnClickListener { onClick() }
        val h = parent.orientation == LinearLayout.HORIZONTAL
        parent.addView(
            b,
            LinearLayout.LayoutParams(
                if (h) 0 else ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (h) 1f else 0f
            )
        )
        return b
    }

    private fun svc(action: String, id: Long = 0L) {
        startForegroundService(
            Intent(this, FloatingService::class.java).setAction(action).putExtra("id", id)
        )
    }

    private fun render() {
        list.removeAllViews()
        label("MacroTap", 26f)
        val overlayOk = Settings.canDrawOverlays(this)
        val accOk = MacroAccessibilityService.instance != null
        label(
            (if (overlayOk) "✔" else "✘") + " Overlay permission     " +
                (if (accOk) "✔" else "✘") + " Accessibility"
        )
        btn(list, "1. Allow display over other apps") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        btn(list, "2. Turn on MacroTap accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        label("Your macros", 20f)
        val macros = Store.all(this)
        if (macros.isEmpty()) label("None yet. Tap Record below.")
        for (m in macros) {
            val rep = if (m.repeat == 0) "forever" else m.repeat.toString()
            label("${m.name}: ${m.steps.size} steps, repeat $rep")
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            list.addView(row)
            val on = FloatingService.shown.contains(m.id)
            btn(row, if (on) "Hide" else "Show") {
                svc(if (on) FloatingService.HIDE else FloatingService.SHOW, m.id)
                list.postDelayed({ render() }, 400)
            }
            btn(row, "Edit") { edit(m) }
            btn(row, "Delete") {
                svc(FloatingService.HIDE, m.id)
                Store.delete(this, m.id)
                list.postDelayed({ render() }, 400)
            }
        }
        btn(list, "+ Record new macro") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow overlay permission first", Toast.LENGTH_LONG).show()
            } else {
                svc(FloatingService.RECORD)
                moveTaskToBack(true)
            }
        }

        label("Screen recorder (screen + system audio)", 20f)
        btn(list, "Start recording") { startRec() }
        btn(list, "Stop recording") {
            startService(Intent(this, RecorderService::class.java).setAction("STOP"))
        }
        label("Videos are saved in Movies/MacroTap.")
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
            "Orange" to "#E64A19", "Blue" to "#1976D2", "Green" to "#388E3C",
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

        val hdr = TextView(this)
        hdr.text = "Steps (wait in ms after each step)"
        hdr.setPadding(0, 24, 0, 8)
        box.addView(hdr)

        val rows = mutableListOf<Pair<Step, EditText>>()
        for ((i, s) in m.steps.withIndex()) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val t = TextView(this)
            val kind = if (s.type == 1) "Swipe" else "Tap"
            t.text = "${i + 1}. $kind (${s.x.toInt()}, ${s.y.toInt()})"
            row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val d = EditText(this)
            d.inputType = InputType.TYPE_CLASS_NUMBER
            d.setText(s.delay.toString())
            row.addView(d, LinearLayout.LayoutParams(220, ViewGroup.LayoutParams.WRAP_CONTENT))
            val x = Button(this)
            x.text = "✕"
            x.setOnClickListener {
                rows.removeAll { it.first === s }
                box.removeView(row)
            }
            row.addView(x)
            box.addView(row)
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
                val newSteps = mutableListOf<Step>()
                for ((s, e) in rows) {
                    s.delay = e.text.toString().toLongOrNull() ?: 100L
                    newSteps.add(s)
                }
                m.steps = newSteps
                Store.put(this, m)
                if (FloatingService.shown.contains(m.id)) svc(FloatingService.SHOW, m.id)
                list.postDelayed({ render() }, 400)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startRec() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val perms = if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            requestPermissions(perms, 1)
            Toast.makeText(this, "Allow, then tap Start recording again", Toast.LENGTH_LONG).show()
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
    }
}

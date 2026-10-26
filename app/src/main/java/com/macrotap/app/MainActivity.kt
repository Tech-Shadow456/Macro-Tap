package com.macrotap.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private var shape = 0
    private var size = 80

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)

        fun btn(text: String, action: () -> Unit): Button {
            val b = Button(this)
            b.text = text
            b.setOnClickListener { action() }
            root.addView(b)
            return b
        }

        btn("1. Allow display over other apps") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        btn("Shape: Circle") { shape = 0; apply() }
        btn("Shape: Square") { shape = 1; apply() }
        btn("Shape: Triangle") { shape = 2; apply() }

        val label = TextView(this)
        label.text = "Size: 80"
        root.addView(label)
        val bar = SeekBar(this)
        bar.max = 160
        bar.progress = 40
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                size = p + 40
                label.text = "Size: $size"
                if (fromUser) apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(bar)

        btn("2. Show floating button") { apply() }
        btn("Hide floating button") { stopService(Intent(this, FloatingService::class.java)) }

        setContentView(root)
    }

    private fun apply() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow overlay permission first", Toast.LENGTH_LONG).show()
            return
        }
        val i = Intent(this, FloatingService::class.java)
        i.putExtra("shape", shape)
        i.putExtra("size", size)
        startForegroundService(i)
    }
}

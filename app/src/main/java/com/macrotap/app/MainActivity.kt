package com.macrotap.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "MacroTap is working!"
        tv.textSize = 24f
        tv.setPadding(48, 96, 48, 48)
        setContentView(tv)
    }
}

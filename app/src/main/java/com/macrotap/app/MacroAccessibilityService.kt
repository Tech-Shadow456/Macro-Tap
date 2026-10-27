package com.macrotap.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class MacroAccessibilityService : AccessibilityService() {
    companion object {
        var instance: MacroAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var token = 0
    private var runningId = 0L

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopMacro()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        stopMacro()
        return super.onUnbind(intent)
    }

    fun toggle(m: Macro) {
        if (runningId == m.id) {
            stopMacro()
        } else {
            stopMacro()
            if (m.steps.isEmpty()) return
            runningId = m.id
            token++
            step(m, token, 0, 0)
        }
    }

    private fun stopMacro() {
        token++
        runningId = 0L
        handler.removeCallbacksAndMessages(null)
    }

    private fun step(m: Macro, tok: Int, round: Int, idx: Int) {
        if (tok != token) return
        if (idx >= m.steps.size) {
            if (m.repeat != 0 && round + 1 >= m.repeat) {
                runningId = 0L
                return
            }
            handler.post { step(m, tok, round + 1, 0) }
            return
        }
        val s = m.steps[idx]
        val path = Path()
        path.moveTo(s.x, s.y)
        if (s.type == 1) path.lineTo(s.x2, s.y2)
        val dur = if (s.type == 1) 300L else 25L
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, dur))
            .build()
        val next = Runnable {
            handler.postDelayed({ step(m, tok, round, idx + 1) }, s.delay)
        }
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                next.run()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                next.run()
            }
        }, null)
        if (!ok) stopMacro()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}

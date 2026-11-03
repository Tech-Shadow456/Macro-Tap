package com.macrotap.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Full-screen configuration overlay for one Circular Tap step.
 * Drag inside the circle to move it, tap outside to jump the centre there,
 * drag the dot on the edge (or use -/+ buttons) to resize.
 * Only exists while configuring; FloatingService removes it on Save/Cancel.
 */
class CircleConfigView(ctx: Context) : View(ctx) {
    var cx = 0f
    var cy = 0f
    var radius = 0f

    private val d = ctx.resources.displayMetrics.density
    private val minR = 8f * d
    private var maxR = 1000f
    private var inited = false
    private var mode = NONE
    private var offX = 0f
    private var offY = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x3300E5A0; style = Paint.Style.FILL
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5A0.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f * d
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 14f * d; textAlign = Paint.Align.CENTER
        setShadowLayer(4f * d, 0f, 0f, Color.BLACK)
    }

    init {
        setBackgroundColor(0x40000000)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    companion object {
        private const val NONE = 0
        private const val MOVE = 1
        private const val RESIZE = 2
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        maxR = min(w, h) / 2f
        if (!inited && w > 0 && h > 0) {
            cx = w / 2f
            cy = h / 2f
            radius = (60f * d).coerceIn(minR, maxR)
            inited = true
        }
    }

    fun grow(deltaPx: Float) {
        radius = (radius + deltaPx).coerceIn(minR, maxR)
        invalidate()
    }

    private fun handleX(): Float = if (cx + radius <= width - 24f * d) cx + radius else cx - radius

    override fun onDraw(c: Canvas) {
        c.drawCircle(cx, cy, radius, fill)
        c.drawCircle(cx, cy, radius, line)
        c.drawLine(cx - 12f * d, cy, cx + 12f * d, cy, line)
        c.drawLine(cx, cy - 12f * d, cx, cy + 12f * d, line)
        c.drawCircle(handleX(), cy, 10f * d, dot)
        c.drawText("Radius ${radius.toInt()} px", cx, cy - radius - 12f * d, label)
        c.drawText("Drag to move, drag the dot or use -/+ to resize", width / 2f, 96f * d, label)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hypot(e.x - handleX(), e.y - cy) <= 32f * d) {
                    mode = RESIZE
                } else {
                    mode = MOVE
                    if (hypot(e.x - cx, e.y - cy) <= radius) {
                        offX = cx - e.x
                        offY = cy - e.y
                    } else {
                        offX = 0f; offY = 0f
                        cx = e.x.coerceIn(0f, width.toFloat())
                        cy = e.y.coerceIn(0f, height.toFloat())
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == RESIZE) {
                    radius = hypot(e.x - cx, e.y - cy).coerceIn(minR, maxR)
                } else if (mode == MOVE) {
                    cx = (e.x + offX).coerceIn(0f, width.toFloat())
                    cy = (e.y + offY).coerceIn(0f, height.toFloat())
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = NONE
        }
        return true
    }
}

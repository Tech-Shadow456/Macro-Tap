package com.macrotap.app

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

class ShapeButtonView(context: Context) : View(context) {
    var shape = 0 // 0 circle, 1 square, 2 triangle
    var color = Color.parseColor("#00E5A0")
    var label = ""

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private fun mix(c: Int, t: Int, f: Float): Int {
        val r = (Color.red(c) * (1 - f) + Color.red(t) * f).toInt()
        val g = (Color.green(c) * (1 - f) + Color.green(t) * f).toInt()
        val b = (Color.blue(c) * (1 - f) + Color.blue(t) * f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun shapePath(l: Float, t: Float, r: Float, b: Float): Path {
        val p = Path()
        when (shape) {
            0 -> p.addOval(RectF(l, t, r, b), Path.Direction.CW)
            1 -> p.addRoundRect(RectF(l, t, r, b), (r - l) * 0.18f, (r - l) * 0.18f, Path.Direction.CW)
            else -> {
                p.moveTo((l + r) / 2, t)
                p.lineTo(r, b)
                p.lineTo(l, b)
                p.close()
            }
        }
        return p
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = w * 0.06f

        // soft outer glow
        fill.shader = null
        fill.color = color
        fill.alpha = 70
        canvas.drawPath(shapePath(0f, 0f, w, h), fill)

        // shaded body
        val cx = w / 2
        val cy = if (shape == 2) h * 0.64f else h / 2
        val light = mix(color, Color.WHITE, 0.45f)
        val dark = mix(color, Color.BLACK, 0.55f)
        fill.shader = RadialGradient(
            cx, cy, w * 0.6f,
            intArrayOf(light, color, dark), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        fill.alpha = 255
        val inset = sw * 1.2f
        canvas.drawPath(shapePath(inset, inset, w - inset, h - inset), fill)
        fill.shader = null

        // bright ring
        ring.strokeWidth = sw
        ring.color = mix(color, Color.WHITE, 0.7f)
        val half = sw / 2
        canvas.drawPath(shapePath(half, half, w - half, h - half), ring)

        // crosshair icon
        val r = w * (if (shape == 2) 0.13f else 0.17f)
        icon.strokeWidth = w * 0.04f
        canvas.drawCircle(cx, cy, r, icon)
        val a = r * 0.55f
        val b = r * 1.6f
        canvas.drawLine(cx - b, cy, cx - a, cy, icon)
        canvas.drawLine(cx + a, cy, cx + b, cy, icon)
        canvas.drawLine(cx, cy - b, cx, cy - a, icon)
        canvas.drawLine(cx, cy + a, cx, cy + b, icon)
        canvas.drawCircle(cx, cy, w * 0.025f, dot)
    }

    fun contains(x: Float, y: Float): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        return when (shape) {
            0 -> hypot(x - w / 2, y - h / 2) <= minOf(w, h) / 2
            1 -> true
            else -> y in 0f..h && abs(x - w / 2) <= (w / 2) * (y / h)
        }
    }
}

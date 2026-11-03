package com.macrotap.app

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ShapeButtonView(context: Context) : View(context) {
    companion object {
        val ICON_NAMES = listOf(
            "Crosshair", "Flame", "Bolt", "Scope", "Jump arrow", "Bullet", "Target", "Ninja star"
        )
    }

    var shape = 0 // 0 circle, 1 square, 2 triangle
    var color = Color.parseColor("#00E5A0")
    var label = ""
    var icon = 0
        set(v) {
            field = v
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

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

    private fun poly(vararg pts: Float): Path {
        val p = Path()
        p.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            p.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        p.close()
        return p
    }

    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, sz: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(sz, sz)
        stroke.strokeWidth = (width * 0.045f) / sz
        when (icon) {
            1 -> {
                val p = Path()
                p.moveTo(0f, -1f)
                p.cubicTo(0.2f, -0.6f, 0.7f, -0.3f, 0.6f, 0.3f)
                p.cubicTo(0.55f, 0.8f, 0.2f, 1f, 0f, 1f)
                p.cubicTo(-0.3f, 1f, -0.7f, 0.8f, -0.6f, 0.3f)
                p.cubicTo(-0.55f, 0f, -0.3f, -0.1f, -0.25f, -0.4f)
                p.cubicTo(-0.1f, -0.25f, 0f, -0.5f, 0f, -1f)
                p.close()
                canvas.drawPath(p, solid)
            }
            2 -> canvas.drawPath(
                poly(0.15f, -1f, -0.55f, 0.15f, -0.05f, 0.15f, -0.2f, 1f, 0.6f, -0.2f, 0.05f, -0.2f), solid
            )
            3 -> {
                canvas.drawCircle(0f, 0f, 0.85f, stroke)
                canvas.drawLine(-1f, 0f, 1f, 0f, stroke)
                canvas.drawLine(0f, -1f, 0f, 1f, stroke)
                canvas.drawCircle(0f, 0f, 0.12f, solid)
            }
            4 -> canvas.drawPath(
                poly(0f, -1f, 0.8f, 0f, 0.3f, 0f, 0.3f, 1f, -0.3f, 1f, -0.3f, 0f, -0.8f, 0f), solid
            )
            5 -> {
                val p = Path()
                p.moveTo(0f, -1f)
                p.cubicTo(0.45f, -0.7f, 0.45f, -0.3f, 0.45f, -0.2f)
                p.lineTo(0.45f, 0.7f)
                p.lineTo(-0.45f, 0.7f)
                p.lineTo(-0.45f, -0.2f)
                p.cubicTo(-0.45f, -0.3f, -0.45f, -0.7f, 0f, -1f)
                p.close()
                canvas.drawPath(p, solid)
            }
            6 -> {
                canvas.drawCircle(0f, 0f, 0.75f, stroke)
                canvas.drawCircle(0f, 0f, 0.35f, solid)
            }
            7 -> {
                val p = Path()
                for (k in 0 until 8) {
                    val r = if (k % 2 == 0) 1f else 0.38f
                    val a = (k * 45.0 - 90.0) * Math.PI / 180.0
                    val px = (r * cos(a)).toFloat()
                    val py = (r * sin(a)).toFloat()
                    if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                p.close()
                canvas.drawPath(p, solid)
            }
            else -> {
                canvas.drawCircle(0f, 0f, 0.5f, stroke)
                canvas.drawLine(-1f, 0f, -0.25f, 0f, stroke)
                canvas.drawLine(0.25f, 0f, 1f, 0f, stroke)
                canvas.drawLine(0f, -1f, 0f, -0.25f, stroke)
                canvas.drawLine(0f, 0.25f, 0f, 1f, stroke)
                canvas.drawCircle(0f, 0f, 0.09f, solid)
            }
        }
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = w * 0.06f

        fill.shader = null
        fill.color = color
        fill.alpha = 70
        canvas.drawPath(shapePath(0f, 0f, w, h), fill)

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

        ring.strokeWidth = sw
        ring.color = mix(color, Color.WHITE, 0.7f)
        val half = sw / 2
        canvas.drawPath(shapePath(half, half, w - half, h - half), ring)

        drawIcon(canvas, cx, cy, w * (if (shape == 2) 0.2f else 0.27f))
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

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                if (contains(e.x, e.y)) animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        return super.dispatchTouchEvent(e)
    }
}

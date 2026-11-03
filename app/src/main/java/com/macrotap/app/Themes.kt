package com.macrotap.app

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class Theme(
    val name: String, val bg: String, val card: String, val line: String,
    val accent: String, val text: String, val sub: String, val pro: Boolean
)

object Themes {
    val list = listOf(
        Theme("Neon Green", "#0B0E14", "#151A23", "#232B38", "#00E5A0", "#EAF0FF", "#8A94A8", false),
        Theme("Cyber Blue", "#0A0F1E", "#121A2E", "#1F2B47", "#00B8FF", "#EAF4FF", "#8AA0C0", false),
        Theme("Purple Haze", "#100B1A", "#1A1426", "#2C2340", "#B388FF", "#F1EAFF", "#9A8FB5", true),
        Theme("Sunset", "#140C0A", "#211512", "#37221C", "#FF7A3D", "#FFF0E8", "#B59A8F", true),
        Theme("Crimson", "#120A0D", "#1E1216", "#33202A", "#FF4D6A", "#FFEAEE", "#B0909A", true),
        Theme("Gold Rush", "#0F0D08", "#1C1910", "#332D1B", "#FFC53D", "#FFF6DD", "#B0A685", true),
        Theme("Matrix", "#000A03", "#04140A", "#0B2A17", "#39FF14", "#D8FFD0", "#6FAF72", true)
    )

    private fun prefs(c: Context) = c.getSharedPreferences("look", Context.MODE_PRIVATE)

    fun index(c: Context): Int = prefs(c).getInt("theme", 0)
    fun setIndex(c: Context, i: Int) = prefs(c).edit().putInt("theme", i).apply()
    fun backdropRaw(c: Context): Int = prefs(c).getInt("backdrop", 1)
    fun setBackdrop(c: Context, s: Int) = prefs(c).edit().putInt("backdrop", s).apply()

    fun backdropStyle(c: Context): Int {
        val s = backdropRaw(c)
        return if (s > 1 && !License.isPro(c)) 1 else s
    }

    fun apply(c: Context) {
        var t = list.getOrElse(index(c)) { list[0] }
        if (t.pro && !License.isPro(c)) t = list[0]
        C.BG = Color.parseColor(t.bg)
        C.CARD = Color.parseColor(t.card)
        C.LINE = Color.parseColor(t.line)
        C.ACCENT = Color.parseColor(t.accent)
        C.TEXT = Color.parseColor(t.text)
        C.SUB = Color.parseColor(t.sub)
        C.TRACK = (C.ACCENT and 0x00FFFFFF) or 0x66000000
    }
}

class Backdrop(c: Context) : View(c) {
    var style = 1
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t0 = System.nanoTime()
    private val rnd = java.util.Random(7)
    private val starX = FloatArray(70) { rnd.nextFloat() }
    private val starY = FloatArray(70) { rnd.nextFloat() }
    private val starS = FloatArray(70) { 0.3f + rnd.nextFloat() }
    private var live = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        live = true
        invalidate()
    }

    override fun onDetachedFromWindow() {
        live = false
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(v: Int) {
        super.onWindowVisibilityChanged(v)
        live = v == VISIBLE
        if (live) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (style == 0) return
        val t = (System.nanoTime() - t0) / 1_000_000_000f
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        val a = C.ACCENT
        val r = Color.red(a)
        val g = Color.green(a)
        val b = Color.blue(a)
        when (style) {
            1 -> for (k in 0..2) {
                val cx = w * (0.5f + 0.45f * sin(t * 0.25f + k * 2.1f))
                val cy = h * (0.5f + 0.45f * cos(t * 0.2f + k * 1.7f))
                p.shader = RadialGradient(
                    cx, cy, w * 0.7f, Color.argb(55, r, g, b), Color.argb(0, r, g, b), Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, w * 0.7f, p)
            }
            2 -> {
                p.strokeWidth = 2f
                p.color = Color.argb(28, r, g, b)
                val gap = d * 48f
                var x = 0f
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, p)
                    x += gap
                }
                var y = (t * 14f) % gap
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, p)
                    y += gap
                }
                p.color = Color.argb(80, r, g, b)
                val sweep = (t * 90f) % (h + 200f) - 100f
                canvas.drawLine(0f, sweep, w, sweep, p)
            }
            else -> {
                for (i in starX.indices) {
                    val y = ((starY[i] - t * 0.02f * starS[i]) % 1f + 1f) % 1f
                    val al = (90 + 90 * sin(t * 1.5f * starS[i] + i)).toInt().coerceIn(0, 255)
                    p.color = Color.argb(al, r, g, b)
                    canvas.drawCircle(starX[i] * w, y * h, (1f + starS[i]) * d, p)
                }
            }
        }
        p.shader = null
        if (live) postInvalidateOnAnimation()
    }
}

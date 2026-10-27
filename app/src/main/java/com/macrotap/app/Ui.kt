package com.macrotap.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object C {
    val BG = Color.parseColor("#0B0E14")
    val CARD = Color.parseColor("#151A23")
    val LINE = Color.parseColor("#232B38")
    val ACCENT = Color.parseColor("#00E5A0")
    val TRACK = Color.parseColor("#6600E5A0")
    val OFF = Color.parseColor("#33FFFFFF")
    val TEXT = Color.parseColor("#EAF0FF")
    val SUB = Color.parseColor("#8A94A8")
    val DANGER = Color.parseColor("#FF4D6A")
    val WARN = Color.parseColor("#FFB020")
}

fun Context.dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

fun rounded(color: Int, radius: Float, stroke: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = radius
    if (stroke != 0) g.setStroke(2, stroke)
    return g
}

fun Context.llp(w: Int = MATCH, h: Int = WRAP, top: Int = 0): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(w, h)
    p.topMargin = dp(top)
    return p
}

fun Context.card(parent: LinearLayout): LinearLayout {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.setPadding(dp(16), dp(16), dp(16), dp(16))
    c.background = rounded(C.CARD, dp(18).toFloat(), C.LINE)
    parent.addView(c, llp(top = 12))
    return c
}

fun Context.row(parent: LinearLayout): LinearLayout {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    parent.addView(r, llp())
    return r
}

fun Context.text(
    parent: LinearLayout, t: String, size: Float = 14f, color: Int = C.TEXT,
    bold: Boolean = false, top: Int = 0
): TextView {
    val v = TextView(this)
    v.text = t
    v.textSize = size
    v.setTextColor(color)
    if (bold) v.typeface = Typeface.DEFAULT_BOLD
    parent.addView(v, llp(top = top))
    return v
}

fun Context.button(
    parent: LinearLayout, t: String, filled: Boolean = true, weighted: Boolean = false,
    onClick: () -> Unit
): Button {
    val b = Button(this)
    b.text = t
    b.isAllCaps = false
    b.stateListAnimator = null
    b.typeface = Typeface.DEFAULT_BOLD
    b.setTextColor(if (filled) Color.BLACK else C.ACCENT)
    b.background = if (filled) {
        rounded(C.ACCENT, dp(14).toFloat())
    } else {
        rounded(Color.TRANSPARENT, dp(14).toFloat(), C.ACCENT)
    }
    b.setOnClickListener { onClick() }
    val p = if (weighted) LinearLayout.LayoutParams(0, dp(46), 1f) else LinearLayout.LayoutParams(MATCH, dp(46))
    val side = if (weighted) dp(3) else 0
    p.setMargins(side, dp(10), side, 0)
    parent.addView(b, p)
    return b
}

fun Context.switchRow(
    parent: LinearLayout, t: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit
): Switch {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    text(col, t, 16f, C.TEXT, true)
    if (sub.isNotEmpty()) text(col, sub, 12f, C.SUB)
    r.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
    val s = Switch(this)
    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
    s.thumbTintList = ColorStateList(states, intArrayOf(C.ACCENT, C.SUB))
    s.trackTintList = ColorStateList(states, intArrayOf(C.TRACK, C.OFF))
    s.isChecked = checked
    s.setOnCheckedChangeListener { _, v -> onChange(v) }
    r.addView(s)
    parent.addView(r, llp(top = 10))
    return s
}

fun Context.slider(
    parent: LinearLayout, t: String, min: Int, max: Int, value: Int,
    fmt: (Int) -> String, onChange: (Int) -> Unit
): SeekBar {
    val head = text(parent, "$t: ${fmt(value)}", 14f, C.TEXT, false, 12)
    val bar = SeekBar(this)
    bar.max = max - min
    bar.progress = value - min
    bar.progressTintList = ColorStateList.valueOf(C.ACCENT)
    bar.thumbTintList = ColorStateList.valueOf(C.ACCENT)
    bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
            head.text = "$t: ${fmt(p + min)}"
            if (fromUser) onChange(p + min)
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
    parent.addView(bar, llp())
    return bar
}

fun Context.permRow(parent: LinearLayout, name: String, ok: Boolean, onGrant: () -> Unit) {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    val t = TextView(this)
    t.text = (if (ok) "✔  " else "✘  ") + name
    t.textSize = 14f
    t.setTextColor(if (ok) C.ACCENT else C.SUB)
    r.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
    if (!ok) {
        val b = Button(this)
        b.text = "Grant"
        b.isAllCaps = false
        b.stateListAnimator = null
        b.setTextColor(C.ACCENT)
        b.background = rounded(Color.TRANSPARENT, dp(12).toFloat(), C.ACCENT)
        b.setOnClickListener { onGrant() }
        r.addView(b, LinearLayout.LayoutParams(dp(84), dp(38)))
    }
    parent.addView(r, llp(top = 10))
}

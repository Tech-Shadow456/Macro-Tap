package com.macrotap.app

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

class ShapeButtonView(context: Context) : View(context) {
    var shape = 0 // 0 circle, 1 square, 2 triangle
    var color = Color.parseColor("#E64A19")
    var label = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = color
        paint.alpha = 220
        when (shape) {
            0 -> canvas.drawCircle(w / 2, h / 2, minOf(w, h) / 2, paint)
            1 -> canvas.drawRoundRect(0f, 0f, w, h, w * 0.1f, w * 0.1f, paint)
            else -> {
                val path = Path()
                path.moveTo(w / 2, 0f)
                path.lineTo(w, h)
                path.lineTo(0f, h)
                path.close()
                canvas.drawPath(path, paint)
            }
        }
        textPaint.textSize = w * 0.3f
        val ty = if (shape == 2) h * 0.78f else h / 2 - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, w / 2, ty, textPaint)
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

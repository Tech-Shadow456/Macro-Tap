package com.macrotap.app

import android.content.Context
import android.graphics.*
import android.view.View

class ShapeButtonView(context: Context) : View(context) {
    var shape = 0 // 0 circle, 1 square, 2 triangle
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E64A19")
        alpha = 220
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
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
    }
}

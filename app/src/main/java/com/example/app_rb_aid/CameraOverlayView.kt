package com.example.app_rb_aid

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CameraOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#80000000") // Hitam transparan
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Layer gelap semi-transparan
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        // Kotak retina di tengah layar
        val boxSize = width * 0.6f
        val left = (width - boxSize) / 2
        val top = (height - boxSize) / 2
        val right = left + boxSize
        val bottom = top + boxSize

        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, 32f, 32f, borderPaint)
    }
}

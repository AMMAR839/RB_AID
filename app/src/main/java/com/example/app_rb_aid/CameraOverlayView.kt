package com.example.app_rb_aid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.min

class CameraOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dimPaint = Paint().apply {
        color = "#80000000".toColorInt() // Hitam transparan
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val boxRect = RectF()
    private val dimPath = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Tentukan ukuran kotak (60% dari lebar, atau pakai sisi pendek agar selalu square)
        val side = min(w, h) * 0.60f
        val left = (w - side) / 2f
        val top  = (h - side) / 2f
        boxRect.set(left, top, left + side, top + side)

        // Siapkan path untuk lapisan gelap berlubang kotak
        dimPath.reset()
        dimPath.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        val hole = Path().apply { addRoundRect(boxRect, 32f, 32f, Path.Direction.CW) }
        dimPath.op(hole, Path.Op.DIFFERENCE)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Buat layer agar CLEAR bekerja
        val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // Gelapkan seluruh layar, sisakan lubang kotak
        canvas.drawPath(dimPath, dimPaint)
        canvas.drawRoundRect(boxRect, 32f, 32f, clearPaint)

        // Gambar border kotak
        canvas.drawRoundRect(boxRect, 32f, 32f, borderPaint)

        canvas.restoreToCount(saved)
    }

    /** ROI kotak dalam koordinat view (dan sama dengan bitmap dari PreviewView) */
    fun getBoxRect(): RectF = RectF(boxRect)
}

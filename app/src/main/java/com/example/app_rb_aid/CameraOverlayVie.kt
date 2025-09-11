package com.example.app_rb_aid

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CameraOverlayVie(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#80000000") // hitam semi transparan
        style = Paint.Style.FILL
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

    init {
        // WAJIB supaya clear mode bisa jalan
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // Lapis seluruh layar dengan hitam transparan
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        // Buat kotak di tengah
        val boxSize = width * 0.6f
        val left = (width - boxSize) / 2
        val top = (height - boxSize) / 2
        val right = left + boxSize
        val bottom = top + boxSize

        val rect = RectF(left, top, right, bottom)

        // Hapus area kotak (jadi transparan, kamera kelihatan)
        canvas.drawRoundRect(rect, 32f, 32f, clearPaint)

        // Gambar garis putih di tepi kotak
        canvas.drawRoundRect(rect, 32f, 32f, borderPaint)

        canvas.restoreToCount(layerId)
    }
}

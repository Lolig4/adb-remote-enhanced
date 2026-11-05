package dev.adbremote.navhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.WindowManager

/** Full-screen, non-touchable accessibility overlay: selection frame (+ debug layer). */
class Overlay(context: Context) : View(context) {

    var selection: Rect? = null
        set(value) { field = value?.let { Rect(it) }; invalidate() }
    var debug = false
        set(value) { field = value; invalidate() }
    var candidates: List<Rect> = emptyList()
    var scrollables: List<Rect> = emptyList()
    var status: List<String> = emptyList()
    var message: String? = null

    private val density = resources.displayMetrics.density
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4 * density; color = Color.rgb(0x8B, 0xC3, 0x4A)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 10 * density; color = Color.argb(0x55, 0x8B, 0xC3, 0x4A)
    }
    private val candPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = Color.argb(0xCC, 0x29, 0xB6, 0xF6)
    }
    private val scrollPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * density; color = Color.argb(0xCC, 0xFF, 0xA0, 0x00)
        pathEffect = DashPathEffect(floatArrayOf(12 * density, 8 * density), 0f)
    }
    private val textBg = Paint().apply { color = Color.argb(0xC0, 0, 0, 0) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13 * density }
    private val msgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18 * density }

    override fun onDraw(canvas: Canvas) {
        // the overlay window may not start at the screen origin; draw in screen coordinates
        val loc = IntArray(2); getLocationOnScreen(loc)
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
        if (debug) {
            scrollables.forEach { canvas.drawRect(it, scrollPaint) }
            candidates.forEach { canvas.drawRect(it, candPaint) }
        }
        selection?.let {
            val r = RectF(it); val radius = 10 * density
            canvas.drawRoundRect(r, radius, radius, glowPaint)
            canvas.drawRoundRect(r, radius, radius, framePaint)
        }
        canvas.translate(loc[0].toFloat(), loc[1].toFloat())
        if (debug && status.isNotEmpty()) {
            val lh = textPaint.textSize * 1.3f
            val w = status.maxOf { textPaint.measureText(it) } + 24 * density
            val x = 16 * density; val y = height - 16 * density - lh * status.size
            canvas.drawRect(x - 8 * density, y - lh, x + w, height - 8 * density, textBg)
            status.forEachIndexed { i, line -> canvas.drawText(line, x, y + i * lh, textPaint) }
        }
        message?.let {
            val w = msgPaint.measureText(it) + 32 * density
            val x = (width - w) / 2; val y = 48 * density
            canvas.drawRoundRect(RectF(x, y, x + w, y + 40 * density), 20 * density, 20 * density, textBg)
            canvas.drawText(it, x + 16 * density, y + 27 * density, msgPaint)
        }
    }

    companion object {
        fun params(): WindowManager.LayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}

package com.jcapps.adbremote.views

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.jcapps.adbremote.R
import com.jcapps.adbremote.model.TouchPoint
import kotlin.math.roundToInt

/** Card that draws a circle for every active touch pointer. Referenced from layout dialog_trackpad. */
class TrackpadView(context: Context, attrs: AttributeSet?) : MaterialCardView(context, attrs) {
    // obf: C1452

    // obf: f1608
    private var pointerDrawable: GradientDrawable? = null

    // obf: f1610
    private var pointerWidth: Int = 0

    // obf: f1609
    private var pointerHeight: Int = 0

    // obf: f1611 — pointerId -> point (-2 = centre marker)
    val touchPoints: LinkedHashMap<Int, TouchPoint> = LinkedHashMap()

    // obf: getCenterTouchPoint
    private val centerTouchPoint: TouchPoint
        get() = TouchPoint(width / 2.0f, height / 2.0f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pointerDrawable == null) return
        synchronized(touchPoints) {
            for (point in touchPoints.values) {
                pointerDrawable?.let { drawable ->
                    drawable.alpha = point.alpha
                    val left = point.x - (pointerWidth / 2)
                    val top = point.y - (pointerHeight / 2)
                    drawable.setBounds(left.toInt(), top.toInt(), (left + pointerWidth).toInt(), (top + pointerHeight).toInt())
                    drawable.draw(canvas)
                }
            }
        }
    }

    // obf: m842
    fun setPointerSize(sizeDp: Int, showCenter: Boolean) {
        pointerDrawable = null
        val fillColor = MaterialColors.getColor(context, R.attr.colorPrimaryContainer, -16777216)
        val strokeColor = MaterialColors.getColor(context, R.attr.colorPrimary, -16777216)
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(fillColor)
        drawable.setStroke((1 * context.resources.displayMetrics.density).roundToInt(), strokeColor)
        val size = sizeDp.toFloat()
        drawable.setSize(
            (context.resources.displayMetrics.density * size).roundToInt(),
            (size * context.resources.displayMetrics.density).roundToInt(),
        )
        pointerDrawable = drawable
        pointerWidth = drawable.intrinsicWidth
        pointerHeight = pointerDrawable?.intrinsicHeight ?: 0
        if (showCenter) {
            touchPoints[-2] = centerTouchPoint
            invalidate()
        }
    }

    /** Hides the pointers (trackpad pointers switched off). (inlined into DialogTrackpad) */
    fun disablePointers() {
        touchPoints.clear()
        invalidate()
        pointerDrawable = null
    }

    /** Tracks touch pointers for drawing. (inlined into DialogTrackpad's ViewOnTouchListenerC3083 case 0) */
    fun onTouch(event: MotionEvent) {
        if (pointerDrawable == null) return
        synchronized(touchPoints) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> {
                    for (i in 0 until event.pointerCount) {
                        val pointerId = event.getPointerId(i)
                        val x = event.getX(i)
                        val y = event.getY(i)
                        val point = touchPoints[pointerId]
                        if (point != null) {
                            point.x = x
                            point.y = y
                        } else {
                            touchPoints[pointerId] = TouchPoint(x, y)
                        }
                    }
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    touchPoints.remove(event.getPointerId(event.actionIndex))
                    invalidate()
                }
                MotionEvent.ACTION_CANCEL -> {
                    touchPoints.clear()
                    invalidate()
                }
            }
        }
    }
}

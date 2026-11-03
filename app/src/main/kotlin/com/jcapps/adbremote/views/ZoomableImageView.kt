package com.jcapps.adbremote.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import com.google.android.material.imageview.ShapeableImageView
import kotlin.math.abs

/**
 * Pinch / double-tap zoomable image (1x..3x) with fling, and optional vertical swipe-to-dismiss.
 * Referenced from layout dialog_screenshot (id k9) by FQN.
 */
class ZoomableImageView(context: Context, attrs: AttributeSet) : ShapeableImageView(context, attrs) {
    // obf: C1495

    // obf: f1614
    private val matrixValues = FloatArray(9)

    /** Fit-center base matrix. */
    // obf: f1615
    private val baseMatrix = Matrix()

    /** User zoom / pan on top of [baseMatrix]. */
    // obf: f1616
    private val suppMatrix = Matrix()

    // obf: f1617
    private lateinit var scroller: OverScroller

    // obf: f1618
    var swipeToDismissEnabled: Boolean = false

    // obf: f1619
    private var longClickListener: View.OnLongClickListener? = null

    /** Debug text of the current translation / scale (never drawn). */
    // obf: f1620
    private var debugText: String = ""

    // obf: f1621
    private lateinit var scaleDetector: ScaleGestureDetector

    // obf: f1622
    private val displayRectF: RectF? = RectF()

    // obf: f1623 (default = C0728 case 3, no-op)
    var dismissProgressListener: (Float) -> Unit = {}

    // obf: f1624
    private val interpolator = AccelerateDecelerateInterpolator()

    /** Display rect captured on touch down (fling bounds / paging edges). */
    // obf: f1625
    private val downDisplayRect = RectF()

    // obf: f1626
    var disallowPagingWhenZoomed: Boolean = false

    // obf: f1627
    private lateinit var tapDetector: GestureDetector

    // obf: f1628
    private var dismissAnimator: ValueAnimator? = null

    // obf: f1629
    private var clickListener: View.OnClickListener? = null

    // obf: f1630 (default = C3079 case 4, no-op)
    var onDismiss: () -> Unit = {}

    /** RECON-UNSURE: name; never-called no-op hook (only `getClass()` survived in setImageDrawable). */
    // obf: f1631 (C3079 case 4)
    private val onImageChanged: () -> Unit = {}

    // obf: f1632
    private var zoomAnimator: ValueAnimator? = null

    // obf: f1633
    private var isDismissing: Boolean = false

    // obf: f1634
    private var viewWidth: Int

    // obf: f1635
    private val flingRunnable = FlingRunnable()

    // obf: f1636
    private var viewHeight: Int

    // obf: f1637
    private val touchSlop: Float

    /** Scale at the start of the current zoom gesture / animation. */
    // obf: f1638
    private var startScale: Float = 1.0f

    // obf: f1639
    private val drawMatrixInternal = Matrix()

    init {
        val paint = Paint()
        viewWidth = right - left - paddingLeft - paddingRight
        viewHeight = bottom - top - paddingTop - paddingBottom
        // obf: C0994
        val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (detector.scaleFactor.isNaN() || detector.scaleFactor.isInfinite()) {
                    return false
                }
                if (currentScale > 3.0f && detector.scaleFactor > 1.0f) {
                    return false
                }
                startScale = currentScale
                zoom(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                super.onScaleEnd(detector)
                startScale = currentScale
                if (currentScale < 1.0f) {
                    animateZoom(1.0f, detector.focusX, detector.focusY)
                }
            }
        }
        touchSlop = ViewConfiguration.get(getContext()).scaledTouchSlop.toFloat()
        paint.color = -1
        paint.style = Paint.Style.FILL
        paint.textSize = 40.0f
        scaleType = ScaleType.MATRIX
        scaleDetector = ScaleGestureDetector(getContext(), scaleListener)
        scroller = OverScroller(getContext(), DecelerateInterpolator())
        // obf: C2493
        tapDetector = GestureDetector(
            getContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    startScale = currentScale
                    animateZoom(if (currentScale == 1.0f) 1.75f else 1.0f, e.x, e.y)
                    return true
                }

                override fun onDown(e: MotionEvent): Boolean {
                    removeCallbacks(flingRunnable)
                    scroller.forceFinished(true)
                    displayRect?.let { downDisplayRect.set(it) }
                    dismissAnimator?.removeAllUpdateListeners()
                    dismissAnimator?.cancel()
                    return true
                }

                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (currentZoom <= 1.0f) {
                        return false
                    }
                    val maxX = (downDisplayRect.width() - viewWidth).toInt()
                    val maxY = (downDisplayRect.height() - viewHeight).toInt()
                    val rect = downDisplayRect
                    val startX = -rect.left
                    flingRunnable.lastX = startX
                    val startY = -rect.top
                    flingRunnable.lastY = startY
                    scroller.fling(startX.toInt(), startY.toInt(), -velocityX.toInt(), -velocityY.toInt(), 0, maxX, 0, maxY)
                    postOnAnimation(flingRunnable)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    longClickListener?.onLongClick(this@ZoomableImageView)
                }

                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                    if (scaleDetector.isInProgress) {
                        return false
                    }
                    val absX = abs(distanceX)
                    val absY = abs(distanceY)
                    if (currentScale > 1.0f) {
                        translate(distanceX, distanceY, false)
                    } else if (swipeToDismissEnabled && absY > absX) {
                        isDismissing = true
                        translate(0.0f, distanceY, false)
                        dismissProgressListener(dismissProgress)
                    }
                    val disallowIntercept = if (disallowPagingWhenZoomed || isDismissing) {
                        true
                    } else {
                        val rect = downDisplayRect
                        if (absX > absY) {
                            !((distanceX > 0.0f && rect.right == viewWidth.toFloat()) ||
                                (distanceX < 0.0f && rect.left == 0.0f))
                        } else {
                            !((distanceY > 0.0f && rect.bottom == viewHeight.toFloat()) ||
                                (distanceY < 0.0f && rect.top == 0.0f))
                        }
                    }
                    parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
                    return absX > touchSlop || absY > touchSlop
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val listener = clickListener ?: return true
                    listener.onClick(this@ZoomableImageView)
                    return true
                }
            },
        )
    }

    // obf: getCurrentScale
    private val currentScale: Float
        get() {
            suppMatrix.getValues(matrixValues)
            return matrixValues[Matrix.MSCALE_X]
        }

    // obf: getCurrentTransX
    private val currentTransX: Float
        get() {
            suppMatrix.getValues(matrixValues)
            return matrixValues[Matrix.MTRANS_X]
        }

    // obf: getCurrentTransY
    private val currentTransY: Float
        get() {
            suppMatrix.getValues(matrixValues)
            return matrixValues[Matrix.MTRANS_Y]
        }

    // obf: getDismissProgress
    private val dismissProgress: Float
        get() = abs(currentTransY) / dismissThreshold

    // obf: getDismissThreshold
    private val dismissThreshold: Float
        get() = viewHeight / 3.0f

    // obf: getDisplayRect
    private val displayRect: RectF?
        get() {
            val drawable = drawable ?: return null
            val rect = displayRectF
            rect?.set(0.0f, 0.0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
            drawMatrix.mapRect(rect)
            return rect
        }

    // obf: getDrawMatrix
    private val drawMatrix: Matrix
        get() {
            val matrix = drawMatrixInternal
            matrix.set(baseMatrix)
            matrix.postConcat(suppMatrix)
            return matrix
        }

    // obf: getDrawableHeight
    private val drawableHeight: Int
        get() = drawable?.intrinsicHeight ?: 0

    // obf: getDrawableWidth
    private val drawableWidth: Int
        get() = drawable?.intrinsicWidth ?: 0

    // obf: getCurrentZoom / setCurrentZoom
    var currentZoom: Float
        get() {
            suppMatrix.getValues(matrixValues)
            return matrixValues[Matrix.MSCALE_X]
        }
        set(value) {
            startScale = currentScale
            animateZoom(value, viewWidth / 2.0f, viewHeight / 2.0f)
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        viewWidth = right - left - paddingLeft - paddingRight
        viewHeight = bottom - top - paddingTop - paddingBottom
        if (changed) {
            resetMatrix()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }
        val disallowIntercept = currentScale > 1.0f || scaleDetector.isInProgress || isDismissing
        if (event.action == MotionEvent.ACTION_UP && isDismissing) {
            if (abs(currentTransY) > dismissThreshold) {
                onDismiss()
            } else {
                val startY = currentTransY
                val progress = dismissProgress
                val startX = 0.0f
                val endX = 0.0f
                val endY = 0.0f
                val animator = ValueAnimator.ofFloat(startX, startY, endX, endY)
                animator.duration = 300L
                animator.addUpdateListener {
                    translate(
                        startX - it.animatedFraction * (startX - endX),
                        startY - it.animatedFraction * (startY - endY),
                        true,
                    )
                    if (1.0f - it.animatedFraction < progress) {
                        dismissProgressListener(1.0f - it.animatedFraction)
                    }
                }
                animator.interpolator = interpolator
                animator.start()
                // obf: C1285 case 0
                animator.doOnCancel {
                    translate(0.0f, 0.0f, true)
                    isDismissing = false
                }
                // obf: C1285 case 1
                animator.doOnEnd {
                    isDismissing = false
                }
                dismissAnimator = animator
            }
        }
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
        if (tapDetector.onTouchEvent(event)) {
            return true
        }
        scaleDetector.onTouchEvent(event)
        return true
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (drawable != null) {
            onImageChanged()
            resetMatrix()
            suppMatrix.set(imageMatrix)
        }
    }

    override fun setOnClickListener(l: View.OnClickListener?) {
        clickListener = l
    }

    override fun setOnLongClickListener(l: View.OnLongClickListener?) {
        longClickListener = l
    }

    /** Animates the zoom to [scale] (clamped to 1..3) around the focus point. */
    // obf: m847
    private fun animateZoom(scale: Float, focusX: Float, focusY: Float) {
        val targetScale = scale.coerceIn(1.0f, 3.0f)
        zoomAnimator?.removeAllUpdateListeners()
        zoomAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(startScale, targetScale)
        animator.duration = 300L
        animator.addUpdateListener {
            val value = it.animatedValue as Float
            zoom(value / currentScale, focusX, focusY)
        }
        animator.interpolator = interpolator
        animator.start()
        zoomAnimator = animator
    }

    // obf: m848
    private fun zoom(scaleFactor: Float, focusX: Float, focusY: Float) {
        suppMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
        checkBounds()
        applyMatrix(drawMatrix)
    }

    // obf: m849
    private fun applyMatrix(matrix: Matrix) {
        debugText = "tX: $currentTransX tY: $currentTransY"
        debugText = "$debugText Scale: $currentScale"
        imageMatrix = matrix
    }

    /** Fits the drawable centered into the view and resets the zoom. */
    // obf: m850
    private fun resetMatrix() {
        val src = RectF(0.0f, 0.0f, drawableWidth.toFloat(), drawableHeight.toFloat())
        val dst = RectF(0.0f, 0.0f, viewWidth.toFloat(), viewHeight.toFloat())
        val matrix = baseMatrix
        matrix.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER)
        animateZoom(1.0f, viewWidth / 2.0f, viewHeight / 2.0f)
        imageMatrix = matrix
    }

    /** Keeps the image inside / centered in the view (no vertical centering while dismissing). */
    // obf: m851 — RECON-UNSURE: jadx mangled the width branch; reconstructed as the symmetric form.
    private fun checkBounds() {
        val rect = displayRect ?: return
        val height = rect.height()
        val width = rect.width()
        val viewHeight = viewHeight.toFloat()
        val deltaY = if (height > viewHeight) {
            if (rect.top > 0.0f) {
                -rect.top
            } else if (rect.bottom < viewHeight) {
                viewHeight - rect.bottom
            } else {
                0.0f
            }
        } else if (isDismissing) {
            0.0f
        } else {
            (viewHeight - height) / 2 - rect.top
        }
        val viewWidth = viewWidth.toFloat()
        val deltaX = if (width > viewWidth) {
            if (rect.left > 0.0f) {
                -rect.left
            } else if (rect.right < viewWidth) {
                viewWidth - rect.right
            } else {
                0.0f
            }
        } else {
            (viewWidth - width) / 2 - rect.left
        }
        suppMatrix.postTranslate(deltaX, deltaY)
    }

    /** Pans by (-dx, -dy), or sets the translation to (dx, dy) when [absolute]. */
    // obf: m852
    private fun translate(dx: Float, dy: Float, absolute: Boolean) {
        val matrix = suppMatrix
        if (absolute) {
            matrix.setTranslate(dx, dy)
        } else {
            matrix.postTranslate(-dx, -dy)
        }
        checkBounds()
        applyMatrix(drawMatrix)
    }

    // obf: RunnableC3926
    private inner class FlingRunnable : Runnable {
        // obf: f13781
        var lastX: Float = 0f

        // obf: f13783
        var lastY: Float = 0f

        override fun run() {
            if (scroller.isFinished) {
                return
            }
            if (scroller.computeScrollOffset()) {
                val currX = scroller.currX.toFloat()
                val currY = scroller.currY.toFloat()
                translate(currX - lastX, currY - lastY, false)
                lastX = currX
                lastY = currY
                postOnAnimation(this)
            }
        }
    }
}

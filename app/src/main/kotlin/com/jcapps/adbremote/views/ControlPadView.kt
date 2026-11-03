package com.jcapps.adbremote.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.jcapps.adbremote.R
import com.jcapps.adbremote.model.Direction
import com.jcapps.adbremote.utils.getColorFromAttr

/** Round D-pad (up/down/left/right sections + center circle) used in layout `a5` (id g8). */
class ControlPadView(context: Context, attrs: AttributeSet) :
    AppCompatImageView(context, attrs), GestureDetector.OnGestureListener {
    // obf: C1253

    enum class SizeMode {
        // obf: EnumC1152
        WRAP,
        FIXED,
    }

    // obf: f1531 (default C3079 case 3 = {})
    var onHapticRequested: () -> Unit = {}

    // obf: f1556
    private var drawDirectionIcons: Boolean = true // RECON-UNSURE name (attr index 0)

    // obf: f1563
    private var drawCenterCircle: Boolean = true // RECON-UNSURE name (attr index 2)

    // obf: f1564
    private var centerRadiusDivider: Float = 4.5f

    // obf: f1533
    var dpadUpIcon: Int = R.drawable.ic_arrow_up

    // obf: f1528
    var dpadDownIcon: Int = R.drawable.ic_arrow_down

    // obf: f1548
    var dpadLeftIcon: Int = R.drawable.ic_arrow_left

    // obf: f1547
    var dpadRightIcon: Int = R.drawable.ic_arrow_right

    // obf: f1527
    private var viewSize: Int = 0

    // obf: f1540
    private var outerRadius: Float = viewSize / 2f

    // obf: f1545
    private var centerRadius: Float = viewSize / 4.5f

    // obf: f1526 (default C2907 case 0 = { _, _ -> })
    var onDPadTouchListener: (Direction?, MotionEvent) -> Unit = { _, _ -> }

    // obf: f1546 (default C3079 case 3 = {})
    var onCenterLongClick: () -> Unit = {}

    // obf: f1561
    private val centerPaint = Paint()

    // obf: f1570
    private val paint2 = Paint()

    // obf: f1571
    private val paint3 = Paint()

    // obf: f1555
    private val paint4 = Paint()

    // obf: f1560
    private val paint5 = Paint()

    // obf: f1552
    private val strokeWidth: Float = 20f / 2

    // obf: f1541
    private var halfSectionAngle: Float = 44f

    // obf: f1535
    private val rect = Rect()

    // obf: f1550
    private val rect2 = Rect()

    // obf: f1558
    private val rectF = RectF()

    // obf: f1544 / f1565 / f1543 / f1572 / f1554 / f1530 / f1569 / f1536 — section angle bounds
    private var downStartAngle: Float = 90 - halfSectionAngle
    private var downEndAngle: Float = 90 + halfSectionAngle
    private var upStartAngle: Float = 270 - halfSectionAngle
    private var upEndAngle: Float = 270 + halfSectionAngle
    private var leftStartAngle: Float = 180 - halfSectionAngle
    private var leftEndAngle: Float = 180 + halfSectionAngle
    private var rightStartAngle: Float = 360 - halfSectionAngle
    private var rightEndAngle: Float = halfSectionAngle

    // obf: f1551
    var normalColor: Int

    // obf: f1567
    var pressedColor: Int

    // obf: f1538
    var directionSectionAngle: Float

    // obf: f1568
    var centerCircleNormalColor: Int

    // obf: f1525
    var centerCirclePressedColor: Int

    // obf: f1542
    var centerIcon: Int?

    // obf: f1532
    private val sizeMode: SizeMode

    // obf: f1529
    private val fixedSize: Float

    // obf: f1549
    var centerIconTint: Int

    // obf: f1553
    var dpadIconsTint: Int

    // obf: f1559
    private val gestureDetector: GestureDetector

    // obf: f1539
    private val centerIconDrawable: Drawable?

    // pressed-state flags (obf: f1534 center, f1557 up, f1537 down, f1562 left, f1566 right)
    private var isCenterPressed = false
    private var isUpPressed = false
    private var isDownPressed = false
    private var isLeftPressed = false
    private var isRightPressed = false

    init {
        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.ControlPadView, 0, 0)
        normalColor = typedArray.getColor(R.styleable.ControlPadView_normalColor, context.getColorFromAttr(android.R.attr.textColorSecondary))
        pressedColor = typedArray.getColor(R.styleable.ControlPadView_pressedColor, context.getColorFromAttr(android.R.attr.colorAccent))
        val angle = typedArray.getFloat(R.styleable.ControlPadView_dpadIconAngle, 90f)
        directionSectionAngle = if (angle <= 90f && angle >= 1f) angle else 90f
        centerCircleNormalColor = typedArray.getColor(R.styleable.ControlPadView_centerCircleNormalColor, context.getColorFromAttr(android.R.attr.colorPrimary))
        centerCirclePressedColor = typedArray.getColor(R.styleable.ControlPadView_centerCirclePressedColor, context.getColorFromAttr(android.R.attr.colorPrimaryDark))
        drawDirectionIcons = typedArray.getBoolean(R.styleable.ControlPadView_drawDirectionIcons, true)
        drawCenterCircle = typedArray.getBoolean(R.styleable.ControlPadView_drawCenterCircle, true)
        centerIcon = typedArray.getResourceId(R.styleable.ControlPadView_centerIcon, 0)
        centerRadiusDivider = typedArray.getFloat(R.styleable.ControlPadView_centerRadiusDivider, 4.5f)
        val padding = typedArray.getDimension(R.styleable.ControlPadView_dpadIconsPadding, 20f)
        sizeMode = if (typedArray.getInt(R.styleable.ControlPadView_padSizeMode, 0) == 1) SizeMode.FIXED else SizeMode.WRAP
        fixedSize = typedArray.getDimension(R.styleable.ControlPadView_fixedPadSize, 48f)
        centerIconTint = typedArray.getColor(R.styleable.ControlPadView_centerIconTint, 0)
        dpadIconsTint = typedArray.getColor(R.styleable.ControlPadView_dpadIconsTint, 0)
        typedArray.recycle()
        gestureDetector = GestureDetector(context, this).apply { setIsLongpressEnabled(true) }
        val icon = centerIcon
        centerIconDrawable = if (icon != null && icon != 0) ContextCompat.getDrawable(getContext(), centerIcon!!) else null
        if (centerIconDrawable != null && centerIconTint != 0) {
            centerIconDrawable.setTint(centerIconTint)
        }
        paint2.color = normalColor
        paint3.color = normalColor
        paint5.color = normalColor
        paint4.color = normalColor
        centerPaint.color = centerCircleNormalColor
        halfSectionAngle = directionSectionAngle / 2
        downStartAngle = 90 - halfSectionAngle
        downEndAngle = 90 + halfSectionAngle
        upStartAngle = 270 - halfSectionAngle
        upEndAngle = 270 + halfSectionAngle
        leftStartAngle = 180 - halfSectionAngle
        leftEndAngle = 180 + halfSectionAngle
        rightStartAngle = 360 - halfSectionAngle
        rightEndAngle = halfSectionAngle
        val p = padding.toInt()
        setPadding(p, p, p, p)
    }

    // obf: getDirection
    private val direction: Direction?
        get() = when {
            isCenterPressed -> Direction.CENTER
            isUpPressed -> Direction.UP
            isDownPressed -> Direction.DOWN
            isLeftPressed -> Direction.LEFT
            isRightPressed -> Direction.RIGHT
            else -> null
        }

    override fun onDown(e: MotionEvent): Boolean = true

    override fun onDraw(canvas: Canvas) {
        outerRadius = (viewSize / 2).toFloat()
        centerRadius = viewSize / centerRadiusDivider
        canvas.getClipBounds(rect)
        rectF.set(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
        canvas.drawArc(rectF, downStartAngle, directionSectionAngle, true, paint3)
        canvas.drawArc(rectF, leftStartAngle, directionSectionAngle, true, paint4)
        canvas.drawArc(rectF, upStartAngle, directionSectionAngle, true, paint2)
        canvas.drawArc(rectF, rightStartAngle, directionSectionAngle, true, paint5)
        if (drawDirectionIcons) {
            canvas.drawCircle(outerRadius, outerRadius, centerRadius, centerPaint)
        }
        super.onDraw(canvas)
        val drawable = centerIconDrawable
        if (drawable != null) {
            if (sizeMode == SizeMode.WRAP) {
                rect2.set(
                    outerRadius.toInt() - drawable.intrinsicWidth / 2,
                    outerRadius.toInt() - drawable.intrinsicHeight / 2,
                    drawable.intrinsicWidth / 2 + outerRadius.toInt(),
                    drawable.intrinsicHeight / 2 + outerRadius.toInt(),
                )
            } else {
                rect2.set(
                    outerRadius.toInt() - fixedSize.toInt() / 2,
                    outerRadius.toInt() - fixedSize.toInt() / 2,
                    fixedSize.toInt() / 2 + outerRadius.toInt(),
                    fixedSize.toInt() / 2 + outerRadius.toInt(),
                )
            }
            drawable.bounds = rect2
            drawable.draw(canvas)
        }
        val up = ContextCompat.getDrawable(context, dpadUpIcon)!!
        up.setTint(dpadIconsTint)
        val upY = (outerRadius - centerRadius) / 2f
        rect2.set(
            (outerRadius - up.intrinsicWidth / 2).toInt(),
            (upY - up.intrinsicWidth / 2).toInt(),
            (outerRadius + up.intrinsicWidth / 2).toInt(),
            (upY + up.intrinsicWidth / 2).toInt(),
        )
        up.bounds = rect2
        up.draw(canvas)
        val down = ContextCompat.getDrawable(context, dpadDownIcon)!!
        down.setTint(dpadIconsTint)
        val downY = (centerRadius + outerRadius) / 2f + outerRadius
        rect2.set(
            (outerRadius - down.intrinsicWidth / 2).toInt(),
            (downY - down.intrinsicWidth / 2).toInt(),
            (outerRadius + down.intrinsicWidth / 2).toInt(),
            (downY + down.intrinsicWidth / 2).toInt(),
        )
        down.bounds = rect2
        down.draw(canvas)
        val left = ContextCompat.getDrawable(context, dpadLeftIcon)!!
        left.setTint(dpadIconsTint)
        val leftX = (outerRadius - centerRadius) / 2f
        rect2.set(
            (leftX - left.intrinsicWidth / 2).toInt(),
            (outerRadius - left.intrinsicWidth / 2).toInt(),
            (leftX + left.intrinsicWidth / 2).toInt(),
            (outerRadius + left.intrinsicWidth / 2).toInt(),
        )
        left.bounds = rect2
        left.draw(canvas)
        val right = ContextCompat.getDrawable(context, dpadRightIcon)!!
        right.setTint(dpadIconsTint)
        val rightX = (centerRadius + outerRadius) / 2f + outerRadius
        rect2.set(
            (rightX - right.intrinsicWidth / 2).toInt(),
            (outerRadius - right.intrinsicWidth / 2).toInt(),
            (rightX + right.intrinsicWidth / 2).toInt(),
            (outerRadius + right.intrinsicWidth / 2).toInt(),
        )
        right.bounds = rect2
        right.draw(canvas)
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = false

    override fun onLongPress(e: MotionEvent) {
        onCenterLongClick()
        isCenterPressed = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val size = Math.min(measuredWidth, measuredHeight)
        viewSize = size
        outerRadius = (size / 2).toFloat()
        setMeasuredDimension(size, size)
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = false

    override fun onShowPress(e: MotionEvent) {
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        isCenterPressed = true
        return true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        val isInPad = isInCircle(event.x, event.y, outerRadius - strokeWidth, outerRadius.toInt())
        val isInCenter = if (drawDirectionIcons && drawCenterCircle) {
            isInCircle(event.x, event.y, centerRadius - strokeWidth, outerRadius.toInt())
        } else {
            false
        }
        if (isInPad) {
            isCenterPressed = false
            isRightPressed = false
            isLeftPressed = false
            isUpPressed = false
            isDownPressed = false
            if (event.action == MotionEvent.ACTION_DOWN) {
                performClick()
            }
            val isPressing = event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE
            if (isInCenter) {
                gestureDetector.onTouchEvent(event)
                isCenterPressed = true
            } else {
                var degrees = Math.toDegrees(
                    Math.atan2((event.y - width / 2).toDouble(), (event.x - width / 2).toDouble()).toFloat().toDouble(),
                )
                if (degrees < 0.0) {
                    degrees += 360
                }
                if (degrees > rightStartAngle || degrees <= rightEndAngle) {
                    isRightPressed = true
                } else if (degrees <= downEndAngle && downStartAngle <= degrees) {
                    isDownPressed = true
                } else if (degrees <= leftEndAngle && leftStartAngle <= degrees) {
                    isLeftPressed = true
                } else if (degrees <= upEndAngle && upStartAngle <= degrees) {
                    isUpPressed = true
                }
            }
            onDPadTouchListener(direction, event)
            isCenterPressed = isCenterPressed and isPressing
            isUpPressed = isUpPressed and isPressing
            isDownPressed = isDownPressed and isPressing
            isLeftPressed = isLeftPressed and isPressing
            isRightPressed = isPressing and isRightPressed
        } else {
            val direction = direction
            if (direction != null) {
                event.action = MotionEvent.ACTION_UP
                onDPadTouchListener(direction, event)
                when (direction) {
                    Direction.UP -> isUpPressed = false
                    Direction.DOWN -> isDownPressed = false
                    Direction.LEFT -> isLeftPressed = false
                    Direction.RIGHT -> isRightPressed = false
                    Direction.CENTER -> isCenterPressed = false
                }
            }
        }
        centerPaint.color = if (!isCenterPressed) centerCircleNormalColor else centerCirclePressedColor
        paint5.color = if (!isRightPressed) normalColor else pressedColor
        paint3.color = if (!isDownPressed) normalColor else pressedColor
        paint4.color = if (!isLeftPressed) normalColor else pressedColor
        paint2.color = if (!isUpPressed) normalColor else pressedColor
        if (isHapticFeedbackEnabled && event.action == MotionEvent.ACTION_DOWN &&
            (isCenterPressed || isRightPressed || isLeftPressed || isUpPressed || isDownPressed)
        ) {
            onHapticRequested()
        }
        invalidate()
        return isInPad
    }

    companion object {
        /** True when (x, y) lies within [radius] of (center, center). */
        // obf: m836
        fun isInCircle(x: Float, y: Float, radius: Float, center: Int): Boolean {
            val c = center.toFloat()
            val dx = Math.abs(x - c)
            if (dx > radius) return false
            val dy = Math.abs(y - c)
            if (dy > radius) return false
            if (dx + dy <= radius) return true
            return dy * dy + dx * dx <= radius * radius
        }

    }
}

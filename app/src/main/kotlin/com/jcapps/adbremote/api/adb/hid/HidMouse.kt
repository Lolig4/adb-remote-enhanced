package com.jcapps.adbremote.api.adb.hid

import android.view.MotionEvent
import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.api.adb.stream.AdbStream
import com.jcapps.adbremote.model.Position

/**
 * Writes virtual-HID mouse/trackpad reports over an [AdbStream].
 *
 * Report template is [NativeConfig.hidReportMouse] (originally `C0104.m801()` base64-decoded).
 * Driven from `AdbClient.processNext` with the incoming [android.view.MotionEvent].
 */
// obf: C1082 (claimed core-adb)
class HidMouse(
    // obf: f5608
    private val stream: AdbStream,
) : AutoCloseable {

    // obf: f5609 — mutable pointer-tracking state (main-a Position, C1172)
    val position: Position = Position(currentTimeMillis = System.currentTimeMillis())

    // obf: f5610 — pointer sensitivity / scale factor
    var sensitivity: Float = 0f

    // obf: f5611
    private var movedSincePress: Boolean = false

    // obf: f5613
    private var pressed: Boolean = false

    // obf: f5612 — was C0450.m2180(C0104.m801()).string(UTF_8)
    private val reportTemplate: String? = NativeConfig.hidReportMouse

    override fun close() {
        stream.close()
    }

    // obf: m3068 — accumulate sub-pixel remainder and clamp the delta to [-127, 127]
    // Returns Pair(remainder, clampedInt). Pure math; ported in full.
    companion object {
        fun accumulate(delta: Float, previous: Float, carry: Float): Pair<Float, Int> {
            val raw = (delta - previous) + carry
            val floored = (if (raw > 0f) Math.floor(raw.toDouble()) else Math.ceil(raw.toDouble())).toFloat()
            val remainder = raw - floored
            val clamped = when {
                floored > 127f -> 127
                floored < -127f -> -127
                else -> floored.toInt()
            }
            return remainder to clamped
        }
    }

    // obf: m3069 — true when (x,y) is within 8px of the press origin (tap detection)
    fun isWithinTapThreshold(x: Float, y: Float): Boolean {
        return Math.abs(position.x - x) < 8.0f && Math.abs(position.y - y) < 8.0f
    }

    // obf: m3070 — send one mouse report (buttons, dx, dy, 0); wraps negative deltas to +256
    fun sendReport(x: Int, y: Int, buttonDown: Boolean) {
        val template = reportTemplate
        val str: String
        if (template == null) {
            str = ""
        } else {
            require(Math.abs(x) < 128) { "x value out of range" }
            require(Math.abs(y) < 128) { "y value out of range" }
            val dx = if (x < 0) x + 256 else x
            val dy = if (y < 0) y + 256 else y
            str = String.format(
                template,
                (if (buttonDown) 1 else 0).toString(),
                dx.toString(),
                dy.toString(),
            )
        }
        stream.write(str)
    }

    /** Trackpad handling of one [MotionEvent]; R8-inlined into AdbClient.processNext. RECON-UNSURE: name. */
    // (inlined in C0180.m1876; param name "event")
    fun onMotionEvent(event: MotionEvent) {
        val rawX = event.rawX
        val rawY = event.rawY
        val scaledX = rawX * sensitivity
        val scaledY = sensitivity * rawY
        val pointerId = event.getPointerId(0)
        val now = System.currentTimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                position.x = rawX
                position.y = rawY
                position.xPow = scaledX
                position.yPow = scaledY
                movedSincePress = true
            }
            MotionEvent.ACTION_UP -> {
                if (position.isFocusDown && isWithinTapThreshold(rawX, rawY)) {
                    sendReport(0, 0, true)
                    pressed = false
                    sendReport(0, 0, false)
                }
                pressed = false
                if (!movedSincePress) {
                    sendReport(0, 0, false)
                    movedSincePress = true
                }
                position.roundedX = 0f
                position.roundedY = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                if (pointerId != position.pointerId) {
                    position.pointerId = pointerId
                    position.xPow = scaledX
                    position.yPow = scaledY
                }
                if (System.currentTimeMillis() - position.currentTimeMillis > 10) {
                    if (position.isFocusDown && !isWithinTapThreshold(rawX, rawY)) {
                        position.isFocusDown = false
                    }
                    val (remainderX, dx) = accumulate(scaledX, position.xPow, position.roundedX)
                    position.roundedX = remainderX
                    val (remainderY, dy) = accumulate(scaledY, position.yPow, position.roundedY)
                    position.roundedY = remainderY
                    sendReport(dx, dy, pressed)
                    position.xPow = scaledX
                    position.yPow = scaledY
                    position.currentTimeMillis = now
                }
                position.isFocusDown = movedSincePress
            }
            else -> when (event.actionMasked) {
                MotionEvent.ACTION_CANCEL -> {
                    position.isFocusDown = false
                    sendReport(0, 0, false)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    pressed = true
                    movedSincePress = false
                    sendReport(0, 0, true)
                }
                MotionEvent.ACTION_POINTER_UP -> pressed = false
            }
        }
    }
}

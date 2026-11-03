package com.jcapps.adbremote.utils

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule

/**
 * Press/hold listener: `callback(true)` when pressed (after 150 ms hold, or as a 100 ms tap pulse
 * on quick release) and `callback(false)` on release.
 */
// obf: n (OnTouchListener), q (TimerTask), o / p (SuspendLambdas)
@SuppressLint("ClickableViewAccessibility")
fun View.setOnPressListener(callback: (Boolean) -> Unit) {
    var isTouched = false
    var task: TimerTask? = null
    var isClicked = false
    setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTouched = true
                task = Timer().schedule(150L) {
                    if (isTouched) {
                        isClicked = true
                        CoroutineScope(Dispatchers.Main).launch {
                            callback(true)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                isTouched = false
                if (isClicked) {
                    callback(false)
                } else {
                    task?.cancel()
                    CoroutineScope(Dispatchers.Main).launch {
                        callback(true)
                        delay(100L)
                        callback(false)
                    }
                }
                isClicked = false
            }
            MotionEvent.ACTION_CANCEL -> {
                isTouched = false
                if (isClicked) {
                    callback(false)
                    isClicked = false
                }
            }
        }
        false
    }
}

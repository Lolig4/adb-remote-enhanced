package com.jcapps.adbremote.ui.intro

import android.app.ActivityOptions
import android.content.Intent
import android.gesture.GestureOverlayView
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.jcapps.adbremote.ui.MainActivity

/**
 * Drives the intro [viewPager] (user input disabled) from the [GestureOverlayView] via fake drags,
 * asking the current [BaseIntroFragment] whether paging is allowed.
 */
class PagerController(
    // obf: f5325
    private val pageCount: Int,
    // obf: f5324
    val viewPager: ViewPager2,
    viewPagerGestureOverlay: GestureOverlayView,
) {
    // obf: C0984

    // obf: f5326
    private var lastBlockedTime: Long = 0

    // obf: f5327
    var activity: IntroActivity? = null

    // obf: f5328
    private var lastX: Float = 0f

    // obf: f5329
    var isSwipeEnabled: Boolean = true

    // obf: f5330
    private var downX: Float = 0f

    // obf: f5331
    var onPageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    init {
        viewPager.isUserInputEnabled = false
        // obf: C2856 — all four callbacks forward to onTouch
        viewPagerGestureOverlay.addOnGestureListener(object : GestureOverlayView.OnGestureListener {
            override fun onGesture(overlay: GestureOverlayView, event: MotionEvent) = onTouch(event)
            override fun onGestureCancelled(overlay: GestureOverlayView, event: MotionEvent) = onTouch(event)
            override fun onGestureEnded(overlay: GestureOverlayView, event: MotionEvent) = onTouch(event)
            override fun onGestureStarted(overlay: GestureOverlayView, event: MotionEvent) = onTouch(event)
        })
    }

    // obf: m2900 (beginFakeDrag/fakeDragBy/endFakeDrag and the two helpers below were R8-inlined)
    private fun onTouch(event: MotionEvent?) {
        if (!shouldHandleTouch(event)) {
            return
        }
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                viewPager.beginFakeDrag()
            }
            MotionEvent.ACTION_MOVE -> {
                val x = event.x
                viewPager.fakeDragBy(x - lastX)
                lastX = x
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> viewPager.endFakeDrag()
        }
    }

    /**
     * Whether the gesture may drive the pager. A swipe towards the next page is refused while the
     * current page blocks it; the page is then notified at most once per second.
     */
    private fun shouldHandleTouch(event: MotionEvent?): Boolean {
        if (!isSwipeEnabled || event == null) {
            return false
        }
        if (event.action == MotionEvent.ACTION_DOWN) {
            downX = event.x
            event.y
        } else {
            if (event.action == MotionEvent.ACTION_UP) {
                viewPager.performClick()
            }
            val canMoveNext = activity?.currentPage?.canMoveNext(true) ?: true
            if (!canMoveNext && downX > event.x) {
                if (canNotifyBlocked()) {
                    activity?.currentPage?.onNextBlocked()
                }
                return false
            }
        }
        return isSwipeEnabled
    }

    private fun canNotifyBlocked(): Boolean {
        if (System.currentTimeMillis() - lastBlockedTime >= 1000) {
            lastBlockedTime = System.currentTimeMillis()
            return true
        }
        return false
    }

    // obf: m2901
    fun next() {
        if (viewPager.currentItem != pageCount - 1) {
            isSwipeEnabled = true
            setCurrentItem(viewPager.currentItem + 1, true)
            return
        }
        val activity = activity ?: return
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
            .putBoolean("intro_completed", true)
            .apply()
        activity.startActivity(Intent(activity, MainActivity::class.java), ActivityOptions.makeBasic().toBundle())
        activity.finish()
    }

    // obf: m2902
    fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        viewPager.endFakeDrag()
        val currentItem = viewPager.currentItem
        viewPager.setCurrentItem(item, smoothScroll)
        if (currentItem == 0 && item == 0) {
            onPageChangeCallback?.onPageSelected(0)
        }
    }

    // obf: m2903
    fun previous() {
        if (viewPager.currentItem != 0) {
            isSwipeEnabled = true
            setCurrentItem(viewPager.currentItem - 1, true)
        } else {
            activity?.finish()
        }
    }
}

package com.jcapps.adbremote.utils

import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView

/** Briefly flashes the pressed button's icon over the remote ("integratedButtonOverlay"). */
class ButtonPressOverlay(
    // obf: f9961
    private val iconView: AppCompatImageView,
) {
    // obf: C2660
    // obf: f9960
    var hideRunnable: Runnable? = null

    // obf: m5072
    fun show(@DrawableRes icon: Int) {
        iconView.apply {
            setImageResource(icon)
            visibility = View.VISIBLE
            alpha = 1.0f
            hideRunnable?.let { removeCallbacks(it) }
            // obf: RunnableC3897 case 0 / case 1
            val runnable = Runnable {
                animate().alpha(0.0f).setDuration(150L).withEndAction {
                    visibility = View.GONE
                }
            }
            hideRunnable = runnable
            postDelayed(runnable, 500L)
        }
    }

    // RECON-UNSURE: inlined into MainActivity.onDestroy (removeCallbacks on iconView + null);
    //  kept as a member because the fields are touched only from there.
    fun release() {
        hideRunnable?.let { iconView.removeCallbacks(it) }
        hideRunnable = null
    }
}

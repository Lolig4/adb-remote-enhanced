package com.jcapps.adbremote.ui.files

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ActivityFilesBinding

/**
 * Bottom "copying/moving file" bar of the Files screen (layout `ow` with cancel `dj` / paste `dv`
 * buttons). Slides in/out and moves the FAB plus the list's bottom padding along with it.
 *
 * RECON-UNSURE: class/member names (unregistered class C2346, claimed by files).
 */
class PasteBar(
    context: Context,
    // obf: f9131
    private val binding: ActivityFilesBinding,
) {
    // obf: C2346

    /** What the paste will do. */
    // obf: EnumC0062 (RECON-UNSURE name; constant names are original)
    enum class Mode {
        COPY,
        MOVE,
    }

    // obf: f9130
    var mode: Mode = Mode.COPY

    // obf: f9134
    var isShowing: Boolean = false

    // obf: f9133
    private val duration: Long = 250L

    // obf: f9136 — bar height (translation distance)
    private val barHeight: Float = context.resources.getDimension(R.dimen.paste_bar_height)

    // obf: f9135 — default list bottom padding
    private val listPadding: Float = context.resources.getDimension(R.dimen.files_list_bottom_padding)

    // obf: f9132 (C2247 case 16) — the FAB's initial bottom margin
    private val fabMargin: Int by lazy {
        (binding.fabAdd.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin
    }

    // obf: m4573
    fun show(mode: Mode, isDirectory: Boolean) {
        this.mode = mode
        val text = when (mode) {
            Mode.COPY -> if (isDirectory) R.string.copying_folder else R.string.copying_file
            Mode.MOVE -> if (isDirectory) R.string.moving_folder else R.string.moving_file
        }
        binding.textPasteInfo.setText(text)
        if (isShowing) return
        isShowing = true
        val bar = binding.layoutPasteBar
        bar.translationY = barHeight
        bar.visibility = View.VISIBLE
        bar.animate().translationY(0.0f).setDuration(duration).start()
        val fabParams = binding.fabAdd.layoutParams as CoordinatorLayout.LayoutParams
        val animator = ValueAnimator.ofInt(fabParams.bottomMargin, fabMargin + barHeight.toInt())
        animator.duration = duration
        // obf: C3527 case 1
        animator.addUpdateListener { valueAnimator ->
            fabParams.bottomMargin = valueAnimator.animatedValue as Int
            binding.fabAdd.layoutParams = fabParams
        }
        // obf: C1374 case 1
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                binding.listFiles.setPadding(0, 0, 0, (listPadding + barHeight).toInt())
            }
        })
        animator.start()
    }

    // obf: m4574
    fun hide() {
        if (!isShowing) return
        isShowing = false
        val bar = binding.layoutPasteBar
        bar.animate().translationY(barHeight).setDuration(duration)
            // obf: RunnableC3440 case 11
            .withEndAction { bar.visibility = View.GONE }
            .start()
        val fabParams = binding.fabAdd.layoutParams as CoordinatorLayout.LayoutParams
        val animator = ValueAnimator.ofInt(fabParams.bottomMargin, fabMargin)
        animator.duration = duration
        // obf: C3527 case 0
        animator.addUpdateListener { valueAnimator ->
            fabParams.bottomMargin = valueAnimator.animatedValue as Int
            binding.fabAdd.layoutParams = fabParams
        }
        // obf: C1374 case 0
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                binding.listFiles.setPadding(0, 0, 0, listPadding.toInt())
            }
        })
        animator.start()
    }
}

package com.jcapps.adbremote.views

import android.content.Context
import android.view.View
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ViewStateBinding
import com.jcapps.adbremote.model.Param

/**
 * Drives the shared state layout (`R.layout.view_state`, proposed `view_state`: lottie `l4` + message
 * `us`) for the [State]s DISCONNECTED / LOADING / EMPTY / SUCCESS.
 *
 * RECON-UNSURE: [Param] member names (`padding`, `animation`, `message`) — owner group "custom";
 * the toString proves `padding`, the other two decrypt keys have no plaintext comment.
 */
class StateView(
    // obf: f6191
    val binding: ViewStateBinding,
    // obf: f6190
    val params: StateParams,
) {
    // obf: C1292

    /** Current state. */
    // obf: f6193
    var state: State = State.DISCONNECTED
        private set // written only by setState(); avoids a JVM clash with fun setState(State)

    /**
     * When true (default), LOADING is only shown while the state view is already visible (the
     * screen's SwipeRefreshLayout shows reloads instead). RECON-UNSURE: name.
     */
    // obf: f6192
    var isSwipeRefreshEnabled: Boolean = true

    /** Whether the state view is currently shown (false after SUCCESS). */
    // obf: f6194
    var isShowing: Boolean = true

    constructor(binding: ViewStateBinding) : this(binding, defaultParams(binding.root.context))

    // obf: m3235
    fun setState(apiState: State) {
        state = apiState
        when (apiState) {
            State.DISCONNECTED -> {
                isShowing = true
                binding.root.visibility = View.VISIBLE
                val messageView = binding.textAppRequestingApplyIconPack
                messageView.visibility = View.VISIBLE
                params.disconnected.message?.let { messageView.setText(it) }
                val param = params.disconnected
                val padding = param.padding
                val lottie = binding.animationState
                lottie.setPadding(padding, 0, padding, 0)
                lottie.setAnimation(param.anim)
                lottie.playAnimation()
            }

            State.LOADING -> {
                if (!isSwipeRefreshEnabled || isShowing) {
                    binding.root.visibility = View.VISIBLE
                    binding.textAppRequestingApplyIconPack.visibility = View.GONE
                    val padding = params.loading.padding
                    val lottie = binding.animationState
                    lottie.setPadding(padding, 0, padding, 0)
                    lottie.setAnimation(params.loading.anim)
                    lottie.playAnimation()
                }
            }

            State.EMPTY -> {
                isShowing = true
                binding.root.visibility = View.VISIBLE
                val messageView = binding.textAppRequestingApplyIconPack
                messageView.visibility = View.VISIBLE
                params.empty.message?.let { messageView.setText(it) }
                val param = params.empty
                val padding = param.padding
                val lottie = binding.animationState
                lottie.setPadding(padding, 0, padding, 0)
                lottie.setAnimation(param.anim)
                lottie.playAnimation()
            }

            State.SUCCESS -> {
                isShowing = false
                binding.animationState.cancelAnimation()
                binding.root.visibility = View.GONE
            }
        }
    }

    companion object {
        // obf: f6189 (C3573 tag 23 = this companion)
        const val LOTTIE_LOADING = "lottie/loading.lottie"
        const val LOTTIE_LOADING_IMAGE = "lottie/loading_image.lottie"
        const val LOTTIE_DISCONNECTED = "lottie/disconnected.lottie"
        const val LOTTIE_EMPTY = "lottie/empty.lottie"

        /** Default params: disconnected (R.string.device_unreachable_try_again_reconnect), loading (no text), empty (R.string.theres_nothing_here). */
        // obf: C3573.m6176
        fun defaultParams(context: Context): StateParams {
            val padding = context.resources.getDimension(R.dimen.state_view_padding).toInt()
            return StateParams(
                Param(padding, LOTTIE_DISCONNECTED, R.string.device_unreachable_try_again_reconnect),
                Param(padding, LOTTIE_LOADING, null),
                Param(padding, LOTTIE_EMPTY, R.string.theres_nothing_here),
            )
        }
    }
}

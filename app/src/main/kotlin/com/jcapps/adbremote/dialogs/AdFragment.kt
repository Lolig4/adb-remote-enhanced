package com.jcapps.adbremote.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import coil.EventListener
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.DialogAdBinding
import com.jcapps.adbremote.model.AppConfig
import com.jcapps.adbremote.ui.BaseFragment
import com.jcapps.adbremote.utils.PlayStore
import com.jcapps.adbremote.utils.beginDelayedTransition
import com.jcapps.adbremote.utils.getParcelableCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen house ad ([AppConfig.Ad], argument "ad") shown over MainActivity: image (k9) opens
 * the ad link, the close button (ej) counts down `countdown` seconds before it is enabled.
 */
class AdFragment : BaseFragment<DialogAdBinding>() {
    // obf: C1044

    // obf: f5500
    private var job: Job? = null

    // obf: f5501
    private var ad: AppConfig.Ad? = null

    /** Seconds elapsed in the countdown. */
    // obf: f5502
    private var elapsedSeconds: Int = 0

    /** Called after the fragment removed itself (MainActivity C0808 case 12). RECON-UNSURE: name. */
    // obf: f5503
    var onDismiss: (() -> Unit)? = null

    // obf: mo3020 (C1277)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogAdBinding
        get() = DialogAdBinding::inflate

    override fun onDestroyView() {
        job?.cancel()
        super.onDestroyView()
    }

    override fun onPause() {
        job?.cancel()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (ad == null) {
            return
        }
        // obf: C0324 (re-read from the jadx fallback listing; launch mask 3 = default context)
        job = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && elapsedSeconds < ad!!.countdown) {
                delay(1000L)
                elapsedSeconds++
                // obf: C1913
                launch(Dispatchers.Main) {
                    updateCountdown()
                }
            }
            // obf: C3109
            launch(Dispatchers.Main) {
                remove()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ad = requireArguments().getParcelableCompat(ARG_AD, AppConfig.Ad::class.java)
        this.ad = ad
        if (ad == null) {
            remove()
            return
        }
        val imageLoader = ImageLoader.Builder(requireContext())
            // obf: C3733 case 11 (EventListener.Factory) wrapping C0885
            .eventListener(object : EventListener {
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    beginDelayedTransition()
                    val lottieView = binding.animationState
                    lottieView.cancelAnimation()
                    lottieView.visibility = View.GONE
                    binding.imageImagePlaceholder.visibility = View.VISIBLE
                }

                override fun onError(request: ImageRequest, result: ErrorResult) {
                    remove()
                }
            })
            // obf: m5312 (ImageLoaderOptions.respectCacheHeaders = false)
            .respectCacheHeaders(false)
            // obf: m5323 (CrossfadeTransition.Factory(100))
            .crossfade(100)
            .build()
        val imageView = binding.imageImagePlaceholder
        imageLoader.enqueue(ImageRequest.Builder(imageView.context).data(ad.image).target(imageView).build())
        // obf: C0583 case 2
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        // lambda case 0
        binding.ej.setOnClickListener {
            remove()
        }
        // lambda case 1
        binding.imageImagePlaceholder.setOnClickListener {
            with(PlayStore) {
                requireContext().launchLink(this@AdFragment.ad!!.link)
            }
        }
        binding.ej.isEnabled = this.ad!!.skippable
        if (this.ad!!.skippable) {
            binding.ej.setIconResource(R.drawable.ic_close_circle)
        }
        updateCountdown()
    }

    /** "Skip in N s" text of the close button. */
    // obf: m3019
    private fun updateCountdown() {
        val remaining = ad!!.countdown - elapsedSeconds
        binding.ej.text = resources.getQuantityString(R.plurals.b, remaining, remaining)
    }

    /** Removes this fragment (exit anim R.anim.slide_out_bottom) and notifies [onDismiss]. */
    // obf: m3021
    private fun remove() {
        val fragmentManager = requireActivity().supportFragmentManager
        fragmentManager.beginTransaction()
            .setCustomAnimations(0, R.anim.slide_out_bottom, 0, 0)
            .remove(this)
            .commit()
        onDismiss?.invoke()
    }

    companion object {
        // obf: static "ad" — RECON-UNSURE: companion shape; creation is inlined at C0350
        const val ARG_AD = "ad"
    }
}

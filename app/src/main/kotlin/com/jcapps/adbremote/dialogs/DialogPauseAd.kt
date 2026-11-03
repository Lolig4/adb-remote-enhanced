package com.jcapps.adbremote.dialogs

import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.doAfterTextChanged
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.jcapps.adbremote.AdbRemoteApp
import com.jcapps.adbremote.R
import com.jcapps.adbremote.api.remote.ApiClient
import com.jcapps.adbremote.databinding.DialogPauseAdBinding
import com.jcapps.adbremote.ntp.SafeTimeInfo
import com.jcapps.adbremote.ntp.SntpClient
import com.jcapps.adbremote.premium.ApplyPromoRequest
import com.jcapps.adbremote.premium.LifetimePromo
import com.jcapps.adbremote.premium.PremiumManager
import com.jcapps.adbremote.premium.Promo
import com.jcapps.adbremote.premium.Reward
import com.jcapps.adbremote.utils.FeatureUsage
import com.jcapps.adbremote.utils.PlayStore
import com.jcapps.adbremote.utils.beginDelayedTransition
import com.jcapps.adbremote.utils.getClipboardText
import com.jcapps.adbremote.utils.getGuideUrl
import com.jcapps.adbremote.utils.isSafe
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import kotlin.concurrent.schedule

/**
 * Premium / "pause ads" sheet: shows the current premium access (expiry or lifetime), lets the
 * user watch a rewarded ad for one more day, or apply a promo code (i0/i1, button d7).
 */
class DialogPauseAd : BaseBottomSheet<DialogPauseAdBinding>() {
    // obf: C2410

    // obf: f9290
    private var progressDrawable: IndeterminateDrawable<CircularProgressIndicatorSpec>? = null

    // obf: f9291
    private var job: Job? = null

    // obf: f9292 (C3079 case 6)
    private val dateFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("MMMM d, yyyy, h:mm a", Locale.getDefault())
    }

    /** Called from [onDestroyView] (MainActivity C1425 case 0). RECON-UNSURE: name. */
    // obf: f9293
    var onDismiss: (() -> Unit)? = null

    // obf: f9294
    private var rewardedAd: RewardedAd? = null

    // obf: mo1933 (C0091)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogPauseAdBinding
        get() = DialogPauseAdBinding::inflate

    override fun onDestroyView() {
        progressDrawable = null
        job?.cancel()
        rewardedAd = null
        onDismiss?.invoke()
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        // RECON-UNSURE: inlined app Context extension (receiver check "<this>") building the button spinner
        val spec = CircularProgressIndicatorSpec(context, null, 0, R.style.Widget_MaterialComponents_CircularProgressIndicator_ExtraSmall)
        progressDrawable = IndeterminateDrawable.createCircularDrawable(context, spec).apply {
            setVisible(true, true, true)
        }
        val access = PremiumManager.getAccess(requireContext())
        val guideUrl = getGuideUrl(requireContext(), "premium-access")
        val markdown = getString(R.string.gn) + " [" + getString(R.string.au) + "](" + guideUrl + ")"
        // obf: C0554 case 0 (= io.noties.markwon.linkify.LinkifyPlugin, R8-merged into the plugin group)
        val markwon = Markwon.builder(requireContext())
            .usePlugin(LinkifyPlugin.create())
            .build()
        markwon.setMarkdown(binding.ux, markdown)
        if (access == null || !access.isLifetime) {
            if (access?.promoCode != null) {
                binding.c7.visibility = View.VISIBLE
                binding.v1.text = access.promoCode
            }
        } else {
            showLifetimeAccess(access.promoCode ?: "")
        }
        if (access != null && !access.isLifetime) {
            val accessView = binding.uz
            accessView.visibility = View.VISIBLE
            accessView.text = getString(R.string.go, formatDate(access.totalEndsAt))
        }
        // Watch a rewarded ad (lambda case 0)
        binding.eo.setOnClickListener {
            beginDelayedTransition()
            binding.uv.visibility = View.GONE
            binding.eo.icon = progressDrawable
            binding.eo.isEnabled = false
            // obf: C2192 case 15 / C0817 case 0
            getSafeTime(
                onSuccess = { time -> loadRewardedAd(time) },
                onFailure = {
                    beginDelayedTransition()
                    binding.uv.setText(R.string.gi)
                    binding.uv.visibility = View.VISIBLE
                },
            )
        }
        // Show the promo code input (lambda case 1)
        binding.ei.setOnClickListener {
            beginDelayedTransition()
            binding.c8.visibility = View.GONE
            binding.pe.visibility = View.VISIBLE
        }
        // Unbind the applied promo code (lambda case 2)
        binding.em.setOnClickListener {
            val promoCode = binding.v1.text.toString()
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.h3)
                .setNegativeButton(R.string.cancel, null)
                // obf: DialogInterfaceOnClickListenerC0807 case 4
                .setPositiveButton(R.string.bf) { _, _ ->
                    binding.em.isEnabled = false
                    binding.em.icon = progressDrawable
                    // obf: C3629 case 6 / C0817 case 1
                    getSafeTime(
                        onSuccess = { time ->
                            val newAccess = PremiumManager.removePromo(requireContext(), time)
                            job = PremiumManager.updateDevice(
                                viewLifecycleOwner.lifecycleScope,
                                promoCode,
                                with(PlayStore) { requireContext().getAndroidId() },
                                true,
                                // obf: C0538 case 5
                                onSuccess = {
                                    binding.em.isEnabled = true
                                    binding.em.setIconResource(R.drawable.ic_rate)
                                    beginDelayedTransition()
                                    if (newAccess != null) {
                                        val accessView = binding.uz
                                        accessView.visibility = View.VISIBLE
                                        accessView.text = getString(R.string.go, formatDate(newAccess.totalEndsAt))
                                    }
                                    binding.ux.visibility = View.VISIBLE
                                    binding.eo.visibility = View.VISIBLE
                                    binding.ei.visibility = View.VISIBLE
                                    binding.c7.visibility = View.GONE
                                    binding.pe.visibility = View.GONE
                                    binding.c8.visibility = View.VISIBLE
                                },
                                // obf: C1920 case 4
                                onFailed = { _, message ->
                                    binding.em.isEnabled = true
                                    binding.em.setIconResource(R.drawable.ic_rate)
                                    MaterialAlertDialogBuilder(requireContext())
                                        .setMessage(message ?: getString(R.string.failed_connect_server))
                                        .setPositiveButton(R.string.ok, null)
                                        .show()
                                },
                            )
                        },
                        onFailure = {
                            binding.em.isEnabled = true
                            binding.em.setIconResource(R.drawable.ic_rate)
                            Toast.makeText(requireContext(), R.string.failed_connect_server, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                .show()
        }
        binding.i0.setOnEditorActionListener { _, actionId, _ ->
            val isDone = actionId == EditorInfo.IME_ACTION_DONE
            if (isDone) {
                applyPromo()
            }
            isDone
        }
        // obf: C3076 case 4
        binding.i0.doAfterTextChanged {
            binding.i1.error = null
        }
        // Paste from clipboard (lambda case 3)
        binding.i1.setEndIconOnClickListener {
            val clipboardText = requireContext().getClipboardText()
            binding.i0.text?.insert(0, clipboardText)
        }
        // Apply promo (lambda default case)
        binding.btnApply.setOnClickListener {
            applyPromo()
        }
    }

    /** Loads and shows the rewarded ad; [time] = NTP-safe current time. */
    // obf: C2192 case 15 (inlined lambda body)
    private fun loadRewardedAd(time: Long) {
        var isLoaded = false
        // obf: C0566 case 2 + RunnableC0115 case 9
        Timer().schedule(3000L) {
            if (!isLoaded && isSafe()) {
                requireActivity().runOnUiThread {
                    beginDelayedTransition()
                    binding.uv.setText(R.string.gw)
                    binding.uv.visibility = View.VISIBLE
                }
            }
        }
        val adRequest = AdRequest.Builder().build()
        // obf: C0330
        RewardedAd.load(
            requireContext(),
            "ca-app-pub-8938536730349592/8477110689",
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(p0: LoadAdError) {
                    if (isSafe()) {
                        isLoaded = true
                        rewardedAd = null
                        binding.eo.setIconResource(R.drawable.ic_play_arrow)
                        binding.eo.isEnabled = true
                        beginDelayedTransition()
                        binding.uv.setText(R.string.gi)
                        binding.uv.visibility = View.VISIBLE
                    }
                }

                override fun onAdLoaded(p0: RewardedAd) {
                    if (!isSafe()) {
                        return
                    }
                    isLoaded = true
                    rewardedAd = p0
                    binding.eo.setIconResource(R.drawable.ic_play_arrow)
                    binding.eo.isEnabled = true
                    binding.uv.visibility = View.GONE
                    // obf: C1321 case 0
                    rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewardedAd = null
                        }

                        override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                            rewardedAd = null
                            binding.uv.setText(R.string.gi)
                            binding.uv.visibility = View.VISIBLE
                        }
                    }
                    rewardedAd?.show(requireActivity()) {
                        FeatureUsage.isRewardEarned = true
                        val endsAt = PremiumManager.addAccess(requireContext(), time + 30000, Reward)
                        val accessView = binding.uz
                        accessView.visibility = View.VISIBLE
                        accessView.text = getString(R.string.go, formatDate(endsAt))
                    }
                }
            },
        )
    }

    /** Gets the NTP-safe time via the app's SntpClient, then [onSuccess] (timestamp) / [onFailure]. RECON-UNSURE: name. */
    // obf: m4643 (listener = C1171 case 23)
    private fun getSafeTime(onSuccess: (Long) -> Unit, onFailure: () -> Unit) {
        val application = requireActivity().application
        // RECON-UNSURE: inlined app extension (receiver check "<this>"): anti-tamper cast check
        if (application !is AdbRemoteApp) {
            Process.killProcess(Process.myPid())
            throw IllegalStateException()
        }
        val sntpClient = application.sntpClient
        // obf: C1171 case 23
        job = sntpClient.sync(
            viewLifecycleOwner.lifecycleScope,
            object : SntpClient.Listener {
                override fun onSuccess(timeInfo: SafeTimeInfo) {
                    onSuccess.invoke(timeInfo.timestamp)
                }

                override fun onError(t: Throwable) {
                    onFailure()
                }
            },
        )
    }

    /** Applies the promo code from i0 on IO (C2817). */
    // obf: m4644
    private fun applyPromo() {
        val promoCode = binding.i0.text.toString()
        val button = binding.btnApply
        button.isEnabled = false
        button.icon = progressDrawable
        // obf: C2817 (+ C4001 / C3940 on Main)
        job = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = ApplyPromoRequest(
                    promoCode = promoCode,
                    deviceId = with(PlayStore) { requireContext().getAndroidId() },
                    deviceVersion = Build.VERSION.SDK_INT,
                    deviceName = PlayStore.getDeviceName(),
                )
                val response = ApiClient.createPremiumService().apply(request)
                val result = response.body()
                val code = response.code()
                withContext(Dispatchers.Main) {
                    // obf: C4001
                    val applyButton = binding.btnApply
                    applyButton.isEnabled = true
                    applyButton.icon = null
                    if (code == 401) {
                        binding.i1.error = getString(R.string.cx)
                    } else if (code == 429) {
                        binding.i1.error = getString(R.string.cz)
                    } else if (code != 200 || result == null) {
                        binding.i1.error = getString(R.string.failed_connect_server)
                    } else if (result.status) {
                        // obf: C0753 case 11 / C0817 case 2
                        getSafeTime(
                            onSuccess = { time ->
                                val grant = if (result.endsAt == null) {
                                    LifetimePromo(promoCode)
                                } else {
                                    Promo(promoCode, result.endsAt)
                                }
                                val endsAt = PremiumManager.addAccess(requireContext(), time, grant)
                                if (grant is LifetimePromo) {
                                    showLifetimeAccess(promoCode)
                                } else {
                                    beginDelayedTransition()
                                    val accessView = binding.uz
                                    accessView.visibility = View.VISIBLE
                                    accessView.text = getString(R.string.go, formatDate(endsAt))
                                    binding.pe.visibility = View.GONE
                                    binding.c7.visibility = View.VISIBLE
                                    binding.v1.text = promoCode
                                }
                            },
                            onFailure = {
                                beginDelayedTransition()
                                binding.i1.error = getString(R.string.failed_connect_server)
                            },
                        )
                    } else {
                        binding.i1.error = result.message
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // obf: C3940
                    val applyButton = binding.btnApply
                    applyButton.isEnabled = true
                    applyButton.icon = null
                    binding.i1.error = getString(R.string.failed_connect_server)
                }
            }
        }
    }

    /** Lifetime premium UI: hides ad/promo options and shows [text] in the access card. */
    // obf: m4645
    private fun showLifetimeAccess(text: String) {
        beginDelayedTransition()
        val titleView = binding.uz
        titleView.visibility = View.VISIBLE
        titleView.setText(R.string.gp)
        binding.ux.visibility = View.GONE
        binding.eo.visibility = View.GONE
        binding.ei.visibility = View.GONE
        binding.c7.visibility = View.VISIBLE
        binding.v1.text = text
        binding.pe.visibility = View.GONE
        binding.c8.visibility = View.GONE
    }

    // obf: m4646
    private fun formatDate(time: Long): String = dateFormat.format(Date(time))
}

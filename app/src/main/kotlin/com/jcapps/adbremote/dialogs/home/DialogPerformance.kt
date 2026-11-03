package com.jcapps.adbremote.dialogs.home

import android.app.Dialog
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.transition.TransitionManager
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.CpuAdapter
import com.jcapps.adbremote.databinding.DialogPerformanceBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.utils.Logger
import com.jcapps.adbremote.utils.SpacingItemDecoration
import com.jcapps.adbremote.viewmodels.AppViewModel
import com.jcapps.adbremote.views.StateView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Live CPU (per core) and memory usage of the connected device, refreshed every second. */
class DialogPerformance(
    // obf: f9897
    private val adbApiViewModel: AppViewModel,
) : BaseBottomSheet<DialogPerformanceBinding>() {
    // obf: C2638

    // obf: f9893
    private var isLoaded: Boolean = false

    /** Called from [onDestroy] once data was shown (MainActivity C1425 case 20). RECON-UNSURE: name. */
    // obf: f9894
    var onDismiss: (() -> Unit)? = null

    // obf: f9895
    private var job: Job? = null

    // obf: f9896 (lambda case 0)
    private val stateView: StateView by lazy { StateView(binding.includeViewState) }

    // obf: f9898 (lambda case 1)
    private val cpuAdapter: CpuAdapter by lazy { CpuAdapter(requireContext()) }

    // obf: mo1933 (C0934)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogPerformanceBinding
        get() = DialogPerformanceBinding::inflate

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // obf: m4416 mask 6 (both defaults)
        return createBottomSheetDialog(savedInstanceState, true, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isLoaded) {
            onDismiss?.invoke()
        }
    }

    override fun onDestroyView() {
        job?.cancel()
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // RECON-UNSURE: writes the view model's backing flow (obf f3437) — core-app must not keep it private
        adbApiViewModel._performance.update { null }
        val recyclerView = binding.list
        recyclerView.addItemDecoration(
            SpacingItemDecoration(recyclerView.resources.getDimension(R.dimen.performance_list_spacing).roundToInt()),
        )
        recyclerView.adapter = cpuAdapter
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        // obf: C3321 -> C1895
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // obf: C1631 / C2533 case 0
                launch {
                    adbApiViewModel.state.collect { state ->
                        if (state != null) {
                            // RECON-UNSURE: inlined app Fragment extension (receiver check "<this>")
                            TransitionManager.beginDelayedTransition(requireView().parent as ViewGroup)
                            stateView.setState(state)
                        }
                    }
                }
                // obf: C2101 / C2533 case 1
                launch {
                    adbApiViewModel.performance.collect { performance ->
                        if (performance != null) {
                            if (!isLoaded) {
                                Logger.log("Device's performance loaded.")
                            }
                            binding.cardPerformance.visibility = View.VISIBLE
                            val memory = performance.memory
                            val available = memory.available
                            val total = memory.total
                            val used = total - available
                            val availableText = Formatter.formatShortFileSize(requireContext(), available)
                            val totalText = Formatter.formatShortFileSize(requireContext(), total)
                            val usedText = Formatter.formatShortFileSize(requireContext(), used)
                            val hundred = 100L
                            val usedPercent = (used * hundred) / total
                            val text = getString(
                                R.string.use_available_total,
                                usedText,
                                usedPercent,
                                availableText,
                                hundred - usedPercent,
                                totalText,
                            )
                            binding.textPerformance.text = text
                            binding.progress.setProgressCompat(usedPercent.toInt(), true)
                            if (!isLoaded) {
                                isLoaded = true
                                Logger.log(text)
                            }
                            cpuAdapter.differ.submitList(performance.cpuList)
                        }
                    }
                }
            }
        }
        Logger.log("Loading device performance ...")
        adbApiViewModel.loadPerformance(true)
        // obf: C1093
        job = lifecycleScope.launch {
            while (isActive) {
                delay(1000L)
                adbApiViewModel.loadPerformance(false)
            }
        }
    }
}

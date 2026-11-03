package com.jcapps.adbremote.dialogs.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.TransitionManager
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.TitleValueAdapter
import com.jcapps.adbremote.databinding.DialogDeviceInfoBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.TitleValue
import com.jcapps.adbremote.utils.Logger
import com.jcapps.adbremote.viewmodels.AppViewModel
import com.jcapps.adbremote.views.State
import com.jcapps.adbremote.views.StateView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Device properties of the connected device (on-device server `getDeviceInfo`). */
class DialogDeviceInfo(
    // obf: f5266
    private val adbApiViewModel: AppViewModel,
) : BaseBottomSheet<DialogDeviceInfoBinding>() {
    // obf: C0961

    /** Called from [onDestroy] once info was shown (MainActivity C1425 case 21). RECON-UNSURE: name. */
    // obf: f5263
    var onDismiss: (() -> Unit)? = null

    // obf: f5264
    private var isLoaded: Boolean = false

    // obf: f5265 (C2247 case 9)
    private val stateView: StateView by lazy { StateView(binding.includeViewState) }

    // obf: f5267
    private var job: Job? = null

    // obf: mo1933 (C2382)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogDeviceInfoBinding
        get() = DialogDeviceInfoBinding::inflate

    override fun onDestroy() {
        super.onDestroy()
        if (isLoaded) {
            onDismiss?.invoke()
        }
    }

    override fun onDestroyView() {
        job?.cancel()
        // RECON-UNSURE: writes the view model's backing flow (obf f3433) — core-app must not keep it private
        adbApiViewModel._deviceInfo.update { null }
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // obf: C2837 -> C2843
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // obf: C2381 / C0183 case 0
                launch {
                    adbApiViewModel.state.collect { state ->
                        if (state != null && state != State.SUCCESS) {
                            stateView.setState(state)
                        }
                    }
                }
                // obf: C2656 / C0183 case 1
                launch {
                    adbApiViewModel.deviceInfo.collect { deviceInfo ->
                        if (deviceInfo != null) {
                            Logger.log("Device info loaded.")
                            // obf: C3956
                            job = lifecycleScope.launch {
                                val dataList = listOf(
                                    TitleValue(R.string.manufacturer, deviceInfo.manufacturer),
                                    TitleValue(R.string.brand, deviceInfo.brand),
                                    TitleValue(R.string.model, deviceInfo.model),
                                    TitleValue(R.string.board, deviceInfo.board),
                                    TitleValue(R.string.device, deviceInfo.device),
                                    TitleValue(R.string.android_version, deviceInfo.androidVersion),
                                    TitleValue(R.string.sdk_version, String.format(Locale.getDefault(), "%d", deviceInfo.sdkVersion)),
                                    TitleValue(R.string.incremental, deviceInfo.incremental),
                                    TitleValue(R.string.supported_abis, deviceInfo.supportedABIs.joinToString(", ")),
                                    TitleValue(R.string.memory_info, deviceInfo.memoryInfo.trim()),
                                    TitleValue(R.string.cpu_info, deviceInfo.cpuInfo.trim()),
                                    TitleValue(R.string.battery_info, deviceInfo.batteryInfo.trim()),
                                )
                                // obf: C0903
                                withContext(Dispatchers.Main) {
                                    // RECON-UNSURE: inlined app Fragment extension (receiver check "<this>")
                                    TransitionManager.beginDelayedTransition(requireView().parent as ViewGroup)
                                    stateView.setState(State.SUCCESS)
                                    binding.list.adapter = TitleValueAdapter(dataList)
                                    isLoaded = true
                                }
                            }
                        }
                    }
                }
            }
        }
        Logger.log("Loading device info ...")
        adbApiViewModel.loadDeviceInfo()
    }
}

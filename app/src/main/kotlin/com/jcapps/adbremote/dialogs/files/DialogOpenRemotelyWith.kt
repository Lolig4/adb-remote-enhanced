package com.jcapps.adbremote.dialogs.files

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.files.SupportedAppAdapter
import com.jcapps.adbremote.api.remote.ApiClient
import com.jcapps.adbremote.api.remote.BaseResponse
import com.jcapps.adbremote.databinding.DialogOpenRemotelyWithBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.SupportedApp
import com.jcapps.adbremote.utils.AppIconLoader
import com.jcapps.adbremote.utils.SpacingItemDecoration
import com.jcapps.adbremote.views.State
import com.jcapps.adbremote.views.StateView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Bottom sheet listing the device's apps that can open a file remotely (via
 * [SupportedAppAdapter]); dismisses itself if the mime type or device IP is missing.
 * Binding: DialogOpenRemotelyWithBinding.
 */
class DialogOpenRemotelyWith : BaseBottomSheet<DialogOpenRemotelyWithBinding>() {
    // obf: C1481

    /** Mime type of the remote file (query `mime_type`). */
    // obf: f6692
    var mimeType: String? = null

    /** JSON of the [com.jcapps.adbremote.model.IntentParams.FilePath] (query `file_path`). */
    // obf: f6691
    var filePath: String? = null

    /** Device IP the API / icon loader talk to. */
    // obf: f6694
    var ipAddress: String? = null

    // obf: f6693
    private lateinit var supportedAppAdapter: SupportedAppAdapter

    /** Invoked with the app the user picked (the sheet dismisses itself afterwards). */
    // obf: f6695
    var onAppSelected: ((SupportedApp) -> Unit)? = null

    // obf: f6697 (lambda ထဋဠအရသဟ case 0)
    private val loadingView: StateView by lazy { StateView(binding.includeViewState) }

    // obf: f6696 (lambda ထဋဠအရသဟ case 1)
    private val appIconLoader: AppIconLoader by lazy { AppIconLoader(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (mimeType == null || ipAddress == null) {
            dismiss()
            return
        }
        appIconLoader.ipAddress = ipAddress
        // RECON-UNSURE: original C0988 was an inner class reading f6696/f6695 directly; the
        // standalone adapter (other group) takes the loader + click body instead.
        supportedAppAdapter = SupportedAppAdapter(requireContext(), appIconLoader) { app ->
            onAppSelected?.invoke(app)
            dismiss()
        }
        val recyclerView = binding.list
        recyclerView.addItemDecoration(
            SpacingItemDecoration(recyclerView.resources.getDimension(R.dimen.list_spacing).roundToInt()),
        )
        recyclerView.adapter = supportedAppAdapter
        loadingView.setState(State.SUCCESS)
        // obf: C1062
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val response: BaseResponse<List<SupportedApp>>? = runCatching {
                ApiClient.create(ipAddress).getSupportedApps(mimeType!!, filePath, null)
            }.getOrNull()
            // obf: C0446
            withContext(Dispatchers.Main) {
                val state = if (response == null || !response.status) {
                    State.DISCONNECTED
                } else {
                    val data = response.data
                    if (data == null || data.isEmpty()) State.EMPTY else State.SUCCESS
                }
                loadingView.setState(state)
                supportedAppAdapter.differ.submitList(response?.data ?: emptyList(), null)
            }
        }
    }

    // obf: mo1933 — binding inflater
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogOpenRemotelyWithBinding = DialogOpenRemotelyWithBinding::inflate
}

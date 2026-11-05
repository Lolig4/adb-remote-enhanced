package com.jcapps.adbremote.dialogs.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.jcapps.adbremote.Constants
import com.jcapps.adbremote.R
import com.jcapps.adbremote.api.remote.ApiClient
import com.jcapps.adbremote.databinding.DialogNtpBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.utils.openGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read / set / delete (empty input) the NTP server of the connected device. */
class DialogNtp : BaseBottomSheet<DialogNtpBinding>() {
    // obf: C2326

    // obf: f9073
    private var progressDrawable: IndeterminateDrawable<CircularProgressIndicatorSpec>? = null

    // obf: f9074
    private var ipAddress: String? = null

    // obf: mo1933 (C0793)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogNtpBinding
        get() = DialogNtpBinding::inflate

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // lambda case 0
        binding.btnHelp.setOnClickListener {
            openGuide(requireContext(), "guide/how-to-use-ntp")
        }
        val context = requireContext()
        // RECON-UNSURE: inlined app Context extension (receiver check "<this>") building the button spinner
        val spec = CircularProgressIndicatorSpec(context, null, 0, R.style.Widget_MaterialComponents_CircularProgressIndicator_ExtraSmall)
        progressDrawable = IndeterminateDrawable.createCircularDrawable(context, spec).apply {
            setVisible(true, true, true)
        }
        ipAddress = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString("current_device_ip", null)
        binding.inputEnterTextHere.apply {
            // obf: C3207 case 2
            doAfterTextChanged {
                binding.inputLayoutText.helperText = getString(R.string.delete_ntp_server_configuration_set)
                if (it.toString().isEmpty()) {
                    // obf: RunnableC0115 case 8
                    postDelayed({ showDropDown() }, 200L)
                }
            }
            setSimpleItems(Constants.ntpHosts)
            threshold = 1
            // obf: ViewOnFocusChangeListenerC3364 case 1
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && text.toString().isBlank()) {
                    showDropDown()
                }
            }
        }
        // lambda default case
        binding.btnSave.setOnClickListener {
            val server = binding.inputEnterTextHere.text.toString()
            setLoading(true)
            // obf: C1358
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val (isSuccess, error) = runCatching {
                    val api = ApiClient.create(ipAddress)
                    val response = if (server.isBlank()) api.deleteNtpServer() else api.setNtpServer(server)
                    if (response.status) Pair(true, null) else Pair(false, response.data)
                }.getOrElse { Pair(false, getString(R.string.failed_connect_device)) }
                // obf: C3768
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    if (isSuccess) {
                        binding.inputLayoutText.helperText = getString(R.string.ntp_server_has_been_configured)
                        binding.inputLayoutText.setHelperTextColor(ContextCompat.getColorStateList(requireContext(), R.color.success))
                    } else {
                        binding.inputLayoutText.error = error
                    }
                }
            }
        }
        setLoading(true)
        // obf: C0489
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val (server, error) = runCatching {
                val response = ApiClient.create(ipAddress).getNtpServer()
                if (response.status) Pair(response.data, null) else Pair(null, response.data)
            }.getOrElse { Pair(null, getString(R.string.failed_connect_device)) }
            // obf: C2239
            withContext(Dispatchers.Main) {
                setLoading(false)
                if (server != null) {
                    binding.inputEnterTextHere.setText(server, false)
                } else {
                    binding.inputLayoutText.error = error
                }
            }
        }
    }

    // obf: m4518
    private fun setLoading(isLoading: Boolean) {
        binding.inputLayoutText.isEnabled = !isLoading
        binding.btnSave.isEnabled = !isLoading
        if (isLoading) {
            binding.btnSave.icon = progressDrawable
        } else {
            binding.btnSave.setIconResource(R.drawable.ic_save)
        }
    }
}

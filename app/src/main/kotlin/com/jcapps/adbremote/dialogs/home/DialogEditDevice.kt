package com.jcapps.adbremote.dialogs.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.DialogEditDeviceBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.AdbDevice
import com.jcapps.adbremote.utils.getParcelableCompat

/** Edits name / IP / port of a saved [AdbDevice] (argument [ARG_DEVICE]); reports via [onSuccess]. */
class DialogEditDevice : BaseBottomSheet<DialogEditDeviceBinding>() {

    // Rebuild: swipe-to-dismiss like the keyboard sheet (no collapsed half state)
    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog =
        createBottomSheetDialog(savedInstanceState, true, true)
    // obf: C3006

    // obf: f10840
    var onSuccess: ((AdbDevice) -> Unit)? = null

    // obf: mo1933 (C0248)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogEditDeviceBinding
        get() = DialogEditDeviceBinding::inflate

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val device = requireArguments().getParcelableCompat(ARG_DEVICE, AdbDevice::class.java) ?: return
        binding.inputDeviceName.setText(device.name)
        binding.inputN192168XX.setText(device.ipAddress)
        binding.inputPort.setText(device.port.toString())
        // obf: C3207 case 0
        binding.inputN192168XX.doAfterTextChanged {
            binding.textEditingIpAddressPortMay.visibility =
                if (binding.inputN192168XX.text?.toString() == device.ipAddress &&
                    binding.inputPort.text?.toString() == device.port.toString()
                ) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }
        // obf: C3207 case 1
        binding.inputPort.doAfterTextChanged {
            binding.textEditingIpAddressPortMay.visibility =
                if (binding.inputN192168XX.text?.toString() == device.ipAddress &&
                    binding.inputPort.text?.toString() == device.port.toString()
                ) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }
        // obf: ViewOnClickListenerC2219 case 3
        binding.btnCancel.setOnClickListener { dismiss() }
        // obf: ViewOnClickListenerC2376 case 4
        binding.btnSave.setOnClickListener {
            binding.inputLayoutName.error = null
            binding.inputLayoutIpAddress.error = null
            binding.inputLayoutPort.error = null
            val name = binding.inputDeviceName.text.toString()
            val ipAddress = binding.inputN192168XX.text.toString()
            val port = binding.inputPort.text.toString().toIntOrNull() ?: -1
            if (name.isBlank()) {
                binding.inputLayoutName.error = getString(R.string.device_name_should_not_empty)
            } else if (!DialogConnect.IP_ADDRESS_REGEX.matches(ipAddress)) {
                binding.inputLayoutIpAddress.error = getString(R.string.invalid_ip_address)
            } else if (port !in 0..65535) {
                // NOTE: original sets the port error on the IP layout (hp), kept as-is.
                binding.inputLayoutIpAddress.error = getString(R.string.invalid_port)
            } else {
                onSuccess?.invoke(
                    device.copy(name = name, serial = "$ipAddress:$port", ipAddress = ipAddress, port = port),
                )
                dismiss()
            }
        }
    }

    companion object {
        // obf: f10839 (C2352 = this companion)
        const val ARG_DEVICE = "arg_device"

        /** Inlined at its only call site (DialogDevices, C0152 case 1). */
        fun newInstance(device: AdbDevice, onSuccess: (AdbDevice) -> Unit): DialogEditDevice {
            val dialog = DialogEditDevice()
            dialog.arguments = Bundle().apply { putParcelable(ARG_DEVICE, device) }
            dialog.onSuccess = onSuccess
            return dialog
        }
    }
}

package com.jcapps.adbremote.dialogs.home

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.DevicesAdapter
import com.jcapps.adbremote.dao.FavApps
import com.jcapps.adbremote.databinding.DialogDevicesBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.AdbDevice
import com.jcapps.adbremote.prefs.DevicesStore
import com.jcapps.adbremote.utils.SpacingItemDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Saved devices: select (becomes default), edit, delete; "add" button opens the connect flow. */
class DialogDevices : BaseBottomSheet<DialogDevicesBinding>() {
    // obf: C0881

    /** A device was deleted; on close, [onDeviceSelected] is told `null`. RECON-UNSURE: name. */
    // obf: f5034
    private var isDeviceDeleted: Boolean = false

    /** "Add device" button callback (MainActivity C1425 case 25). RECON-UNSURE: name. */
    // obf: f5035
    var onAddDevice: (() -> Unit)? = null

    // obf: f5036
    private var devices: MutableList<AdbDevice> = ArrayList()

    // obf: f5037 (lambda case 0)
    private val dataStore: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    /** Selected / updated device, or `null` after deletions (MainActivity C1433 case 4). */
    // obf: f5038
    var onDeviceSelected: ((AdbDevice?) -> Unit)? = null

    // obf: f5039
    private lateinit var deviceAdapter: DevicesAdapter

    // obf: f5040 (lambda case 1)
    private val devicesStore: DevicesStore by lazy { DevicesStore(dataStore) }

    // obf: mo1933 (C3679)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogDevicesBinding
        get() = DialogDevicesBinding::inflate

    override fun onDestroyView() {
        if (isDeviceDeleted) {
            onDeviceSelected?.invoke(null)
        }
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // obf: ViewOnClickListenerC2219 case 2
        binding.btnAddDevice.setOnClickListener {
            onAddDevice?.invoke()
            dismiss()
        }
        deviceAdapter = DevicesAdapter(
            // obf: C0152 case 0
            onSelected = { position, device ->
                if (device.isDefault) {
                    dismiss()
                } else {
                    val selectedDevice = device.copy(isDefault = true)
                    for (it in devices) {
                        it.unsetDefault()
                    }
                    devices[position] = selectedDevice
                    // obf: C2552 case 1
                    saveDevices(devices) {
                        onDeviceSelected?.invoke(selectedDevice)
                        dismiss()
                    }
                }
            },
            // obf: C0152 case 1
            onEdit = { position, device ->
                // obf: C1709 case 2
                val dialog = DialogEditDevice.newInstance(device) { updatedDevice ->
                    devices[position] = updatedDevice
                    // obf: C2127 case 0
                    saveDevices(devices) {
                        // obf: C2552 case 0
                        val onUpdated = {
                            onDeviceSelected?.invoke(updatedDevice)
                            dismiss()
                        }
                        // obf: C1462
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            if (device.ipAddress != updatedDevice.ipAddress) {
                                FavApps(dataStore).moveApps(device.ipAddress, updatedDevice.ipAddress)
                            }
                            // obf: C0072
                            withContext(Dispatchers.Main) {
                                onUpdated()
                            }
                        }
                    }
                }
                dialog.show(childFragmentManager, DialogEditDevice::class.java.name)
            },
            // obf: C0152 case 2
            onDelete = { position, device ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_device)
                    .setMessage(R.string.deleting_device_also_remove_list)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        devices.removeAt(position)
                        // obf: C2552 case 2
                        saveDevices(devices) {
                            FavApps(dataStore).removeApps(device.ipAddress)
                            deviceAdapter.differ.submitList(devices.toList())
                            binding.textTheresNothingHere.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                            isDeviceDeleted = true
                        }
                    }
                    .show()
            },
        )
        val recyclerView = binding.list
        recyclerView.addItemDecoration(
            SpacingItemDecoration(recyclerView.resources.getDimension(R.dimen.devices_list_spacing).roundToInt()),
        )
        recyclerView.adapter = deviceAdapter
        // obf: C2004
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            devices = devicesStore.getDevices().toMutableList()
            // obf: C0367
            withContext(Dispatchers.Main) {
                // RECON-UNSURE: obf `Util.toImmutableList` (R8-merged with okhttp's) = copy of the list
                deviceAdapter.differ.submitList(devices.toList())
            }
        }
    }

    // obf: m2710 (body C3118)
    private fun saveDevices(devices: MutableList<AdbDevice>, onSaved: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // RECON-UNSURE: inlined DevicesStore member (param "devices") = put(devices, "adb_devices")
            devicesStore.put(devices, DevicesStore.KEY_ADB_DEVICES)
            // obf: C1373
            withContext(Dispatchers.Main) {
                onSaved()
            }
        }
    }
}

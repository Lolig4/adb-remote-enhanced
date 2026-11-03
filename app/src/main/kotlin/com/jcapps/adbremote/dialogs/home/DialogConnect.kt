package com.jcapps.adbremote.dialogs.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.R
import com.jcapps.adbremote.api.adb.AdbBinary
import com.jcapps.adbremote.api.adb.connect
import com.jcapps.adbremote.api.adb.devices
import com.jcapps.adbremote.api.adb.pair
import com.jcapps.adbremote.api.adb.tcpip
import com.jcapps.adbremote.databinding.DialogConnectBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.AdbDevice
import com.jcapps.adbremote.model.CommandResult
import com.jcapps.adbremote.model.CommandResultType
import com.jcapps.adbremote.model.CommandStatus
import com.jcapps.adbremote.model.DeviceState
import com.jcapps.adbremote.model.DeviceStatus
import com.jcapps.adbremote.prefs.DevicesStore
import com.jcapps.adbremote.utils.Logger
import com.jcapps.adbremote.utils.beginDelayedTransition
import com.jcapps.adbremote.utils.isSafe
import com.jcapps.adbremote.utils.isWifiConnected
import com.jcapps.adbremote.utils.killAdbServer
import com.jcapps.adbremote.utils.networkStateFlow
import com.jcapps.adbremote.utils.openGuide
import com.jcapps.adbremote.utils.showNetworkWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Connect a new device over Wi-Fi: optional `adb pair` (wireless debugging), `adb connect`,
 * `adb devices -l` until the device is ready, optional `adb tcpip`, then saves the [AdbDevice]
 * as default and reports it via [onDeviceConnected].
 *
 * Binding ids: ip hn/hp, port hz/ht, pairing switch sx, pairing code hw/hr, pairing port hx/hs,
 * tcpip port i2/hu, status us, warning (no Wi-Fi) uu, progress card km, progress pb,
 * buttons d8 (close) d_ (connect) dh / di (guides).
 */
class DialogConnect : BaseBottomSheet<DialogConnectBinding>() {
    // obf: C0434

    // obf: f3851
    private var job: Job? = null

    // obf: f3852 (C2247 case 8) / getter m2155
    private val adbBinary: AdbBinary by lazy { AdbBinary(requireContext()) }

    /** Set once `adb devices` reported the device ready (m2159). */
    // obf: f3853
    private var isDeviceReady: Boolean = false

    /** First connect attempt uses a 2000 ms timeout, later ones 1000 ms. */
    // obf: f3854
    private var isFirstAttempt: Boolean = true

    /**
     * Result callback (set by MainActivity, C1433 case 6). Invoked with `null` from
     * [onDestroyView] when a connection was started but no device became ready.
     * RECON-UNSURE: name.
     */
    // obf: f3855
    var onDeviceConnected: ((AdbDevice?) -> Unit)? = null

    /** Count of TIMEOUT results (>= 5 switches to the longer timeout message). */
    // obf: f3856
    private var timeoutCount: Int = 0

    /** True once the connection process was started (connect button). */
    // obf: f3857
    private var isConnecting: Boolean = false

    // obf: mo1933 (C0519)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogConnectBinding
        get() = DialogConnectBinding::inflate

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // obf: m4416 mask 6 (both defaults)
        return createBottomSheetDialog(savedInstanceState, true, false)
    }

    override fun onDestroyView() {
        job?.cancel()
        if (!isDeviceReady && isConnecting) {
            onDeviceConnected?.invoke(null)
        }
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!requireContext().isWifiConnected()) {
            val warning = binding.textNetworkWarning
            warning.visibility = View.VISIBLE
            warning.setText(R.string.connect_both_devices_same_wifi)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            // obf: C2529 -> C2597
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // obf: C2745 case 1
                requireContext().networkStateFlow().collect { state ->
                    showNetworkWarning(binding.textNetworkWarning, state)
                }
            }
        }
        // obf: C3220 case 2
        binding.switchWirelessDebuggingMode.setOnCheckedChangeListener { _, isChecked ->
            beginDelayedTransition()
            val visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.layoutPairing.visibility = visibility
            binding.inputLayoutTcpIpPort.visibility = visibility
        }
        // obf: anonymous click listener case 0
        binding.btnCancel.setOnClickListener { dismiss() }
        // obf: anonymous click listener case 1
        binding.btnConnect.setOnClickListener {
            binding.inputLayoutIpAddress.error = null
            binding.inputLayoutPort.error = null
            binding.inputLayoutPairingCode.error = null
            binding.inputLayoutPairingPort.error = null
            binding.inputLayoutTcpIpPort.error = null
            val ipAddress = binding.inputN192168XX.text.toString()
            val port = binding.inputPort.text.toString().toIntOrNull() ?: -1
            val pairingCode = binding.inputPairingCode.text.toString().toIntOrNull() ?: -1
            val pairingPort = binding.inputPairingPort.text.toString().toIntOrNull() ?: -1
            val tcpipPort = binding.inputTcpIpPort.text.toString().toIntOrNull() ?: -1
            val isPairing = binding.switchWirelessDebuggingMode.isChecked
            if (!IP_ADDRESS_REGEX.matches(ipAddress)) {
                binding.inputLayoutIpAddress.error = getString(R.string.invalid_ip_address)
            } else if (port !in 0..65535) {
                binding.inputLayoutPort.error = getString(R.string.invalid_port)
            } else if (isPairing && pairingPort !in 0..65535) {
                binding.inputLayoutPairingPort.error = getString(R.string.invalid_port)
            } else if (isPairing && pairingCode < 0) {
                binding.inputLayoutPairingCode.error = getString(R.string.invalid_pairing_code)
            } else if (isPairing && tcpipPort !in 0..65535) {
                binding.inputLayoutTcpIpPort.error = getString(R.string.invalid_port)
            } else {
                binding.cardConnectStatus.visibility = View.VISIBLE
                Logger.log("Connection process started")
                showStatus(R.string.disconnecting_devices)
                binding.btnConnect.isEnabled = false
                binding.progress.visibility = View.VISIBLE
                job = killAdbServer {
                    isConnecting = true
                    if (isPairing) {
                        showStatus(getString(R.string.pairing, "$ipAddress:$pairingPort"))
                        job = adbBinary.pair(ipAddress, pairingPort, pairingCode) { success, result ->
                            // obf: C4013
                            if (success) {
                                showStatus(R.string.paired_connecting_device)
                                connect(0, ipAddress, port) {
                                    showStatus(R.string.connected_changing_tcp_ip_mode)
                                    enableTcpip(0, "$ipAddress:$port", tcpipPort) {
                                        reconnectOverTcpip(ipAddress, tcpipPort)
                                    }
                                }
                            } else {
                                showError(result)
                            }
                        }
                    } else {
                        showStatus(getString(R.string.connecting, "$ipAddress:$port"))
                        job = adbBinary.connect(ipAddress, port, if (isFirstAttempt) 2000L else 1000L) { type, result ->
                            if (type == CommandResultType.SUCCESS) {
                                showStatus(R.string.connected_getting_device_ready)
                                // obf: C1709 case 0
                                waitForDevice(0) { deviceInfo -> onDeviceReady(deviceInfo, ipAddress, port) }
                            } else {
                                showError(result)
                            }
                        }
                    }
                }
                if (isFirstAttempt) {
                    isFirstAttempt = false
                }
            }
        }
        // obf: anonymous click listener case 2
        binding.btnHowConnect.setOnClickListener { openGuide(requireContext(), "guide/how-to-connect") }
        // obf: anonymous click listener case 3
        binding.btnWhat.setOnClickListener { openGuide(requireContext(), "guide/wifi-pairing-mode") }
    }

    /**
     * After `adb tcpip`: kill the adb server, forget the known pairing hosts and reconnect on the
     * tcpip port after 3 s. (C0897 cases 0..2)
     */
    private fun reconnectOverTcpip(ipAddress: String, port: Int) {
        // obf: C0897 case 0
        showStatus(R.string.tcp_ip_mode_changed_reconnecting)
        killAdbServer {
            // obf: C0897 case 1
            val knownHosts = File(adbBinary.filesDirPath, ".android" + File.separator + "adb_known_hosts.pb")
            if (knownHosts.exists()) {
                knownHosts.delete()
            }
            job = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                // obf: C2890
                delay(3000L)
                withContext(Dispatchers.Main) {
                    // obf: C1970
                    connect(0, ipAddress, port) {
                        // obf: C0897 case 2
                        showStatus(R.string.connected_getting_device_ready)
                        // obf: C1709 case 1
                        waitForDevice(0) { deviceInfo -> onDeviceReady(deviceInfo, ipAddress, port) }
                    }
                }
            }
        }
    }

    /** `adb tcpip` with retry (status R.string.changing_tcp_ip_mode_takes from the 2nd attempt). */
    // obf: m2156 (result = C3969 tcpip case)
    private fun enableTcpip(attempt: Int, serial: String, port: Int, onSuccess: () -> Unit) {
        if (attempt >= 2) {
            showStatus(R.string.changing_tcp_ip_mode_takes)
        }
        job = adbBinary.tcpip(serial, port) { type, result ->
            // obf: C3969 default case
            Logger.log("type: " + type.label() + ", output: " + result.output + ", error: " + result.error)
            when (type) {
                CommandResultType.SUCCESS -> onSuccess()
                CommandResultType.RETRY -> if (isSafe()) {
                    enableTcpip(attempt + 1, serial, port, onSuccess)
                }
                CommandResultType.FAILURE -> if (attempt > 5) {
                    showError(result)
                } else {
                    enableTcpip(attempt + 1, serial, port, onSuccess)
                }
            }
        }
    }

    /** `adb devices -l` with retry (status R.string.connected_getting_device_ready_takes from the 2nd attempt). */
    // obf: m2157 (result = C3753)
    private fun waitForDevice(attempt: Int, callback: (DeviceState) -> Unit) {
        if (attempt >= 2) {
            showStatus(R.string.connected_getting_device_ready_takes)
        }
        job = adbBinary.devices { type, devices, result ->
            // obf: C3753
            Logger.log("type: " + type.label() + ", devices: " + devices?.size)
            when (type) {
                CommandResultType.SUCCESS -> {
                    val deviceInfo = devices?.firstOrNull()
                    if (deviceInfo != null && deviceInfo.state == DeviceStatus.CONNECTED) {
                        callback(deviceInfo)
                    } else if (isSafe()) {
                        // RECON-UNSURE: for a null device jadx only shows the `!isRemoving` part of
                        //  the isSafe() check (duplicated block); the full check is used for both.
                        job = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            // obf: C0774
                            delay(1000L)
                            waitForDevice(attempt + 1, callback)
                        }
                    }
                }
                CommandResultType.RETRY -> waitForDevice(attempt + 1, callback)
                CommandResultType.FAILURE -> showError(result)
            }
        }
    }

    // obf: m2158
    private fun showStatus(@StringRes resId: Int) {
        val text = getString(resId)
        showStatus(text)
    }

    /** Logs the ready device, builds the default [AdbDevice] and saves it (C0125 on IO). */
    // obf: m2159
    private fun onDeviceReady(deviceState: DeviceState, ipAddress: String, port: Int) {
        Logger.log("Device ready!")
        val state = when (deviceState.state) {
            DeviceStatus.CONNECTED -> "connected"
            DeviceStatus.OFFLINE -> "offline"
            DeviceStatus.AUTHORIZING -> "authorizing"
            DeviceStatus.UNAUTHORIZED -> "unauthorized"
        }
        val model = deviceState.model
        Logger.log(deviceState.serial + ", model: " + model + " [" + state + ']')
        isDeviceReady = true
        val device = AdbDevice(model ?: getString(R.string.unknown_model), deviceState.serial, ipAddress, port, true)
        binding.btnConnect.isEnabled = true
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // obf: C0125
            val devicesStore = DevicesStore(PreferenceManager.getDefaultSharedPreferences(requireContext()))
            val devices = devicesStore.getDevices().toMutableList()
            for (it in devices) {
                it.unsetDefault()
            }
            val index = devices.indexOfFirst { it.ipAddress == device.ipAddress }
            if (index == -1) {
                devices.add(device)
            } else {
                devices[index] = device
            }
            devicesStore.put(devices, DevicesStore.KEY_ADB_DEVICES)
            launch(Dispatchers.Main) {
                // obf: C4030
                onDeviceConnected?.invoke(device)
                dismiss()
            }
        }
    }

    /** `adb connect` with a 4000 ms timeout and retry (status R.string.connecting_device_takes_too_long from the 3rd attempt). */
    // obf: m2160 (result = C3969 connect case)
    private fun connect(attempt: Int, ipAddress: String, port: Int, onSuccess: () -> Unit) {
        if (attempt >= 3) {
            showStatus(R.string.connecting_device_takes_too_long)
        }
        job = adbBinary.connect(ipAddress, port, 4000L) { type, result ->
            // obf: C3969 case 0
            Logger.log("type: " + type.label() + ", output: " + result.output + ", error: " + result.error)
            when (type) {
                CommandResultType.SUCCESS -> onSuccess()
                CommandResultType.RETRY -> if (attempt > 10) {
                    showError(result)
                } else {
                    connect(attempt + 1, ipAddress, port, onSuccess)
                }
                CommandResultType.FAILURE -> if (attempt > 10) {
                    showError(result)
                } else {
                    viewLifecycleOwner.lifecycleScope.launch {
                        // obf: C2489
                        delay(1000L)
                        connect(attempt + 1, ipAddress, port, onSuccess)
                    }
                }
            }
        }
    }

    /** Shows [text] in the status view on the UI thread while the sheet is attached. */
    // obf: m2161 (runnable = RunnableC1435 case 2)
    private fun showStatus(text: String) {
        if (isRemoving || activity == null || isDetached || !isAdded || view == null) {
            return
        }
        requireActivity().runOnUiThread {
            beginDelayedTransition()
            binding.textAppRequestingApplyIconPack.text = text
            Logger.log(text)
        }
    }

    /** Re-enables the UI and shows/logs the error text of a failed [result]. */
    // obf: m2162
    private fun showError(result: CommandResult) {
        if (!isSafe()) return
        binding.btnConnect.isEnabled = true
        binding.progress.visibility = View.INVISIBLE
        beginDelayedTransition()
        val output = result.output
        val error = result.error
        val message: String = when (result.type) {
            CommandStatus.OK -> if (output != null && output.contains("failed to authenticate to", true)) {
                getString(R.string.allow_usb_debugging_target_device_2)
            } else if (error == null || error.isBlank() || error.contains("daemon not running", true)) {
                if (output == null || output.isBlank()) "" else output.trim().toString()
            } else {
                error.trim().toString()
            }
            CommandStatus.ERROR -> error?.trim()?.toString() ?: output?.trim()?.toString() ?: getString(R.string.unexpected_error_occurred)
            CommandStatus.TIMEOUT -> {
                timeoutCount++
                getString(if (timeoutCount >= 5) R.string.connection_timed_out_ensure_ip else R.string.connection_timed_out_try_again)
            }
        }
        binding.textAppRequestingApplyIconPack.text = message
        Logger.log(message)
        val typeName = when (result.type) {
            CommandStatus.OK -> "OK"
            CommandStatus.ERROR -> "ERROR"
            CommandStatus.TIMEOUT -> "TIMEOUT"
        }
        Logger.log(
            "type: " + typeName + ", exitCode: " + result.exitCode + ", output: " + output + ", error: " + error,
        )
    }

    /** `OK` / `RETRY` / `FAILURE` label of a command result type (inlined, leftover "<this>"). */
    private fun CommandResultType.label(): String = when (this) {
        CommandResultType.SUCCESS -> "OK"
        CommandResultType.RETRY -> "RETRY"
        CommandResultType.FAILURE -> "FAILURE"
    }

    companion object {
        // obf: f3850 — RECON-UNSURE: name
        val IP_ADDRESS_REGEX = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$")
    }
}

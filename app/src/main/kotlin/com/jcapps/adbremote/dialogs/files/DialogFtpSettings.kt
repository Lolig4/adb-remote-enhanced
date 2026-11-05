package com.jcapps.adbremote.dialogs.files

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.jcapps.adbremote.R
import com.jcapps.adbremote.api.adb.ServerInstaller
import com.jcapps.adbremote.databinding.DialogFtpSettingsBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.FTPSettings
import com.jcapps.adbremote.model.InputValidation
import com.jcapps.adbremote.model.ServerState
import com.jcapps.adbremote.model.SimpleResult
import com.jcapps.adbremote.prefs.DevicesStore
import com.jcapps.adbremote.prefs.JsonPrefs
import com.jcapps.adbremote.utils.SecureStorage
import com.jcapps.adbremote.utils.apiServiceFor
import com.jcapps.adbremote.utils.beginDelayedTransition
import com.jcapps.adbremote.utils.copyToClipboard
import com.jcapps.adbremote.utils.getParcelableCompat
import com.jcapps.adbremote.utils.openGuide
import com.jcapps.adbremote.utils.serverState
import com.jcapps.adbremote.utils.toFtpConfig
import com.jcapps.adbremote.utils.updateServerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Bottom sheet to edit a device's [FTPSettings] (port, dirs, anonymous/credentials, passive,
 * read-only) with live input validation, and to start/stop the on-device FTP server.
 * Binding: DialogFtpSettingsBinding.
 */
class DialogFtpSettings : BaseBottomSheet<DialogFtpSettingsBinding>() {
    // obf: C2789

    // obf: f10295 — snapshot to detect changes
    private lateinit var originalFTPSettings: FTPSettings

    // obf: f10296
    private lateinit var ipAddress: String

    // obf: f10297 (lambda ဏဇနထညဎက case 1) — port of the ADB server (5555 by default)
    private val adbPort: Int by lazy {
        DevicesStore(PreferenceManager.getDefaultSharedPreferences(requireContext())).getDefault()?.port ?: 5555
    }

    // obf: f10298 — working copy being edited
    private lateinit var ftpSettings: FTPSettings

    /** Invoked with the edited settings whenever they are saved. */
    // obf: f10299
    var onSettingsChanged: ((FTPSettings) -> Unit)? = null

    /** Whether settings were passed in (i.e. already saved before). */
    // obf: f10300
    private var hasSavedSettings: Boolean = false

    // obf: f10301 — in-flight start/stop coroutine
    private var serverJob: Job? = null

    // obf: f10302 (lambda ဏဇနထညဎက case 0)
    private val basePrefs: JsonPrefs by lazy {
        JsonPrefs(PreferenceManager.getDefaultSharedPreferences(requireContext()))
    }

    // obf: f10303
    private val inputValidation: InputValidation = InputValidation().apply {
        usernameValidated = true
        portValidated = true
        customRootDirValidated = true
        fallbackRootDirValidated = true
    }

    override fun onDestroyView() {
        serverJob?.cancel()
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (inputValidation.isValid() && originalFTPSettings != ftpSettings) {
            saveSettings()
        }
        super.onDismiss(dialog)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ipAddress = arguments?.getString(KEY_IP_ADDRESS, null)
            ?: throw IllegalStateException("IP Address argument is required.")

        // obf: ဂအဋဇဍဓပ case 0
        binding.btnHelp.setOnClickListener {
            openGuide(requireContext(), "guide/how-to-use-files")
        }

        val savedSettings = arguments?.getParcelableCompat(KEY_FTP_SETTINGS, FTPSettings::class.java)
        hasSavedSettings = savedSettings != null
        ftpSettings = savedSettings ?: FTPSettings(
            2121,
            null,
            DEFAULT_ROOT_DIRS.first(),
            false,
            true,
            false,
            "admin",
            Random.nextInt(100000, 999999).toString(),
        )
        originalFTPSettings = ftpSettings.copy()

        // obf: ဂအဋဇဍဓပ case 1
        binding.layoutFtpServer.setOnClickListener {
            when (serverState.value) {
                ServerState.NOT_STARTED -> startServer()
                ServerState.STARTING -> {
                    setServerState(ServerState.NOT_STARTED)
                    // obf: RunnableC0222 case 0
                    binding.layoutFtpServer.postDelayed({ startServer() }, 200L)
                }
                ServerState.RUNNING -> stopServer()
                ServerState.STOPPING -> {
                    setServerState(ServerState.RUNNING)
                    // obf: RunnableC0222 case 1
                    binding.layoutFtpServer.postDelayed({ stopServer() }, 200L)
                }
            }
        }
        updateUrl()

        // obf: ဂအဋဇဍဓပ case 2
        binding.layoutFtpUrl.setOnClickListener {
            val url = binding.textFtpUrl.text.toString()
            requireContext().copyToClipboard(url)
            Toast.makeText(requireContext(), R.string.copied_clipboard, Toast.LENGTH_SHORT).show()
        }

        setCredentialsEnabled(!ftpSettings.anonymousLogin)
        binding.switchAnonymousLogin.apply {
            isChecked = ftpSettings.anonymousLogin
            // obf: ထနဃဖဒနဗ case 0
            setOnCheckedChangeListener { _, isChecked ->
                setCredentialsEnabled(!isChecked)
                ftpSettings.anonymousLogin = isChecked
                inputValidation.usernameValidated = if (isChecked) {
                    true
                } else {
                    !binding.inputUsername.text?.toString()?.trim().isNullOrEmpty()
                }
                updateUrl()
                updateValidation()
            }
        }

        binding.inputUsername.apply {
            append(ftpSettings.username)
            // obf: C2644 case 0
            doAfterTextChanged { editable ->
                val username = editable?.toString()?.trim()
                inputValidation.usernameValidated = !username.isNullOrEmpty()
                if (inputValidation.usernameValidated) {
                    ftpSettings.username = username!!
                }
                updateUrl()
                updateValidation()
            }
        }

        binding.inputPassword.apply {
            append(ftpSettings.password ?: "")
            // obf: C2644 case 1
            doAfterTextChanged { editable ->
                ftpSettings.password = editable?.toString()
            }
        }

        binding.inputPort.apply {
            append(ftpSettings.port.toString())
            // obf: C2644 case 2
            doAfterTextChanged { editable ->
                val port = editable?.toString()?.toIntOrNull()
                inputValidation.portValidated = port != null && port in 0..65535 &&
                    !listOf(5037, 5038, 5555, ServerInstaller.SERVER_CONSTANT, adbPort).contains(port)
                if (inputValidation.portValidated) {
                    ftpSettings.port = port!!
                }
                updateUrl()
                updateValidation()
            }
        }

        val rootDirTypes = requireContext().resources.getStringArray(R.array.internal_storage_custom_path_entries)
        binding.dropdownRootFolderType.apply {
            setSimpleItems(rootDirTypes)
            setText(rootDirTypes[if (ftpSettings.defaultDir == null) 0 else 1], false)
            // obf: C1923 case 0
            setOnItemClickListener { _, _, position, _ ->
                val custom = position == 1
                setCustomRootDirVisible(custom)
                val text = binding.dropdownRootFolderPath.text?.toString()
                ftpSettings.defaultDir = if (custom && !text.isNullOrEmpty()) text else null
            }
        }
        setCustomRootDirVisible(ftpSettings.defaultDir != null)

        binding.dropdownRootFolderPath.apply {
            setSimpleItems(DEFAULT_ROOT_DIRS)
            setText(ftpSettings.defaultDir ?: DEFAULT_ROOT_DIRS.first(), false)
            // obf: C3720 case 0
            doAfterTextChanged {
                val dir = text?.toString()
                inputValidation.customRootDirValidated = !dir.isNullOrEmpty()
                if (inputValidation.customRootDirValidated) {
                    ftpSettings.defaultDir = dir
                }
                updateValidation()
            }
        }

        binding.dropdownFallbackRootFolder.apply {
            setSimpleItems(DEFAULT_ROOT_DIRS)
            setText(ftpSettings.fallbackDir, false)
            // obf: C3720 case 1
            doAfterTextChanged {
                val dir = text?.toString()
                inputValidation.fallbackRootDirValidated = !dir.isNullOrEmpty()
                if (inputValidation.fallbackRootDirValidated) {
                    ftpSettings.fallbackDir = dir!!
                }
                updateValidation()
            }
        }

        binding.switchPassiveMode.apply {
            isChecked = ftpSettings.passiveMode
            // obf: ထနဃဖဒနဗ case 1
            setOnCheckedChangeListener { _, isChecked -> ftpSettings.passiveMode = isChecked }
        }

        binding.switchReadOnly.apply {
            isChecked = ftpSettings.readOnly
            // obf: ထနဃဖဒနဗ case 2
            setOnCheckedChangeListener { _, isChecked -> ftpSettings.readOnly = isChecked }
        }

        // obf: C3482 (+ collector C2745 case 2)
        viewLifecycleOwner.lifecycleScope.launch {
            serverState.collect { setServerState(it) }
        }
    }

    // obf: mo1933 — binding inflater
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogFtpSettingsBinding = DialogFtpSettingsBinding::inflate

    private fun InputValidation.isValid(): Boolean =
        usernameValidated && portValidated && customRootDirValidated && fallbackRootDirValidated

    /** Shows/hides the server error text. */
    // obf: m5203
    private fun setErrorVisible(visible: Boolean) {
        binding.textFtpError.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Hands the settings to the listener and stores them (encrypted) under "ftp_settings". */
    // obf: m5204
    private fun saveSettings() {
        onSettingsChanged?.invoke(ftpSettings)
        basePrefs.putEncrypted(ftpSettings, KEY_FTP_SETTINGS)
    }

    /** Enables the start button / copy row only for valid input and shows field errors. */
    // obf: m5205
    private fun updateValidation() {
        val valid = inputValidation.isValid()
        binding.layoutFtpServer.isEnabled = valid
        binding.switchFtpSettings.isEnabled = valid
        binding.textFtpStatus.isEnabled = valid
        binding.textNotStarted.isEnabled = valid
        binding.inputLayoutUsername.error = if (inputValidation.usernameValidated) null else getString(R.string.username_required)
        binding.inputLayoutPortFtp.error = if (inputValidation.portValidated) null else getString(R.string.port_range_must_between_0)
        binding.inputLayoutRootFolderPath.error = if (inputValidation.customRootDirValidated) null else getString(R.string.root_folder_path_required)
        binding.inputLayoutFallbackRootFolder.error = if (inputValidation.fallbackRootDirValidated) null else getString(R.string.fallback_root_folder_required)
    }

    /** Enables/disables all settings inputs (locked while the server runs). */
    // obf: m5206
    private fun setInputsEnabled(enabled: Boolean) {
        binding.switchAnonymousLogin.isEnabled = enabled
        setCredentialsEnabled(enabled)
        binding.inputLayoutPortFtp.isEnabled = enabled
        binding.inputLayoutRootFolder.isEnabled = enabled
        binding.inputLayoutRootFolderPath.isEnabled = enabled
        binding.inputLayoutFallbackRootFolder.isEnabled = enabled
        binding.switchPassiveMode.isEnabled = enabled
        binding.switchReadOnly.isEnabled = enabled
    }

    /** Stops the on-device FTP server. */
    // obf: m5207
    private fun stopServer() {
        setErrorVisible(false)
        val apiService = apiServiceFor(ipAddress)
        // obf: C1464
        serverJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                updateServerState(ServerState.STOPPING)
                val response = apiService.stopFtp()
                updateServerState(if (response.status) ServerState.NOT_STARTED else ServerState.RUNNING)
                SimpleResult(status = response.status, message = if (response.status) response.data else response.message ?: response.data)
            }.getOrElse { SimpleResult(status = false, message = it.message) }
            // obf: C2377
            withContext(Dispatchers.Main) { showResultError(result) }
        }
    }

    // obf: m5208
    private fun setCredentialsEnabled(enabled: Boolean) {
        binding.inputLayoutUsername.isEnabled = enabled
        binding.inputLayoutPassword.isEnabled = enabled
    }

    /** Shows the custom root directory input. */
    // obf: m5209
    private fun setCustomRootDirVisible(visible: Boolean) {
        beginDelayedTransition()
        binding.inputLayoutRootFolderPath.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Reflects the server run-state in the switch, status text and input lock. */
    // obf: m5210
    private fun setServerState(state: ServerState) {
        when (state) {
            ServerState.NOT_STARTED -> {
                setInputsEnabled(true)
                binding.switchFtpSettings.isChecked = false
                binding.textNotStarted.setText(R.string.not_started)
            }
            ServerState.STARTING -> {
                setInputsEnabled(false)
                binding.switchFtpSettings.isChecked = true
                binding.textNotStarted.setText(R.string.starting)
            }
            ServerState.RUNNING -> {
                setInputsEnabled(false)
                binding.switchFtpSettings.isChecked = true
                binding.textNotStarted.setText(R.string.running)
            }
            ServerState.STOPPING -> {
                setInputsEnabled(false)
                binding.switchFtpSettings.isChecked = false
                binding.textNotStarted.setText(R.string.stopping)
            }
        }
    }

    /** Saves the settings if needed and starts the on-device FTP server with them. */
    // obf: m5211
    private fun startServer() {
        if (hasSavedSettings) {
            if (originalFTPSettings != ftpSettings) {
                saveSettings()
            }
        } else {
            saveSettings()
        }
        setErrorVisible(false)
        val apiService = apiServiceFor(ipAddress)
        val config = ftpSettings.toFtpConfig(ipAddress)
        // obf: C0083
        serverJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                updateServerState(ServerState.STARTING)
                val response = apiService.startFtp(config)
                updateServerState(if (response.status) ServerState.RUNNING else ServerState.NOT_STARTED)
                SimpleResult(status = response.status, message = if (response.status) response.data else response.message ?: response.data)
            }.getOrElse { SimpleResult(status = false, message = it.message) }
            // obf: C3794
            withContext(Dispatchers.Main) { showResultError(result) }
        }
    }

    // obf: C2377 / C3794 (identical bodies)
    private fun showResultError(result: SimpleResult) {
        val message = result.message
        if (!result.status && !message.isNullOrEmpty()) {
            setErrorVisible(true)
            binding.textFtpError.text = message
        }
    }

    /** Shows the ftp:// URL to connect to. */
    // obf: m5212
    private fun updateUrl() {
        val sb = StringBuilder()
        sb.append("ftp://")
        if (!ftpSettings.anonymousLogin) {
            sb.append(ftpSettings.username + '@')
        }
        sb.append(ipAddress + ':' + ftpSettings.port)
        binding.textFtpUrl.text = sb.toString()
    }

    companion object {
        // obf: f10293 (C3573 tag 4)

        private const val KEY_FTP_SETTINGS = "ftp_settings"
        private const val KEY_IP_ADDRESS = "ip_address"

        /** Root-directory candidates; the first one is the default. */
        // obf: f10294
        val DEFAULT_ROOT_DIRS: Array<String> =
            arrayOf("/storage/emulated/0", "/sdcard", "/storage/emulated/999", "/storage/emulated/10")

        /** Saved FTP settings ("ftp_settings", encrypted JSON), or null. */
        // obf: C3573.m6171
        fun loadFtpSettings(basePrefs: JsonPrefs): FTPSettings? {
            var settings: FTPSettings? = null
            val json = basePrefs.dataStore.getString(KEY_FTP_SETTINGS, null)
            if (json != null) {
                val decrypted = SecureStorage.decrypt(json)
                if (decrypted != null) {
                    try {
                        // obf: C1434
                        settings = basePrefs.gson.fromJson(decrypted, object : TypeToken<FTPSettings>() {}.type)
                    } catch (e: JsonSyntaxException) {
                    }
                }
            }
            return settings
        }

        /** Creates and shows the sheet for [ipAddress] (inlined at its only call site). */
        // obf: C3573 (show helper, inlined into FilesActivity.m885)
        fun show(fragmentManager: FragmentManager, ipAddress: String, ftpSettings: FTPSettings?): DialogFtpSettings {
            val dialog = DialogFtpSettings()
            dialog.arguments = bundleOf(KEY_IP_ADDRESS to ipAddress, KEY_FTP_SETTINGS to ftpSettings)
            dialog.show(fragmentManager, DialogFtpSettings::class.java.name)
            return dialog
        }
    }
}

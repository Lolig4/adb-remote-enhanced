package com.jcapps.adbremote.dialogs.files

import android.app.Dialog
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.Toast
import androidx.core.view.iterator
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.files.ProgressFilesAdapter
import com.jcapps.adbremote.api.adb.AdbBinary
import com.jcapps.adbremote.api.adb.connect
import com.jcapps.adbremote.databinding.DialogFileConflictBinding
import com.jcapps.adbremote.databinding.DialogFileProgressBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.CommandResultType
import com.jcapps.adbremote.model.ProgressFTPFile
import com.jcapps.adbremote.model.ProgressState
import com.jcapps.adbremote.model.SelectedFile
import com.jcapps.adbremote.model.ServerState
import com.jcapps.adbremote.model.SimpleResult
import com.jcapps.adbremote.model.TransferType
import com.jcapps.adbremote.model.UiProgressFile
import com.jcapps.adbremote.prefs.DevicesStore
import com.jcapps.adbremote.prefs.JsonPrefs
import com.jcapps.adbremote.provider.ftp.FtpAuthority
import com.jcapps.adbremote.provider.ftp.FtpClientHelper
import com.jcapps.adbremote.provider.ftp.FtpPath
import com.jcapps.adbremote.transfer.ConflictAction
import com.jcapps.adbremote.transfer.FileDownloader
import com.jcapps.adbremote.transfer.FileUploader
import com.jcapps.adbremote.transfer.FtpFileCopier
import com.jcapps.adbremote.transfer.TransferListener
import com.jcapps.adbremote.utils.AdbServiceLauncher
import com.jcapps.adbremote.utils.Logger
import com.jcapps.adbremote.utils.apiServiceFor
import com.jcapps.adbremote.utils.beginDelayedTransition
import com.jcapps.adbremote.utils.generateUniqueName
import com.jcapps.adbremote.utils.getParcelableCompat
import com.jcapps.adbremote.utils.getPath
import com.jcapps.adbremote.utils.listFilesRecursively
import com.jcapps.adbremote.utils.mimeType
import com.jcapps.adbremote.utils.serverState
import com.jcapps.adbremote.utils.toFtpConfig
import com.jcapps.adbremote.utils.toTreeDocumentFile
import com.jcapps.adbremote.utils.updateServerState
import com.jcapps.adbremote.views.State
import com.jcapps.adbremote.views.StateView
import java.util.Locale
import java8.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Bottom sheet running and showing a file transfer (upload files / upload folder / download /
 * copy / cut) with per-file progress ([ProgressFilesAdapter]); pauses while the FTP server is
 * down, reconnects the device and resumes. Binding: DialogFileProgressBinding.
 */
class DialogFileProgress : BaseBottomSheet<DialogFileProgressBinding>(), TransferListener {
    // obf: C2631

    /** State of the main action button. */
    // obf: EnumC2116
    enum class ButtonState {
        PAUSE,
        RESUME,
        OK,
    }

    // obf: f9863
    private lateinit var basePrefs: JsonPrefs

    // obf: f9864
    private lateinit var adapter: ProgressFilesAdapter

    // obf: f9865 (C3079 case 5)
    private val locale: Locale by lazy { Locale.getDefault() }

    // obf: f9866
    private var fileUploader: FileUploader? = null

    /** True once every item was processed. */
    // obf: f9867
    private var isCompleted: Boolean = false

    // obf: f9868
    private lateinit var loadingView: StateView

    /** Invoked when the sheet's view is destroyed, with whether the transfer completed. */
    // obf: f9869
    var onDismissed: ((Boolean) -> Unit)? = null

    // obf: f9870
    private var fileDownloader: FileDownloader? = null

    // obf: f9871
    private var ftpFileCopier: FtpFileCopier? = null

    // obf: f9872
    private var adbServiceLauncher: AdbServiceLauncher? = null

    // obf: f9873
    private lateinit var authority: FtpAuthority

    // obf: f9874
    private var buttonState: ButtonState = ButtonState.PAUSE

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        createBottomSheetDialog(savedInstanceState, true, true)

    override fun onDestroyView() {
        adbServiceLauncher?.unregisterReceiver()
        onDismissed?.invoke(isCompleted)
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadingView = StateView(binding.includeViewState)
        loadingView.isSwipeRefreshEnabled = false
        loadingView.setState(State.SUCCESS)
        setTotalPercent(0)

        // obf: C3951 (+ collector C3416 case 1)
        lifecycleScope.launch {
            var collected = false
            serverState.collect { state ->
                if (collected) {
                    if (state == ServerState.RUNNING) {
                        beginDelayedTransition()
                        setListVisible(true)
                        setState(State.SUCCESS)
                        setButtonEnabled(true)
                        resumeTransfers(false)
                    } else if (state == ServerState.NOT_STARTED) {
                        beginDelayedTransition()
                        setState(State.DISCONNECTED)
                        setListVisible(false)
                        setButtonEnabled(true)
                        setButtonState(ButtonState.RESUME)
                    }
                } else {
                    collected = true
                }
            }
        }

        basePrefs = JsonPrefs(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        // obf: C3191 case 0 — restart the FTP server with the saved settings
        val launcher = AdbServiceLauncher(requireActivity(), onDisconnected = {
            val ipAddress = basePrefs.dataStore.getString("current_device_ip", null)
            val ftpSettings = DialogFtpSettings.loadFtpSettings(basePrefs)
            val apiService = apiServiceFor(ipAddress!!)
            val config = ftpSettings!!.toFtpConfig(ipAddress)
            // obf: C3260
            lifecycleScope.launch(Dispatchers.IO) {
                val result = runCatching {
                    updateServerState(ServerState.STARTING)
                    val response = apiService.startFtp(config)
                    updateServerState(if (response.status) ServerState.RUNNING else ServerState.NOT_STARTED)
                    SimpleResult(status = response.status, message = response.data)
                }.getOrElse { SimpleResult(status = false, message = it.message) }
                // obf: C1058 (empty body)
                withContext(Dispatchers.Main) { result }
            }
        })
        adbServiceLauncher = launcher
        launcher.registerReceiver()

        adapter = ProgressFilesAdapter(requireContext())
        binding.list.adapter = adapter
        // obf: ဉဖဋဏဂငဋ case 0
        binding.btnCancel.setOnClickListener {
            fileUploader?.cancel()
            fileDownloader?.cancel()
            ftpFileCopier?.cancel()
            dismiss()
        }

        val arguments = requireArguments()
        val transferType = if (Build.VERSION.SDK_INT >= 34) {
            arguments.getSerializable(KEY_PROGRESS_TYPE, TransferType::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments.getSerializable(KEY_PROGRESS_TYPE) as? TransferType
        }
        val ftpAuthority = arguments.getParcelableCompat(KEY_AUTHORITY, FtpAuthority::class.java)
        if (transferType == null || ftpAuthority == null) {
            return
        }
        authority = ftpAuthority
        when (transferType) {
            TransferType.UPLOAD_FILES -> {
                binding.textTitle.setText(R.string.upload_files)
                setListVisible(true)
                val filePath = arguments.getString(KEY_FTP_FILE_PATH)
                if (filePath != null) {
                    val uploadFolder = authority.getPath(filePath)
                    val uris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= 34) {
                        arguments.getParcelableArrayList(KEY_URI_LIST, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        arguments.getParcelableArrayList(KEY_URI_LIST)
                    }
                    if (uris != null) {
                        // obf: C0273
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            val files = FileUploader.getPickedFiles(requireContext(), uploadFolder, uris)
                            val uiFiles = files.filter { !it.isDirectory }.map { UiProgressFile(it) }
                            // obf: C3099
                            withContext(Dispatchers.Main) {
                                adapter.differ.submitList(uiFiles) {
                                    // obf: RunnableC4048 case 0
                                    setButtonEnabled(true)
                                    fileUploader = FileUploader(requireContext().contentResolver, files, this@DialogFileProgress)
                                    resumeTransfers(false)
                                }
                            }
                        }
                    }
                }
            }
            TransferType.UPLOAD_FOLDER -> {
                binding.textTitle.setText(R.string.upload_folder)
                setState(State.LOADING)
                val filePath = arguments.getString(KEY_FTP_FILE_PATH)
                if (filePath != null) {
                    val destFolder = authority.getPath(filePath)
                    val uri = arguments.getParcelableCompat(KEY_URI, Uri::class.java)
                    if (uri != null) {
                        // obf: C3730
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            val files = FileUploader.getFolderFiles(requireContext(), destFolder, uri, this@DialogFileProgress)
                            val uiFiles = files.filter { !it.isDirectory }.map { UiProgressFile(it) }
                            // obf: C3786
                            withContext(Dispatchers.Main) {
                                adapter.differ.submitList(uiFiles) {
                                    // obf: RunnableC4048 case 1
                                    beginDelayedTransition()
                                    setState(State.SUCCESS)
                                    setListVisible(true)
                                    setButtonEnabled(true)
                                    fileUploader = FileUploader(requireContext().contentResolver, files, this@DialogFileProgress)
                                    resumeTransfers(false)
                                }
                            }
                        }
                    }
                }
            }
            TransferType.DOWNLOAD -> {
                setButtonEnabled(false)
                binding.textTitle.setText(R.string.download_2)
                setState(State.LOADING)
                val selectedFile = arguments.getParcelableCompat(KEY_FTP_SELECTED_FILE, SelectedFile::class.java)
                val uri = arguments.getParcelableCompat(KEY_URI, Uri::class.java)
                if (selectedFile != null && uri != null) {
                    val folder = uri.toTreeDocumentFile(requireContext())
                    if (folder != null) {
                        // obf: C3475
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            val source = authority.getPath(selectedFile.path)
                            if (selectedFile.isDirectory) {
                                val targetDir = folder.getTargetDir(selectedFile.name)
                                if (targetDir == null) {
                                    // obf: C2168
                                    withContext(Dispatchers.Main) { dismiss() }
                                } else {
                                    startDownload(targetDir, listFilesRecursively(source))
                                }
                            } else {
                                startDownload(folder, getSingleFile(source))
                            }
                        }
                    }
                }
            }
            TransferType.COPY -> {
                setButtonEnabled(false)
                val selectedFile = arguments.getParcelableCompat(KEY_FTP_SELECTED_FILE, SelectedFile::class.java)
                val filePath = arguments.getString(KEY_FTP_FILE_PATH)
                if (selectedFile != null && filePath != null) {
                    val destFolder = authority.getPath(filePath)
                    val isDirectory = selectedFile.isDirectory
                    binding.textTitle.setText(if (isDirectory) R.string.copying_folder else R.string.copying_file)
                    if (isDirectory) {
                        setState(State.LOADING)
                    }
                    // obf: C1622
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val source = authority.getPath(selectedFile.path)
                        val destPath: Path = if (selectedFile.isDirectory) destFolder.resolve(selectedFile.name) else destFolder
                        val files = if (selectedFile.isDirectory) listFilesRecursively(source) else getSingleFile(source)
                        startCopy(FtpFileCopier.Type.COPY, source, destPath, files)
                    }
                }
            }
            TransferType.CUT -> {
                setButtonEnabled(false)
                val selectedFile = arguments.getParcelableCompat(KEY_FTP_SELECTED_FILE, SelectedFile::class.java)
                val filePath = arguments.getString(KEY_FTP_FILE_PATH)
                if (selectedFile != null && filePath != null) {
                    val destFolder = authority.getPath(filePath)
                    binding.textTitle.setText(if (selectedFile.isDirectory) R.string.moving_folder else R.string.moving_file)
                    // obf: C1227
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val source = authority.getPath(selectedFile.path)
                        if (selectedFile.isDirectory) {
                            val result = FtpFileCopier.moveFolder(source, destFolder, selectedFile.name, this@DialogFileProgress)
                            val files = result.files
                            if (result.isMoved) {
                                // obf: C0474
                                withContext(Dispatchers.Main) { dismiss() }
                            } else if (files != null) {
                                startCopy(FtpFileCopier.Type.MOVE, source, destFolder.resolve(selectedFile.name), files)
                            } else {
                                // obf: C0499
                                withContext(Dispatchers.Main) {
                                    MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(R.string.unexpected_error_occurred)
                                        .setMessage(result.message)
                                        .setPositiveButton(R.string.ok, null)
                                        .setOnDismissListener { dismiss() }
                                        .show()
                                }
                            }
                        } else {
                            startCopy(FtpFileCopier.Type.MOVE, source, destFolder, getSingleFile(source))
                        }
                    }
                }
            }
        }
        isCancelable = false
        setButtonEnabled(false)
        // obf: ဉဖဋဏဂငဋ case 1
        binding.btnPause.setOnClickListener {
            when (buttonState) {
                ButtonState.PAUSE -> {
                    fileUploader?.cancel()
                    fileDownloader?.cancel()
                    ftpFileCopier?.cancel()
                    setButtonState(ButtonState.RESUME)
                }
                ButtonState.RESUME -> resumeTransfers(true)
                ButtonState.OK -> dismiss()
            }
        }
    }

    // obf: mo1933 — binding inflater
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogFileProgressBinding = DialogFileProgressBinding::inflate

    /**
     * The folder [name] inside this download folder; asks when a file with that name exists.
     * Null when skipped or creation failed.
     */
    // obf: m5009
    private suspend fun DocumentFile.getTargetDir(name: String): DocumentFile? {
        val existing = findFile(name) ?: return createDirectory(this, name)
        if (existing.isDirectory) {
            return existing
        }
        // obf: C3827
        val (action, _) = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                // obf: C2209 case 0
                showConflictDialog(name) { action, applyToAll ->
                    continuation.resume(Pair(action, applyToAll))
                }
            }
        }
        val dirName = when (action) {
            ConflictAction.REPLACE -> {
                existing.delete()
                name
            }
            ConflictAction.SKIP -> null
            // obf: C2042 case 0
            ConflictAction.KEEP_BOTH -> generateUniqueName(name) { newName -> findFile(newName) == null }
        } ?: return null
        return createDirectory(this, dirName)
    }

    /** Shows the copy/move items and starts the [FtpFileCopier] once the list is committed. */
    // obf: m5010
    private suspend fun startCopy(type: FtpFileCopier.Type, sourcePath: Path, destPath: Path, files: List<ProgressFTPFile>) {
        val uiFiles = files.filter { !it.isDirectory }.map { UiProgressFile(it) }
        // obf: C0287
        withContext(Dispatchers.Main) {
            adapter.differ.submitList(uiFiles) {
                // obf: RunnableC0506 case 2
                setState(State.SUCCESS)
                setListVisible(true)
                setButtonEnabled(true)
                val copier = FtpFileCopier(type, sourcePath, destPath, files, this@DialogFileProgress)
                ftpFileCopier = copier
                copier.start()
            }
        }
    }

    /** The single transfer item for the file at [path] (empty when missing). */
    // obf: m5011
    private fun getSingleFile(path: FtpPath): List<ProgressFTPFile> {
        val file = runCatching {
            FtpClientHelper.getFileOrNull(path)?.let { ftpFile ->
                ProgressFTPFile(1, ftpFile.name, ftpFile.name, ftpFile.size, ftpFile.isDirectory, path, ftpFile.mimeType, emptyList())
            }
        }.getOrNull()
        return if (file == null) emptyList() else listOf(file)
    }

    /** Shows the download items and starts the [FileDownloader] once the list is committed. */
    // obf: m5012
    private suspend fun startDownload(folder: DocumentFile, files: List<ProgressFTPFile>) {
        val uiFiles = files.filter { !it.isDirectory }.map { UiProgressFile(it) }
        // obf: C0531
        withContext(Dispatchers.Main) {
            adapter.differ.submitList(uiFiles) {
                // obf: RunnableC2443 case 2
                beginDelayedTransition()
                setState(State.SUCCESS)
                setListVisible(true)
                setButtonEnabled(true)
                val downloader = FileDownloader(requireContext().contentResolver, folder, files, this@DialogFileProgress)
                fileDownloader = downloader
                downloader.start()
            }
        }
    }

    /** Resumes the transfers; with [checkConnection] the FTP server is checked (and the device reconnected) first. */
    // obf: m5013
    private fun resumeTransfers(checkConnection: Boolean) {
        setButtonState(ButtonState.PAUSE)
        if (checkConnection) {
            setButtonEnabled(false)
            // obf: C4015
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val isReachable = FtpClientHelper.isReachable(authority)
                // obf: C3900
                withContext(Dispatchers.Main) { onConnectionChecked(isReachable) }
            }
            return
        }
        fileUploader?.start()
        fileDownloader?.start()
        ftpFileCopier?.start()
    }

    // obf: C3900
    private fun onConnectionChecked(isReachable: Boolean) {
        beginDelayedTransition()
        if (isReachable) {
            setState(State.SUCCESS)
            setListVisible(true)
            setButtonEnabled(true)
            fileUploader?.start()
            fileDownloader?.start()
            ftpFileCopier?.start()
        } else {
            setState(State.LOADING)
            setListVisible(false)
            val devicesStore = DevicesStore(PreferenceManager.getDefaultSharedPreferences(requireContext()))
            val adbBinary = AdbBinary(requireContext())
            val device = devicesStore.getDefault()
            if (device != null) {
                Logger.log("reconnecting device ...")
                adbBinary.connect(device.ipAddress, device.port, 2000L) { type, result ->
                    // obf: C1920 case 1
                    val typeName = when (type) {
                        CommandResultType.SUCCESS -> "OK"
                        CommandResultType.RETRY -> "RETRY"
                        CommandResultType.FAILURE -> "FAILURE"
                    }
                    Logger.log("type: " + typeName + ", output: " + result.output + ", error: " + result.error)
                    if (type == CommandResultType.SUCCESS) {
                        adbServiceLauncher?.startService()
                    } else {
                        beginDelayedTransition()
                        setListVisible(false)
                        setState(State.DISCONNECTED)
                        setButtonState(ButtonState.RESUME)
                        setButtonEnabled(true)
                    }
                }
            }
        }
    }

    // obf: m5014
    private fun setState(state: State) {
        loadingView.setState(state)
    }

    // obf: m5015
    override fun onFileProgress(id: Int, percent: Int, processedSize: String) {
        val list = adapter.differ.currentList.map {
            if (it.file.id == id) {
                it.copy(
                    processedSize = processedSize,
                    percent = percent,
                    state = if (percent == 100) ProgressState.COMPLETED else ProgressState.PENDING,
                    error = null,
                )
            } else {
                it
            }
        }
        adapter.differ.submitList(list, null)
    }

    // obf: AbstractC4056.m6636 (inlined part)
    override fun onFileError(id: Int, e: Exception) {
        val isCancelled = e is CancellationException
        val list = adapter.differ.currentList.map {
            if (it.file.id == id) {
                if (isCancelled) {
                    it.copy(state = ProgressState.PAUSE, error = null)
                } else {
                    it.copy(state = ProgressState.ERROR, error = e.message)
                }
            } else {
                it
            }
        }
        adapter.differ.submitList(list, null)
    }

    // obf: C0561
    override fun onTransferPaused() {
        setButtonState(ButtonState.RESUME)
    }

    // obf: C1482
    override fun onTotalProgress(percent: Int, completed: Boolean) {
        setTotalPercent(percent)
        binding.progress.setProgressCompat(percent, true)
        if (completed) {
            isCompleted = true
            isCancelable = true
            setButtonState(ButtonState.OK)
        }
    }

    /** Creates folder [name] in [document]; toasts the error and returns null on failure. */
    // obf: m5016
    private suspend fun createDirectory(document: DocumentFile, name: String): DocumentFile? {
        val result = runCatching { document.createDirectory(name) }
        val e = result.exceptionOrNull() ?: return result.getOrNull()
        // obf: C0138
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
        }
        return null
    }

    // obf: m5017
    private fun setButtonEnabled(enabled: Boolean) {
        binding.btnPause.isEnabled = enabled
    }

    /** "File exists" dialog: replace / skip / keep both, optionally for all; cancel stops the transfers. */
    // obf: m5018 (inlined helper with params context, fileName, onCancel, onResult)
    override fun showConflictDialog(name: String, callback: (ConflictAction, Boolean) -> Unit) {
        val context = requireContext()
        // obf: C3191 case 1
        val onCancel = {
            fileDownloader?.cancel()
            fileUploader?.cancel()
            ftpFileCopier?.cancel()
        }
        val inflater = LayoutInflater.from(context)
        val dialogBinding = DialogFileConflictBinding.inflate(inflater, null, false)
        dialogBinding.textAdbRemote.text = name
        var checkedItem = 0
        val choices = listOf(Pair(R.string.replace, 0), Pair(R.string.skip, 1), Pair(R.string.keep_both_files, 2))
        choices.map { (textRes, index) ->
            val choice = inflater.inflate(R.layout.select_dialog_singlechoice_material, dialogBinding.layoutFileConflict, false) as CheckedTextView
            choice.apply {
                setText(textRes)
                isChecked = index == 0
                // obf: တဋဌဖဈဓဈ
                setOnClickListener {
                    checkedItem = index
                    for (child in dialogBinding.layoutFileConflict) {
                        (child as CheckedTextView).isChecked = false
                    }
                    isChecked = true
                }
            }
            dialogBinding.layoutFileConflict.addView(choice)
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            choice.setBackgroundResource(typedValue.resourceId)
            choice
        }
        MaterialAlertDialogBuilder(context)
            .setCancelable(false)
            .setTitle(R.string.file_already_exists)
            .setView(dialogBinding.root)
            // obf: DialogInterfaceOnClickListenerC0167 case 1
            .setPositiveButton(R.string.ok) { _, _ ->
                callback(ConflictAction.entries[checkedItem], dialogBinding.checkboxApplyActionAllFiles.isChecked)
            }
            // obf: DialogInterfaceOnClickListenerC2999 case 2. RECON-UNSURE: negative vs neutral
            // (R8-specialized builder call with fixed text R.string.cancel)
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .show()
    }

    // obf: m5019
    private fun setListVisible(visible: Boolean) {
        binding.list.visibility = if (visible) View.VISIBLE else View.GONE
    }

    // obf: m5020
    private fun setButtonState(state: ButtonState) {
        buttonState = state
        val text = when (state) {
            ButtonState.PAUSE -> R.string.pause
            ButtonState.RESUME -> R.string.resume
            ButtonState.OK -> R.string.ok
        }
        binding.btnPause.setText(text)
    }

    // obf: m5021
    private fun setTotalPercent(percent: Int) {
        binding.textPercent.text = String.format(locale, "%d%%", percent)
    }

    companion object {
        // obf: f9862 (C2352) — argument keys (the show helper is inlined at the FilesActivity call sites)
        const val KEY_PROGRESS_TYPE = "progress_type"
        const val KEY_AUTHORITY = "authority"
        const val KEY_FTP_FILE_PATH = "ftp_file_path"
        const val KEY_FTP_SELECTED_FILE = "ftp_selected_file"
        const val KEY_URI_LIST = "uri_list"
        const val KEY_URI = "uri"
    }
}

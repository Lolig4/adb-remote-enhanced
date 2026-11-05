package com.jcapps.adbremote.ui.files

import com.jcapps.adbremote.BuildConfig
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.files.FilesAdapter
import com.jcapps.adbremote.adapters.files.NavDirectoryAdapter
import com.jcapps.adbremote.api.remote.ApiClient
import com.jcapps.adbremote.databinding.ActivityFilesBinding
import com.jcapps.adbremote.databinding.DialogFileInfoBinding
import com.jcapps.adbremote.dialogs.DialogInput
import com.jcapps.adbremote.dialogs.DialogOption
import com.jcapps.adbremote.dialogs.files.DialogFileProgress
import com.jcapps.adbremote.dialogs.files.DialogFilesSettings
import com.jcapps.adbremote.dialogs.files.DialogFtpSettings
import com.jcapps.adbremote.dialogs.files.DialogOpenRemotelyWith
import com.jcapps.adbremote.model.FTPConfig
import com.jcapps.adbremote.model.FTPSettings
import com.jcapps.adbremote.model.FilesSettings
import com.jcapps.adbremote.model.IntentParams
import com.jcapps.adbremote.model.MxPlayerDecodeMode
import com.jcapps.adbremote.model.NavDirectory
import com.jcapps.adbremote.model.Option
import com.jcapps.adbremote.model.SelectedFile
import com.jcapps.adbremote.model.ServerState
import com.jcapps.adbremote.model.SimpleResult
import com.jcapps.adbremote.model.SupportedApp
import com.jcapps.adbremote.model.TransferType
import com.jcapps.adbremote.prefs.JsonPrefs
import com.jcapps.adbremote.provider.ftp.FtpAuthenticator
import com.jcapps.adbremote.provider.ftp.FtpAuthority
import com.jcapps.adbremote.provider.ftp.FtpClientHelper
import com.jcapps.adbremote.provider.ftp.FtpFileSystemProvider
import com.jcapps.adbremote.provider.ftp.FtpMode
import com.jcapps.adbremote.provider.ftp.FtpPath
import com.jcapps.adbremote.provider.ftp.FtpProtocol
import com.jcapps.adbremote.ui.BaseActivity
import com.jcapps.adbremote.utils.SpacingItemDecoration
import com.jcapps.adbremote.utils.apiServiceFor
import com.jcapps.adbremote.utils.copyToClipboard
import com.jcapps.adbremote.utils.formatFileSize
import com.jcapps.adbremote.utils.mimeType
import com.jcapps.adbremote.utils.requestAppReviewIfRecommended
import com.jcapps.adbremote.utils.serverState
import com.jcapps.adbremote.utils.toFtpConfig
import com.jcapps.adbremote.utils.updateServerState
import com.jcapps.adbremote.views.State
import com.jcapps.adbremote.views.StateView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPFile
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The remote FTP file manager screen: browses the device's filesystem over FTP, sorts and selects
 * files, copy/cut/move/paste, uploads files or folders via SAF, downloads, creates files/folders,
 * and opens files remotely with a chosen app. Binding: ActivityFilesBinding (layout a7).
 */
class FilesActivity : BaseActivity() {
    // obf: C1673

    // obf: f1687 — the file marked for copy/cut/download (null = none)
    var selectedFile: SelectedFile? = null

    // obf: f1688
    lateinit var navDirectoryAdapter: NavDirectoryAdapter

    // obf: f1692
    lateinit var fileAdapter: FilesAdapter

    // obf: f1693 — device IP address ("current_device_ip")
    var ipAddress: String? = null

    // obf: f1694 — guards concurrent listing refreshes
    var loading: Boolean = false

    // obf: f1700
    var ftpSettings: FTPSettings? = null

    // obf: f1701
    lateinit var filesSettings: FilesSettings

    // obf: f1702 — in-flight coroutine
    var loadJob: Job? = null

    // obf: f1704 — the directory currently shown
    lateinit var currentDirectory: NavDirectory

    // obf: f1689 (lambda case 0)
    private val binding: ActivityFilesBinding by lazy { ActivityFilesBinding.inflate(layoutInflater) }

    // obf: f1690 (lambda case 3)
    private val surfaceColor: Int by lazy { SurfaceColors.SURFACE_3.getColor(this) }

    // obf: f1691 (lambda case 2) — "disconnected" state shows "FTP server not started." (C0853.m2697 mask 3: message = R.string.ftp_server_not_started)
    private val stateView: StateView by lazy {
        val params = StateView.defaultParams(this)
        StateView(binding.includeViewState, params.copy(disconnected = params.disconnected.copy(message = R.string.ftp_server_not_started)))
    }

    // obf: f1697 (lambda case 4)
    private val basePrefs: JsonPrefs by lazy {
        JsonPrefs(PreferenceManager.getDefaultSharedPreferences(this))
    }

    // obf: f1699 (lambda case 1)
    private val pasteBar: PasteBar by lazy { PasteBar(this, binding) }

    // obf: f1703 (lambda case 5)
    private val fileSortingMenu: FileSortingMenu by lazy { FileSortingMenu(basePrefs) }

    // obf: f1696 — C0875(1) = OpenMultipleDocuments; callback C3788 case 1 (upload files)
    val uploadFilesLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isEmpty()) return@registerForActivityResult
            val destFolder = currentDirectory.path ?: return@registerForActivityResult
            // obf: C2352 (DialogFileProgress companion, inlined)
            val dialog = DialogFileProgress()
            val arguments = bundleOf(
                "progress_type" to TransferType.UPLOAD_FILES,
                "authority" to ftpAuthority(),
                "ftp_file_path" to destFolder.toString(),
            )
            arguments.putParcelableArrayList("uri_list", ArrayList(uris))
            dialog.arguments = arguments
            dialog.show(supportFragmentManager, DialogFileProgress::class.java.name)
            dialog.onDismissed = { refresh() }
        }

    // obf: f1695 — C0875(0) = OpenDocumentTree; callback C3788 case 2 (upload folder)
    val uploadFolderLauncher: ActivityResultLauncher<Uri?> =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            val destFolder = currentDirectory.path ?: return@registerForActivityResult
            val dialog = DialogFileProgress()
            dialog.arguments = bundleOf(
                "progress_type" to TransferType.UPLOAD_FOLDER,
                "authority" to ftpAuthority(),
                "ftp_file_path" to destFolder.toString(),
                "uri" to uri,
            )
            dialog.show(supportFragmentManager, DialogFileProgress::class.java.name)
            dialog.onDismissed = { refresh() }
        }

    // obf: f1698 — C0875(0) = OpenDocumentTree; callback C3788 case 3 (download selected file)
    val downloadLauncher: ActivityResultLauncher<Uri?> =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val file = selectedFile
            if (uri == null || file == null) return@registerForActivityResult
            val dialog = DialogFileProgress()
            dialog.arguments = bundleOf(
                "progress_type" to TransferType.DOWNLOAD,
                "authority" to ftpAuthority(),
                "ftp_selected_file" to file,
                "uri" to uri,
            )
            dialog.show(supportFragmentManager, DialogFileProgress::class.java.name)
            dialog.onDismissed = { selectedFile = null }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applySystemBarInsets(binding.root)
        val ip = basePrefs.dataStore.getString("current_device_ip", null)
        ipAddress = ip
        if (ip != null) {
            ftpSettings = DialogFtpSettings.loadFtpSettings(basePrefs)
            filesSettings = loadFilesSettings(this)
            setState(if (ftpSettings == null) State.DISCONNECTED else State.LOADING)
            currentDirectory = NavDirectory(getString(R.string.internal_storage), null)
            FtpClientHelper.authenticator = FtpAuthenticator(this)
            // obf: C2356 case 2
            navDirectoryAdapter = NavDirectoryAdapter(this) { directory ->
                currentDirectory = directory
                refresh()
            }
            binding.listDirectories.apply {
                setHasFixedSize(true)
                setItemViewCacheSize(10)
                adapter = navDirectoryAdapter
            }
            pushNavDirectory(currentDirectory)
            fileAdapter = FilesAdapter(this)
            binding.listFiles.apply {
                setHasFixedSize(true)
                addItemDecoration(SpacingItemDecoration(resources.getDimension(R.dimen.files_list_spacing).roundToInt()))
                adapter = fileAdapter
            }
            // obf: C2672 + collector C2745 case 0
            lifecycleScope.launch {
                serverState.collect { state ->
                    val viewState = when (state) {
                        ServerState.NOT_STARTED -> State.DISCONNECTED
                        ServerState.STARTING -> State.LOADING
                        ServerState.RUNNING -> State.SUCCESS
                        ServerState.STOPPING -> State.LOADING
                    }
                    val isRunning = state == ServerState.RUNNING
                    binding.fabAdd.isEnabled = isRunning
                    if (isRunning) {
                        if (currentDirectory.path == null) {
                            val root = FtpFileSystemProvider.getOrNewFileSystem(ftpAuthority()).getPath("/")
                            val directory = currentDirectory.copy(path = root)
                            currentDirectory = directory
                            if (navDirectoryAdapter.directories.size >= 1) {
                                navDirectoryAdapter.directories[0] = directory
                            }
                        }
                        refresh()
                    } else {
                        fileAdapter.differ.submitList(emptyList())
                    }
                    setState(viewState)
                }
            }
            binding.appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                val collapsed = abs(verticalOffset) - appBarLayout.totalScrollRange == 0
                binding.listDirectories.setBackgroundColor(if (collapsed) surfaceColor else 0)
                binding.root.setBackgroundColor(if (collapsed) surfaceColor else 0)
            })
            val swipe = binding.swipeRefresh
            swipe.setProgressBackgroundColorSchemeColor(SurfaceColors.SURFACE_3.getColor(swipe.context))
            // obf: C3788 case 0
            binding.swipeRefresh.setOnRefreshListener { refresh() }
            binding.fabAdd.setOnClickListener {
                val dialog = DialogOption()
                dialog.options = listOf(
                    Option("create_file", R.drawable.ic_create_file, getString(R.string.create_new_file)),
                    Option("create_folder", R.drawable.ic_folder, getString(R.string.create_new_folder)),
                    Option("upload_files", R.drawable.ic_upload_file, getString(R.string.upload_files)),
                    Option("upload_folder", R.drawable.ic_upload_folder, getString(R.string.upload_folder)),
                )
                // obf: C2356 case 8
                dialog.onOptionSelected = { option -> onOptionSelected(option) }
                dialog.show(supportFragmentManager, DialogOption::class.java.name)
            }
            // obf: C2356 case 3
            onBackPressedDispatcher.addCallback {
                if (serverState.value != ServerState.RUNNING) {
                    finish()
                } else if (navDirectoryAdapter.directories.size == 1) {
                    finish()
                } else {
                    val adapter = navDirectoryAdapter
                    val size = adapter.directories.size
                    if (size > 1) {
                        adapter.directories.removeAt(size - 1)
                        currentDirectory = adapter.directories.last()
                        refresh()
                        adapter.notifyDataSetChanged()
                    }
                }
            }
            val settings = ftpSettings
            if (settings == null) {
                showFtpSettings()
            } else if (serverState.value != ServerState.RUNNING) {
                val api = apiServiceFor(ip)
                val config = settings.toFtpConfig(ip)
                // obf: C1642
                loadJob = lifecycleScope.launch(Dispatchers.IO) {
                    val result = runCatching {
                        updateServerState(ServerState.STARTING)
                        val response = api.startFtp(config)
                        updateServerState(if (response.status) ServerState.RUNNING else ServerState.NOT_STARTED)
                        SimpleResult(status = response.status, message = if (response.status) response.data else response.message ?: response.data)
                    }.getOrElse { SimpleResult(status = false, message = it.message) }
                    // obf: C2053 (body removed by R8)
                    withContext(Dispatchers.Main) { result }
                }
            }
        } else {
            stateView.setState(State.DISCONNECTED)
        }
        // obf: lambda case 2 — cancel paste
        binding.btnPasteClose.setOnClickListener {
            selectedFile = null
            pasteBar.hide()
        }
        // obf: lambda case 0 — paste
        binding.btnPaste.setOnClickListener { paste() }
        if (ftpSettings != null) {
        }
    }

    /** Paste bar "paste" button body. obf: ညဏကဖသရဇ case 0 */
    private fun paste() {
        pasteBar.hide()
        val file = selectedFile
        if (file != null) {
            val destPath = currentDirectory.path?.toString() ?: "/"
            val sourcePath = file.path
            if (destPath == sourcePath || destPath.startsWith("$sourcePath/")) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(if (pasteBar.mode == PasteBar.Mode.COPY) R.string.cannot_copy_folder_into_itself else R.string.cannot_move_folder_into_itself)
                    .setMessage(R.string.destination_folder_inside_source_folder)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else {
                val parent = sourcePath.substringBeforeLast("/").ifEmpty { "/" }
                if (parent != destPath) {
                    val dialog = DialogFileProgress()
                    dialog.arguments = bundleOf(
                        "progress_type" to if (pasteBar.mode == PasteBar.Mode.COPY) TransferType.COPY else TransferType.CUT,
                        "authority" to ftpAuthority(),
                        "ftp_selected_file" to file,
                        "ftp_file_path" to destPath,
                    )
                    dialog.show(supportFragmentManager, DialogFileProgress::class.java.name)
                    // obf: C2356 case 6
                    dialog.onDismissed = { refresh() }
                }
            }
        }
        selectedFile = null
    }

    /** FAB option sheet result. obf: C2356 default case */
    private fun onOptionSelected(option: Option) {
        when (option.key) {
            "upload_files" -> uploadFilesLauncher.launch(arrayOf("*/*"))
            "create_file", "create_folder" -> {
                val dialog = DialogInput()
                dialog.title = option.title
                // obf: C3629 case 2
                dialog.onInput = { inputText -> createFile(inputText, option) }
                dialog.show(supportFragmentManager, DialogInput::class.java.name)
            }
            "upload_folder" -> uploadFolderLauncher.launch(null)
        }
    }

    /** obf: C0219 */
    private fun createFile(name: String, option: Option) {
        lifecycleScope.launch(Dispatchers.IO) {
            val ftpFile = runCatching {
                val path = currentDirectory.path?.let { (it as FtpPath).resolve(name) }
                    ?: throw IllegalStateException("Path is null.")
                val existing = runCatching { FtpClientHelper.getFile(path, true) }.getOrNull()
                if (option.key == "create_file") {
                    if (existing != null && existing.isFile) {
                        throw UnsupportedOperationException("File already exists.")
                    }
                    FtpClientHelper.createFile(path)
                } else {
                    if (existing != null && existing.isDirectory) {
                        throw UnsupportedOperationException("Folder already exists.")
                    }
                    FtpClientHelper.createDirectory(path)
                }
                FtpClientHelper.getFile(path, true)
            }.getOrElse {
                // obf: C1596
                withContext(Dispatchers.Main) { showError(it) }
                return@launch
            }
            // obf: C0065
            withContext(Dispatchers.Main) { addFile(ftpFile) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.files, menu)
        if (ipAddress == null) {
            menu?.findItem(R.id.action_sort)?.isEnabled = false
            menu?.findItem(R.id.action_ftp_settings)?.isEnabled = false
            menu?.findItem(R.id.action_files_settings)?.isEnabled = false
        }
        if (menu != null) {
            val sorting = fileSortingMenu
            val itemBySortBy = sorting.sortByItems.entries.associate { (id, sortBy) -> sortBy to id }
            val fileSorting = sorting.fileSorting
            itemBySortBy[fileSorting.sortBy]?.let { menu.findItem(it) }?.isChecked = true
            menu.findItem(sorting.orderItemId)?.isChecked = fileSorting.orderAscending
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_ftp_settings) {
            showFtpSettings()
        } else if (itemId == R.id.action_files_settings) {
            val dialog = DialogFilesSettings()
            dialog.filesSettings = filesSettings
            // obf: C2356 case 0
            dialog.onSettingsChanged = { filesSettings = it }
            dialog.show(supportFragmentManager, null)
        } else {
            val sorting = fileSortingMenu
            val id = item.itemId
            val sortByItems = sorting.sortByItems
            val basePrefs = sorting.basePrefs
            val fileSorting = sorting.fileSorting
            if (sortByItems.keys.contains(id)) {
                item.isChecked = true
                fileSorting.sortBy = sortByItems[id]!!
                basePrefs.put(fileSorting, "files_sorting")
                updateFiles(null)
            } else if (id == sorting.orderItemId) {
                fileSorting.orderAscending = !fileSorting.orderAscending
                item.isChecked = fileSorting.orderAscending
                basePrefs.put(fileSorting, "files_sorting")
                updateFiles(null)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        loadJob?.cancel()
        super.onDestroy()
    }

    // obf: m877 — the effective FTPConfig from ftpSettings + ipAddress
    fun ftpConfig(): FTPConfig = ftpSettings!!.toFtpConfig(ipAddress!!)

    // obf: m878 — push a directory onto the breadcrumb and scroll to it
    fun pushNavDirectory(directory: NavDirectory) {
        val adapter = navDirectoryAdapter
        adapter.directories.add(directory)
        adapter.notifyDataSetChanged()
        binding.listDirectories.smoothScrollToPosition(adapter.directories.size - 1)
    }

    // obf: m879
    fun binding(): ActivityFilesBinding = binding

    /**
     * Re-sorts the shown list off the main thread, optionally transforming it first.
     */
    // obf: m880 (body = C2851, result C1984)
    fun updateFiles(transform: ((MutableList<FTPFile>) -> List<FTPFile>?)?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val currentFiles = fileAdapter.differ.currentList.toMutableList()
            val files = transform?.invoke(currentFiles) ?: currentFiles
            val sorted = fileSortingMenu.sort(files)
            withContext(Dispatchers.Main) { showFiles(sorted) }
        }
    }

    // obf: m881 — remote absolute path of an FTPFile in the current directory
    fun remotePath(ftpFile: FTPFile): String {
        val path = currentDirectory.resolve(ftpFile.name)?.toString()
        val root = ftpConfig().defaultDir ?: DialogFtpSettings.DEFAULT_ROOT_DIRS.first()
        return root + path
    }

    // obf: m882 — (re)load the current directory listing if connected
    fun refresh() {
        if (!loading && currentDirectory.path != null && serverState.value == ServerState.RUNNING) {
            loading = true
            // obf: C1377
            loadJob = lifecycleScope.launch(Dispatchers.IO) {
                // obf: C1775
                withContext(Dispatchers.Main) { binding.swipeRefresh.isRefreshing = true }
                val files = runCatching {
                    fileSortingMenu.sort(FtpClientHelper.listDirectory(currentDirectory.path!!))
                }.getOrElse {
                    // obf: C2055
                    withContext(Dispatchers.Main) { showError(it) }
                    null
                }
                loading = false
                if (files != null) {
                    // RECON-UNSURE: the decompile shows both `z8.f2570` alone and
                    //  `z8.f2570 && z8.f2572`; the shared inlined helper checks both.
                    requestAppReviewIfRecommended(this@FilesActivity)
                }
                // obf: C2112
                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                    showFiles(files)
                }
            }
            return
        }
        binding.swipeRefresh.isRefreshing = false
    }

    // obf: m883 — the IntentParams.FilePath for opening an FTPFile remotely
    fun buildFilePath(ftpFile: FTPFile): IntentParams.FilePath? {
        val path = currentDirectory.resolve(ftpFile.name) ?: return null
        return IntentParams.FilePath(ftpConfig().defaultDir, ftpConfig().fallbackDir, path.toString())
    }

    // obf: m884 — the FtpAuthority of the current connection
    fun ftpAuthority(): FtpAuthority {
        val config = ftpConfig()
        return FtpAuthority(
            FtpProtocol.FTP,
            config.ipAddress,
            config.port,
            config.username ?: "anonymous",
            if (config.passiveMode) FtpMode.PASSIVE else FtpMode.ACTIVE,
            // RECON-UNSURE: AbstractC0343.f3593 (app default charset) assumed UTF-8
            Charsets.UTF_8.name(),
        )
    }

    // obf: m885 — show the FTP settings bottom sheet (C3573 show helper inlined)
    fun showFtpSettings() {
        val dialog = DialogFtpSettings()
        dialog.arguments = bundleOf("ip_address" to ipAddress!!, "ftp_settings" to ftpSettings)
        dialog.show(supportFragmentManager, DialogFtpSettings::class.java.name)
        // obf: C2356 case 7
        dialog.onSettingsChanged = { ftpSettings = it }
    }

    // obf: m886
    fun pasteBar(): PasteBar = pasteBar

    // obf: m887 — show a snackbar (anchored to the FAB while the paste bar is shown)
    fun showSnackbar(messageRes: Int) {
        val root = binding.root
        val snackbar = Snackbar.make(root, root.resources.getText(messageRes), Snackbar.LENGTH_SHORT)
        if (pasteBar.isShowing) {
            snackbar.anchorView = binding.fabAdd
        }
        snackbar.show()
    }

    // obf: m888 — set the state view and hide the list while not showing content
    fun setState(state: State) {
        stateView.setState(state)
        binding.listFiles.visibility =
            if (listOf(State.DISCONNECTED, State.LOADING, State.EMPTY).contains(state)) View.INVISIBLE else View.VISIBLE
    }

    // obf: m875
    private fun showFiles(files: List<FTPFile>?) {
        setState(if (files.isNullOrEmpty()) State.EMPTY else State.SUCCESS)
        fileAdapter.differ.submitList(files ?: emptyList())
    }

    // obf: m876
    private fun showError(throwable: Throwable) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(throwable.toString())
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    // obf: inlined (C1587 case 0)
    private fun addFile(ftpFile: FTPFile) {
        updateFiles { it.add(ftpFile); it }
    }

    // obf: inlined (C1587 case 1)
    private fun removeFile(ftpFile: FTPFile) {
        updateFiles { it.remove(ftpFile); it }
    }

    // obf: inlined (C3629 case 3)
    private fun replaceFile(old: FTPFile, new: FTPFile) {
        updateFiles { it.remove(old); it.add(new); it }
    }

    /**
     * Card click of a list item: enter directories / follow symlinks, otherwise open the file
     * locally through the app's FileProvider. RECON-UNSURE: name (R8 inlined it into FileViewHolder).
     */
    fun onFileClicked(file: FTPFile) {
        if (file.isDirectory || file.isSymbolicLink) {
            if (loading || currentDirectory.path == null) return
            val name = if (file.isDirectory) file.name else file.link ?: ""
            val path = currentDirectory.path
            val directory = NavDirectory(name, path?.let { (it as FtpPath).resolve(name) })
            currentDirectory = directory
            pushNavDirectory(directory)
            refresh()
            return
        }
        if (!file.isFile) {
            if (file.type == FTPFile.UNKNOWN_TYPE) {
                showSnackbar(R.string.file_type_not_supported)
            }
            return
        }
        if (Build.VERSION.SDK_INT < 26) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unsupported_android_version)
                .setMessage(R.string.file_streaming_requires_android_8)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        val path = currentDirectory.resolve(file.name) ?: return
        val mimeType = file.mimeType
        val title = file.name
        val decodeMode = MxPlayerDecodeMode.fromValue(filesSettings.mxPlayerDecodeMode)
        val uri = Uri.Builder()
            .scheme("content")
            .authority(BuildConfig.APPLICATION_ID + ".providers.file_provider")
            .path(Uri.encode(path.toUri().toString()))
            .build()
        var intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .putExtra("title", title)
            .putExtra("filename", title)
        if (decodeMode != null) {
            intent = intent.putExtra("decode_mode", decodeMode.value.toByte())
        }
        startActivity(intent.putExtra("com.jcapps.adbremote.extra.PATH_URI", path.toUri().toString()))
    }

    /**
     * Overflow button of a list item: per-file action menu (R.menu.files_item).
     * RECON-UNSURE: name (R8 inlined it into FileViewHolder).
     */
    fun onFileMenuClicked(view: View, file: FTPFile) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.files_item, popup.menu)
        // obf: C1536 case 2
        popup.setOnMenuItemClickListener { item ->
            onFileMenuItemClicked(item, file)
            // RECON-UNSURE: R8 turned the listener into a void method; constant result assumed
            true
        }
        popup.show()
    }

    private fun onFileMenuItemClicked(item: MenuItem, file: FTPFile) {
        when (item.itemId) {
            R.id.action_open_remotely -> {
                val filePath = buildFilePath(file) ?: return
                openRemotely(file, filePath, null)
            }
            R.id.action_open_remotely_with -> {
                val filePath = buildFilePath(file) ?: return
                val dialog = DialogOpenRemotelyWith()
                dialog.mimeType = file.mimeType
                dialog.filePath = Gson().toJson(filePath)
                dialog.ipAddress = ipAddress
                // obf: C1115 case 0
                dialog.onAppSelected = { app ->
                    buildFilePath(file)?.let { openRemotely(file, it, app) }
                }
                dialog.show(supportFragmentManager, null)
            }
            R.id.action_download -> {
                selectedFile = selectFile(file)
                downloadLauncher.launch(null)
            }
            R.id.action_cut -> {
                selectedFile = selectFile(file)
                pasteBar.show(PasteBar.Mode.MOVE, file.isDirectory)
            }
            R.id.action_copy -> {
                selectedFile = selectFile(file)
                pasteBar.show(PasteBar.Mode.COPY, file.isDirectory)
            }
            R.id.action_rename -> {
                val dialog = DialogInput()
                dialog.title = getString(R.string.rename)
                dialog.text = file.name
                // obf: C1115 case 1
                dialog.onInput = { inputText -> rename(file, inputText) }
                dialog.show(supportFragmentManager, DialogInput::class.java.name)
            }
            R.id.action_delete -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_permanently)
                    .setMessage(R.string.permanent_cannot_undone)
                    .setNegativeButton(R.string.cancel, null)
                    // obf: DialogInterfaceOnClickListenerC0807 case 3
                    .setPositiveButton(R.string.delete) { _, _ -> delete(file) }
                    .show()
            }
            R.id.action_copy_path -> {
                copyToClipboard(remotePath(file))
                showSnackbar(R.string.copied_clipboard)
            }
            R.id.action_properties -> showFileInfo(file)
        }
    }

    private fun selectFile(file: FTPFile): SelectedFile =
        SelectedFile(
            path = currentDirectory.resolve(file.name)!!.toString(),
            isDirectory = file.isDirectory,
            name = file.name,
        )

    /** File info dialog (layout bb). RECON-UNSURE: inlined helper (params context/ftpFile/parentFolder). */
    private fun showFileInfo(ftpFile: FTPFile) {
        val parentFolder = remotePath(ftpFile).substringBeforeLast("/")
        val infoBinding = DialogFileInfoBinding.inflate(LayoutInflater.from(this))
        infoBinding.inputFileName.setText(ftpFile.name)
        infoBinding.inputFileParentFolder.setText(parentFolder)
        infoBinding.inputArrowDropDown.setText(ftpFile.mimeType)
        infoBinding.inputFileSize.setText(formatFileSize(ftpFile.size))
        infoBinding.inputFileModified.setText(
            SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.getDefault()).format(ftpFile.timestamp.time),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.properties)
            .setView(infoBinding.root)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /** Ask the device server to open the file with an intent. obf: C2657 */
    private fun openRemotely(file: FTPFile, filePath: IntentParams.FilePath, supportedApp: SupportedApp?) {
        loadJob = lifecycleScope.launch(Dispatchers.IO) {
            val response = runCatching {
                val params = IntentParams(
                    filesSettings.openRemotelyUsing,
                    Intent.ACTION_VIEW,
                    filePath,
                    file.mimeType,
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
                if (supportedApp != null) {
                    params.componentName = supportedApp.componentName
                }
                ApiClient.create(ipAddress).startIntent(params)
            }.getOrNull()
            val message = if (response == null || !response.status) {
                R.string.device_unreachable_try_again_reconnect
            } else if (file.isDirectory) {
                R.string.folder_opened_remotely
            } else {
                R.string.file_opened_remotely
            }
            // obf: C3015
            withContext(Dispatchers.Main) { showSnackbar(message) }
        }
    }

    /** obf: C2967 */
    private fun rename(sourceFile: FTPFile, inputText: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val newFile = runCatching {
                val source = currentDirectory.resolve(sourceFile.name)
                if (source == null) {
                    null
                } else {
                    val target = currentDirectory.resolve(inputText)
                    if (target == null) {
                        null
                    } else {
                        FtpClientHelper.rename(source, target)
                        FtpClientHelper.getFile(target, true)
                    }
                }
            }.getOrElse {
                // obf: C2259
                withContext(Dispatchers.Main) { showError(it) }
                null
            }
            if (newFile != null) {
                // obf: C0672
                withContext(Dispatchers.Main) { replaceFile(sourceFile, newFile) }
            }
        }
    }

    /** obf: C2589 */
    private fun delete(file: FTPFile) {
        lifecycleScope.launch(Dispatchers.IO) {
            val isDirectory = file.isDirectory
            val deleted = runCatching {
                val path = currentDirectory.resolve(file.name)
                if (path != null) {
                    if (isDirectory) {
                        FtpClientHelper.removeDirectory(path)
                    } else {
                        FtpClientHelper.deleteFile(path)
                    }
                }
                true
            }.getOrElse {
                // obf: C0683
                withContext(Dispatchers.Main) { showError(it) }
                false
            }
            if (deleted) {
                // obf: C3996
                withContext(Dispatchers.Main) {
                    removeFile(file)
                    showSnackbar(if (isDirectory) R.string.folder_deleted else R.string.file_deleted)
                }
            }
        }
    }

    companion object {
        // obf: f1686 (unused int constant from the original)
    }
}

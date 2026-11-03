package com.jcapps.adbremote.ui

import android.app.ActivityOptions
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.os.Process
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.search.SearchView
import com.google.android.material.snackbar.Snackbar
import com.jcapps.adbremote.AdbRemoteApp
import com.jcapps.adbremote.Constants
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.ToolsAdapter
import com.jcapps.adbremote.adapters.apps.AppsAdapter
import com.jcapps.adbremote.adapters.icons.IconShapePreviewAdapter
import com.jcapps.adbremote.api.adb.AdbBinary
import com.jcapps.adbremote.api.adb.ConnectionMethod
import com.jcapps.adbremote.api.adb.ServerInstaller
import com.jcapps.adbremote.api.adb.commands.ApiCommand
import com.jcapps.adbremote.api.adb.commands.BinaryCommand
import com.jcapps.adbremote.api.remote.ApiClient
import com.jcapps.adbremote.dao.Fav
import com.jcapps.adbremote.dao.FavApps
import com.jcapps.adbremote.databinding.ActivityMainBinding
import com.jcapps.adbremote.databinding.NavHeaderBinding
import com.jcapps.adbremote.dialogs.DialogOption
import com.jcapps.adbremote.dialogs.DialogRateApp
import com.jcapps.adbremote.dialogs.home.DialogClipboard
import com.jcapps.adbremote.dialogs.home.DialogConnect
import com.jcapps.adbremote.dialogs.home.DialogDeviceInfo
import com.jcapps.adbremote.dialogs.home.DialogDevices
import com.jcapps.adbremote.dialogs.home.DialogNtp
import com.jcapps.adbremote.dialogs.home.DialogPerformance
import com.jcapps.adbremote.dialogs.home.DialogScreenshot
import com.jcapps.adbremote.dialogs.icons.DialogIconPackApply
import com.jcapps.adbremote.model.AdbDevice
import com.jcapps.adbremote.model.App
import com.jcapps.adbremote.model.AppsTab
import com.jcapps.adbremote.model.ConnectionState
import com.jcapps.adbremote.model.Direction
import com.jcapps.adbremote.model.Link
import com.jcapps.adbremote.model.Option
import com.jcapps.adbremote.model.ServerState
import com.jcapps.adbremote.model.ToolType
import com.jcapps.adbremote.model.VirtualKey
import com.jcapps.adbremote.prefs.AppPrefs
import com.jcapps.adbremote.prefs.DevicesStore
import com.jcapps.adbremote.services.AdbService
import com.jcapps.adbremote.ui.apps.AppsActivity
import com.jcapps.adbremote.ui.files.FilesActivity
import com.jcapps.adbremote.ui.intro.IntroActivity
import com.jcapps.adbremote.ui.main.BackPressHandler
import com.jcapps.adbremote.utils.AdbServiceLauncher
import com.jcapps.adbremote.utils.AppConfigLoader
import com.jcapps.adbremote.utils.ButtonManager
import com.jcapps.adbremote.utils.ButtonPressOverlay
import com.jcapps.adbremote.utils.ContinuousPressHandler
import com.jcapps.adbremote.utils.HapticFeedback
import com.jcapps.adbremote.utils.HidKeyMapper
import com.jcapps.adbremote.utils.Logger
import com.jcapps.adbremote.utils.NetworkState
import com.jcapps.adbremote.utils.PlayStore
import com.jcapps.adbremote.utils.SpacingItemDecoration
import com.jcapps.adbremote.utils.getConnectionMethod
import com.jcapps.adbremote.utils.getIconPacks
import com.jcapps.adbremote.utils.getIconShape
import com.jcapps.adbremote.utils.createShapeAppearanceModel
import com.jcapps.adbremote.utils.hasConnected
import com.jcapps.adbremote.utils.hasSentCommand
import com.jcapps.adbremote.utils.killAdbServer
import com.jcapps.adbremote.utils.loadIconPack
import com.jcapps.adbremote.utils.networkStateFlow
import com.jcapps.adbremote.utils.requestAppReviewIfRecommended
import com.jcapps.adbremote.utils.showMarkdownDialog
import com.jcapps.adbremote.utils.showNetworkWarning
import com.jcapps.adbremote.utils.tools
import com.jcapps.adbremote.utils.updateServerState
import com.jcapps.adbremote.viewmodels.AppViewModel
import com.jcapps.adbremote.viewmodels.MainViewModel
import com.jcapps.adbremote.viewmodels.MainViewModelFactory
import com.jcapps.adbremote.views.State
import com.jcapps.adbremote.views.StateView
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.math.roundToInt

/**
 * Remote-control home screen: D-pad / keys / physical buttons, drawer with tools grid, SearchView
 * over installed apps + favorites, native ad slot, icon-pack apply intent.
 *
 * Lambda groups inlined here: C1425 (cases 0–29), C0808 (0–12), C1433 (0–13), C0930 (viewModels),
 * C2497 (activity results + nav listener body in C1710 case 18), C0538 (onProceed), C0566 (Timer task),
 * C2097, C0665, C3704, C2925 (flow collectors); suspend lambdas C3583, C1763, C2703, C0553, C3066,
 * C2045, C3593, C2439, C0350, C2418, C3347, C1715, C0967.
 */
class MainActivity : BaseActivity() {
    // obf: C1204

    // obf: f1495 ("toolAdapter")
    private lateinit var toolsAdapter: ToolsAdapter

    // obf: f1496 (C0808 case 6)
    private val animation: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.blink) }

    // obf: f1498 (C0808 case 5) / m821
    val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    // obf: f1499 (C1425 case 6) — C3414 = ActionBarDrawerToggle(activity, drawer, searchBar, p5, ln)
    private val drawerToggle: ActionBarDrawerToggle by lazy {
        ActionBarDrawerToggle(this, binding.drawerMain, binding.searchBar, R.string.open_drawer, R.string.close_drawer_2)
    }

    // obf: f1501 (C1425 case 5)
    private val surfaceColor: Int by lazy { SurfaceColors.SURFACE_3.getColor(this) }

    // obf: f1502 (C1425 case 13) / m827
    val vibrator: HapticFeedback by lazy { HapticFeedback(this) }

    // obf: f1503 (C1425 case 10) / m825
    val configPrefs: AppPrefs by lazy { AppPrefs(prefs) }

    // obf: f1504 (C1425 case 3) / m817
    val appsAdapter: AppsAdapter by lazy {
        AppsAdapter(this, false, null) { _, app, _, _ ->
            // obf: C3704 case 2
            openApp(app, true)
        }
    }

    // obf: f1505 (C1425 case 1)
    private val buttonPressOverlay: ButtonPressOverlay by lazy { ButtonPressOverlay(binding.imageButtonPressOverlay) }

    // obf: f1506 / m826
    val appViewModel: AppViewModel by viewModels()

    // obf: f1508 (C1425 case 2)
    private val adbServiceLauncher: AdbServiceLauncher by lazy {
        AdbServiceLauncher(
            this,
            {
                // obf: C1425 case 15
                binding.content.imageStatusDot.clearAnimation()
                appViewModel.loadApps(cacheDir) { apps ->
                    // obf: C0728 case 0
                    apps.filter { it.isEnabled && it.isLauncherAvailable }.sortedBy { it.name }
                }
                binding.content.textNetworkWarning.visibility = android.view.View.GONE
                startCommandProcessing()
            },
            {
                // obf: C1425 case 16
                mainViewModel.disconnect()
            },
        )
    }

    // obf: f1509 (C0808 case 8)
    val buttonManager: ButtonManager by lazy { ButtonManager(this, vibrator, continuousPressHandler) }

    // obf: f1510 (C0808 case 7)
    private val continuousPressHandler: ContinuousPressHandler by lazy {
        ContinuousPressHandler(this, lifecycleScope)
    }

    // obf: f1511 — set after the first onResume pass (RECON-UNSURE name)
    private var isResumedOnce: Boolean = false

    // obf: f1512 (C1425 case 7) / m828
    val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    // obf: f1513 (C0808 case 9) / m824
    val iconShapePreviewAdapter: IconShapePreviewAdapter by lazy { IconShapePreviewAdapter(this) }

    // obf: f1515 (C1425 case 4)
    private val stateView: StateView by lazy { StateView(binding.includeViewState) }

    // obf: f1516 (C1425 case 11)
    private val appConfigLoader: AppConfigLoader by lazy { AppConfigLoader(this, configPrefs) }

    // obf: f1517
    val backPressHandler: BackPressHandler = BackPressHandler(this)

    // obf: f1518 (C1425 case 12)
    private val adbBinary: AdbBinary by lazy { AdbBinary(this) }

    // obf: f1519 ("mainViewModel")
    lateinit var mainViewModel: MainViewModel

    // obf: f1520 (C1425 case 8)
    private val favApps: FavApps by lazy { FavApps(prefs) }

    // obf: f1521
    var connectionMethod: ConnectionMethod = ConnectionMethod.SOCKET

    // obf: f1493
    private val hidKeyMapper = HidKeyMapper()

    // obf: f1514 — direction -> (HID key, Android DPAD keyCode)
    private val directionKeys: Map<Direction, Pair<VirtualKey, Int>> = mapOf(
        Direction.RIGHT to Pair(VirtualKey(79, null), 22),
        Direction.LEFT to Pair(VirtualKey(80, null), 21),
        Direction.DOWN to Pair(VirtualKey(81, null), 20),
        Direction.UP to Pair(VirtualKey(82, null), 19),
    )

    // obf: f1522 (result = C2497 case 0)
    private val toolLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            loadApps(!isResumedOnce)
        }

    // obf: f1523 (result = C2497 case 1)
    private val settingsLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // obf: AbstractC2110.m4297 — RECON-UNSURE: recreate() (theme / language changed)
                recreate()
            } else {
                setupButtons()
                vibrator.loadSettings()
                continuousPressHandler.loadSettings()
                iconShapePreviewAdapter.iconShape = createShapeAppearanceModel(
                    getIconShape(iconShapePreviewAdapter.context).shape,
                    iconShapePreviewAdapter.cornerSize,
                )
                iconShapePreviewAdapter.notifyDataSetChanged()
                appsAdapter.iconShape = createShapeAppearanceModel(
                    getIconShape(appsAdapter.context).shape,
                    appsAdapter.iconSize,
                )
                appsAdapter.notifyDataSetChanged()
                toolsAdapter.iconShape = createShapeAppearanceModel(
                    getIconShape(toolsAdapter.context).shape,
                    toolsAdapter.cornerSize,
                )
                toolsAdapter.notifyDataSetChanged()
                val size = iconShapePreviewAdapter.currentList.size
                if (size > 0) {
                    iconShapePreviewAdapter.notifyItemRangeChanged(0, size, Unit)
                }
                binding.content.listFavoriteApps.scheduleLayoutAnimation()
                val method = prefs.getConnectionMethod()
                if (connectionMethod != method) {
                    connectionMethod = method
                    mainViewModel.closeClient()
                    mainViewModel.deviceHelper.stop()
                    appViewModel.apiCommandExecutor.job?.cancel()
                    startCommandProcessing()
                }
            }
        }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val usePhysicalButtons = prefs.getBoolean("use_physical_buttons", true)
        val doNotUseBackAction = prefs.getBoolean("do_not_use_back_action", false)
        val doNotUseVolumeButtons = prefs.getBoolean("do_not_use_volume_buttons", false)
        val isBack = event.keyCode == KeyEvent.KEYCODE_BACK
        val isVolume = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isDown = event.action == KeyEvent.ACTION_DOWN
        if (isDown && isBack) {
            val searchView = binding.searchViewSearchApps
            if (searchView.currentTransitionState == SearchView.TransitionState.SHOWN ||
                searchView.currentTransitionState == SearchView.TransitionState.SHOWING
            ) {
                binding.searchViewSearchApps.hide()
                return true
            }
        }
        if (isDown && isBack) {
            if (binding.drawerMain.isDrawerOpen(GravityCompat.START)) {
                binding.drawerMain.closeDrawers()
                return true
            }
        }
        if (mainViewModel.connectionState.value == ConnectionState.DISCONNECTED || !usePhysicalButtons ||
            (doNotUseBackAction && isBack) || (doNotUseVolumeButtons && isVolume)
        ) {
            return super.dispatchKeyEvent(event)
        }
        if (isDown && isBack) {
            backPressHandler.onBackPressed()
            buttonPressOverlay.show(R.drawable.ic_back)
            return true
        }
        when (connectionMethod) {
            ConnectionMethod.SOCKET -> hidKeyMapper.map(event)?.let { sendCommand(it) }
            ConnectionMethod.BINARY -> {
                val keyCode = if (event.action == KeyEvent.ACTION_DOWN) event.keyCode else null
                if (keyCode != null) sendCommand(BinaryCommand.KeyEvent(keyCode))
            }
            ConnectionMethod.SERVER -> {
                val keyCode = if (event.action == KeyEvent.ACTION_DOWN) event.keyCode else null
                if (keyCode != null) sendCommand(ApiCommand.KeyEvent(keyCode))
            }
        }
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            buttonPressOverlay.show(R.drawable.ic_volume_down)
            return true
        }
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return true
        }
        buttonPressOverlay.show(R.drawable.ic_volume_up)
        return true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // obf: C3414.f12222.mo1691() + m5953 (syncState)
        drawerToggle.onConfigurationChanged(newConfig)
        drawerToggle.syncState()
    }

    // installSplashScreen() replaces C3369/C1880.mo4055 (androidx SplashScreen.Impl31/Impl)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        if (prefs.getBoolean("use_dynamic_color", false)) {
            // RECON-UNSURE: DynamicColorsOptions built via R8-merged C1010; default options assumed.
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)
        // obf: inlined helper ("pref", "onProceed")
        if (configPrefs.dataStore.contains("intro_completed")) {
            onProceed(savedInstanceState)
        } else {
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
        }
    }

    /** Main setup once the intro was completed. */
    // obf: C0538 case 0
    private fun onProceed(savedInstanceState: Bundle?) {
        setContentView(binding.root)
        // obf: C0583 case 1 ("main", "view")
        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorMain) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // original: top inset as margin (DrawerLayout.LayoutParams), rest as padding, insets consumed
            val layoutParams = v.layoutParams as androidx.drawerlayout.widget.DrawerLayout.LayoutParams
            layoutParams.topMargin = bars.top
            v.layoutParams = layoutParams
            v.setPadding(bars.left, v.paddingTop, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.searchBar)
        // obf: inlined nav-header helper ("activity", "headerView", "closeDrawer")
        val header = NavHeaderBinding.bind(binding.navViewMain.getHeaderView(0))
        header.textAppVersion.text = getString(R.string.version, "1.1.0", "6")
        // obf: ViewOnClickListenerC2219 case 13 — share app
        header.btnShareApp.setOnClickListener {
            try {
                // obf: C2247 case 15
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.seamlessly_control_android_devices_using, Constants.website))
                intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.adb_remote))
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                startActivity(Intent.createChooser(intent, getString(R.string.share_app)))
            } catch (e: Exception) {
            }
        }
        // obf: ViewOnClickListenerC2376 case 15 — rate app
        header.d1.setOnClickListener {
            // obf: C1425 case 14
            binding.drawerMain.closeDrawers()
            // obf: C2696 default case -> RunnableC0115 case 20
            Timer().schedule(180L) {
                runOnUiThread { DialogRateApp().show(supportFragmentManager, DialogRateApp::class.java.name) }
            }
        }
        mainViewModel = ViewModelProvider(
            viewModelStore,
            MainViewModelFactory(adbBinary, DevicesStore(prefs), this, savedInstanceState),
            defaultViewModelCreationExtras,
        )[MainViewModel::class.java]
        mainViewModel.serverInstaller = ServerInstaller(this, adbBinary)
        // obf: C2497 case 2 (body in C1710 case 18)
        binding.navViewMain.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.m4 -> runDelayed {
                    // obf: C1425 case 28
                    with(PlayStore) { launchLink(configPrefs.config.telegramChannel) }
                }
                R.id.m3 -> runDelayed {
                    // obf: C1425 case 29
                    with(PlayStore) { launchLink(configPrefs.config.facebookPage) }
                }
                R.id.action_whats_new -> runDelayed {
                    // obf: C0808 case 0 (showMarkdownDialog inlined)
                    showMarkdownDialog(lifecycleScope, this, "what_is_new.md")
                }
                R.id.action_settings -> runDelayed {
                    // obf: C0808 case 1
                    settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                }
                R.id.action_app_logs -> runDelayed {
                    // obf: C0808 case 2
                    startActivity(Intent(this, LogsActivity::class.java))
                }
                R.id.action_visit_website -> runDelayed {
                    // obf: C0808 case 3
                    val dialog = DialogOption()
                    dialog.title = getString(R.string.choose_one)
                    dialog.options = Constants.websites.map { Option(it, R.drawable.ic_open_in_new, it) }
                    // obf: C1433 case 7
                    dialog.onOptionSelected = { option ->
                        with(PlayStore) { launchLink(Link(option.key, null)) }
                    }
                    dialog.show(supportFragmentManager, MainActivity::class.simpleName)
                }
                R.id.action_exit_app -> {
                    stopService(Intent(this, AdbService::class.java))
                    mainViewModel.closeClient()
                    // obf: C0808 case 4
                    killAdbServer { finish() }
                }
            }
            binding.drawerMain.closeDrawers()
            true
        }
        // obf: C3388 (SearchView.TransitionListener, body inlined into SearchView)
        binding.searchViewSearchApps.addTransitionListener { _, _, newState ->
            if (newState == SearchView.TransitionState.SHOWING) {
                val coordinatorLayout = binding.coordinatorMain
                coordinatorLayout.setPadding(
                    coordinatorLayout.paddingLeft,
                    0,
                    coordinatorLayout.paddingRight,
                    coordinatorLayout.paddingBottom,
                )
                appViewModel.loadApps(cacheDir) { apps ->
                    // obf: C0728 case 0
                    apps.filter { it.isEnabled && it.isLauncherAvailable }.sortedBy { it.name }
                }
                if (mainViewModel.connectionState.value == ConnectionState.CONNECTED) {
                    searchApps(null)
                }
                setToolbarBackground(true)
            } else if (newState == SearchView.TransitionState.HIDING) {
                setToolbarBackground(false)
                binding.appBar.setExpanded(true, false)
            }
        }
        // obf: C3076 case 0
        binding.searchViewSearchApps.editText.addTextChangedListener(afterTextChanged = { editable -> searchApps(editable?.toString()) })
        val recyclerView = binding.listSearchResults
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = appsAdapter
        // obf: anonymous click listener case 0 — device card
        binding.content.cardConnection.setOnClickListener {
            if (mainViewModel.device.value == null) {
                showConnectDialog()
                return@setOnClickListener
            }
            val dialog = DialogDevices()
            // obf: C1425 case 25
            dialog.onAddDevice = { showConnectDialog() }
            // obf: C1433 case 4
            dialog.onDeviceSelected = { device ->
                mainViewModel.disconnectAll(true) {
                    // obf: C0538 case 2
                    mainViewModel.connect(device)
                    loadApps(true)
                }
            }
            dialog.show(supportFragmentManager, DialogDevices::class.java.name)
        }
        // obf: anonymous click listener case 1 — reconnect
        binding.content.btnReconnect.setOnClickListener {
            ApiClient.clearAppsCache(cacheDir)
            Logger.log("Reconnecting to the device ...")
            mainViewModel.disconnectAll(false) {
                // obf: C1425 case 23
                mainViewModel.connect(null)
            }
        }
        setupButtons()
        // obf: C1425 case 17
        mainViewModel.onConnected = { adbServiceLauncher.startService() }
        // obf: C1425 case 18
        mainViewModel.onConnectionFailed = {
            connectionMethod = ConnectionMethod.BINARY
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.button_control_mode_changed)
                .setMessage(R.string.appears_connected_device_may_not)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        // obf: C1425 case 19 -> RunnableC3533 case 0
        mainViewModel.onAuthRequired = {
            runOnUiThread {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.allow_usb_debugging)
                    .setMessage(R.string.allow_usb_debugging_target_device)
                    .setCancelable(false)
                    .setNegativeButton(R.string.cancel) { _, _ -> mainViewModel.disconnectAll(false, null) }
                    .setPositiveButton(R.string.try_again) { _, _ -> mainViewModel.connectSocket(true) }
                    .show()
            }
        }
        lifecycleScope.launch {
            // obf: C0479 -> C3347
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // obf: C2759
                    networkStateFlow().collect { state -> onNetworkStateChanged(state) }
                }
                launch {
                    // obf: C3902
                    mainViewModel.device.collect { device -> onDeviceChanged(device) }
                }
                launch {
                    // obf: C2975
                    mainViewModel.connectionState.collect { state -> onConnectionStateChanged(state) }
                }
                launch {
                    // obf: C1526 (C2925 case 3)
                    appViewModel.apps.collect { apps ->
                        if (apps != null) {
                            Logger.log(apps.size.toString() + " openable apps listed.")
                            searchApps(null)
                        }
                    }
                }
                launch {
                    // obf: C1623 (C2925 case 4)
                    appViewModel.state.collect { state ->
                        if (state != null) {
                            stateView.setState(state)
                            binding.listSearchResults.visibility = if (listOf(State.DISCONNECTED, State.EMPTY).contains(state)) {
                                android.view.View.GONE
                            } else {
                                android.view.View.VISIBLE
                            }
                        }
                    }
                }
            }
        }
        // obf: C0538 local `currentDirection` (C3559 ObjectRef)
        var currentDirection: Direction? = null
        val controlPad = binding.content.controlPad
        // obf: C0538 case 1
        controlPad.onHapticRequested = { vibrator.perform(controlPad, true) }
        controlPad.onDPadTouchListener = { direction, motionEvent ->
            val action = motionEvent.actionMasked
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                if (direction != null && (action == MotionEvent.ACTION_DOWN || direction != currentDirection)) {
                    if (direction == Direction.CENTER) {
                        sendCommand(
                            when (connectionMethod) {
                                ConnectionMethod.SOCKET -> VirtualKey(40, null)
                                ConnectionMethod.BINARY -> BinaryCommand.KeyEvent(66)
                                ConnectionMethod.SERVER -> ApiCommand.KeyEvent(66)
                            },
                        )
                    } else {
                        continuousPressHandler.start {
                            // obf: C0753 case 6
                            val pair = directionKeys[direction]
                            val command: Any? = if (pair != null) {
                                when (connectionMethod) {
                                    ConnectionMethod.SOCKET -> pair.first
                                    ConnectionMethod.BINARY -> BinaryCommand.KeyEvent(pair.second)
                                    ConnectionMethod.SERVER -> ApiCommand.KeyEvent(pair.second)
                                }
                            } else {
                                null
                            }
                            if (command != null) {
                                sendCommand(command)
                            }
                            vibrator.perform(controlPad, true)
                        }
                    }
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                continuousPressHandler.stop()
                vibrator.perform(controlPad, false)
            }
            currentDirection = direction
        }
        // obf: C1433 case 1 (icon pack loaded)
        loadIconPack(this, null) {
            iconShapePreviewAdapter.onAddClicked = {
                // obf: C1425 case 24
                openApps()
            }
            // obf: C1433 case 2
            iconShapePreviewAdapter.onItemClicked = { app -> openApp(app, false) }
            // obf: C1433 case 3
            iconShapePreviewAdapter.onItemLongClicked = { app ->
                if (prefs.getBoolean("force_stop_app_on_long_press", true)) {
                    appViewModel.forceStopApp(this, app) { _, message ->
                        // obf: C0665 case 1
                        Logger.log(message)
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            val favRecyclerView = binding.content.listFavoriteApps
            favRecyclerView.addItemDecoration(
                SpacingItemDecoration(favRecyclerView.resources.getDimension(R.dimen.fav_apps_spacing).roundToInt()),
            )
            favRecyclerView.adapter = iconShapePreviewAdapter
        }
        // obf: C1433 case 0
        toolsAdapter = ToolsAdapter(this, tools) { toolType -> onToolClicked(toolType) }
        val toolsRecyclerView = binding.content.listTools
        toolsRecyclerView.addItemDecoration(
            SpacingItemDecoration(toolsRecyclerView.resources.getDimension(R.dimen.tools_spacing).roundToInt()),
        )
        toolsRecyclerView.adapter = toolsAdapter
        toolsRecyclerView.scheduleLayoutAnimation()
        lifecycleScope.launch(Dispatchers.IO) {
            // obf: C2439
            Logger.isEnabled = prefs.getBoolean("enable_connection_history", true)
            connectionMethod = prefs.getConnectionMethod()
            withContext(Dispatchers.Main) {
                startConnection()
            }
        }
        loadApps(!isResumedOnce)
        vibrator.loadSettings()
        continuousPressHandler.loadSettings()
        val sensitivity = prefs.getFloat("trackpad_sensitivity", 0.0f)
        val mouse = mainViewModel.adbClient?.mouse
        if (mouse != null) {
            mouse.sensitivity = Math.pow(2.0, (sensitivity.toInt() / 40.0f).toDouble()).toFloat()
        }
        handleIntent(null)
    }

    /** Tool-grid click. */
    // obf: C1433 case 0
    private fun onToolClicked(toolType: ToolType) {
        when (toolType) {
            ToolType.APPS -> openApps()
            ToolType.PROCESSES -> {
                val intent = Intent(this, AppsActivity::class.java)
                intent.putExtra("tool_type", AppsTab.PROCESSES)
                toolLauncher.launch(
                    intent,
                    ActivityOptionsCompat.makeCustomAnimation(this, R.anim.zoom_enter, R.anim.fade_out_slow),
                )
            }
            ToolType.PERFORMANCE -> {
                val dialog = DialogPerformance(appViewModel)
                // obf: C1425 case 20
                dialog.onDismiss = { requestAppReviewIfRecommended(this) }
                dialog.show(supportFragmentManager, DialogPerformance::class.java.name)
            }
            ToolType.SCREENSHOT -> {
                run {
                    val dialog = DialogScreenshot()
                    // obf: C1433 case 5
                    dialog.onScreenshotSaved = { isSaved ->
                        Snackbar.make(binding.root, binding.root.resources.getText(if (isSaved) R.string.screenshot_saved else R.string.failed_save_screenshot), Snackbar.LENGTH_SHORT).show()
                    }
                    // obf: C1425 case 27
                    dialog.onDismiss = { requestAppReviewIfRecommended(this) }
                    dialog.show(supportFragmentManager, DialogScreenshot::class.java.name)
                }
            }
            ToolType.DEVICE_INFO -> {
                val dialog = DialogDeviceInfo(appViewModel)
                // obf: C1425 case 21
                dialog.onDismiss = { requestAppReviewIfRecommended(this) }
                dialog.show(supportFragmentManager, DialogDeviceInfo::class.java.name)
            }
            ToolType.FILES -> {
                run {
                    val intent = Intent(this, FilesActivity::class.java)
                    startActivity(intent, ActivityOptions.makeCustomAnimation(this, R.anim.zoom_enter, R.anim.fade_out_slow).toBundle())
                }
            }
            ToolType.CLIPBOARD -> {
                run {
                    DialogClipboard().show(supportFragmentManager, DialogClipboard::class.java.name)
                }
            }
            ToolType.NTP -> {
                run {
                    DialogNtp().show(supportFragmentManager, DialogNtp::class.java.name)
                }
            }
        }
    }

    // obf: C2925 case 0
    private fun onNetworkStateChanged(state: NetworkState) {
        if (mainViewModel.connectionState.value != ConnectionState.CONNECTED) {
            showNetworkWarning(binding.content.textNetworkWarning, state)
        }
        if (state == NetworkState.WIFI_CONNECTED || state == NetworkState.WIFI_AND_VPN_CONNECTED) {
            val loader = appConfigLoader
            if (loader.isCachedConfigLoaded && loader.job == null && !loader.isLoaded) {
                loader.load()
            }
        }
    }

    // obf: C2925 case 1
    private fun onDeviceChanged(device: AdbDevice?) {
        val ipAddress = device?.ipAddress
        prefs.edit().putString("current_device_ip", ipAddress).apply()
        appViewModel.setIpAddress(ipAddress)
        appsAdapter.appIconLoader.ipAddress = ipAddress
        iconShapePreviewAdapter.appIconLoader.ipAddress = ipAddress
    }

    // obf: C2925 case 2
    private fun onConnectionStateChanged(state: ConnectionState) {
        if (state != ConnectionState.CONNECTED) {
            updateServerState(ServerState.NOT_STARTED)
        }
        when (state) {
            ConnectionState.DISCONNECTED -> {
                val device = mainViewModel.device.value
                binding.content.btnReconnect.visibility = if (device != null) android.view.View.VISIBLE else android.view.View.GONE
                binding.content.textConnectionStatus.text = device?.name ?: getString(R.string.connect_device)
                val indicator = binding.content.imageStatusDot
                indicator.imageTintList = ContextCompat.getColorStateList(indicator.context, R.color.status_disconnected)
                indicator.clearAnimation()
            }
            ConnectionState.CONNECTING -> {
                val name = mainViewModel.device.value?.name ?: "-"
                binding.content.btnReconnect.visibility = android.view.View.GONE
                binding.content.textConnectionStatus.text = getString(R.string.connecting, name)
                val indicator = binding.content.imageStatusDot
                indicator.imageTintList = ContextCompat.getColorStateList(indicator.context, R.color.status_connecting)
                indicator.startAnimation(animation)
            }
            ConnectionState.CONNECTED -> {
                val name = mainViewModel.device.value?.name ?: "-"
                binding.content.btnReconnect.visibility = android.view.View.VISIBLE
                binding.content.textConnectionStatus.text = getString(R.string.connected, name)
                val indicator = binding.content.imageStatusDot
                indicator.imageTintList = ContextCompat.getColorStateList(indicator.context, R.color.status_connected)
            }
        }
    }

    override fun onDestroy() {
        appConfigLoader.job?.cancel()
        buttonPressOverlay.release()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onPause() {
        adbServiceLauncher.unregisterReceiver()
        super.onPause()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        binding.drawerMain.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
    }

    override fun onResume() {
        super.onResume()
        adbServiceLauncher.registerReceiver()
        val loader = appConfigLoader
        val pendingUpdate = loader.pendingUpdate
        if (!loader.isUpdateShown && pendingUpdate != null) {
            loader.showUpdate(pendingUpdate)
            loader.pendingUpdate = null
        }
        lifecycleScope.launch(Dispatchers.IO) {
            // obf: C2703
            val isConnected = mainViewModel.connectionState.value == ConnectionState.CONNECTED
            if (isResumedOnce && !AdbService.isRunning && isConnected) {
                mainViewModel.disconnect()
            }
            isResumedOnce = true
        }
    }

    /** Called once the intro/ad passed: connect the current device (or ask for one) and load the config. */
    // obf: m815
    private fun startConnection() {
        if (mainViewModel.device.value == null) {
            showConnectDialog()
        } else {
            killAdbServer {
                // obf: C1425 case 22
                ApiClient.clearAppsCache(cacheDir)
                mainViewModel.connect(null)
            }
        }
        appConfigLoader.start()
    }

    // obf: m816 (suspend lambda C0553, Dispatchers.IO)
    fun searchApps(query: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val appsInSearch = prefs.getString("apps_in_search", null)
            var apps: List<App> = appViewModel.apps.value?.filter { app ->
                when (appsInSearch) {
                    "user" -> !app.isSystemApp
                    "system" -> app.isSystemApp
                    else -> true
                }
            } ?: emptyList()
            if (!(query == null || query.isBlank())) {
                apps = apps.filter { it.name.contains(query, true) }
            }
            withContext(Dispatchers.Main) {
                // obf: C1715
                stateView.setState(if (apps.isEmpty()) State.EMPTY else State.SUCCESS)
                appsAdapter.differ.submitList(apps)
            }
        }
    }

    // obf: m818
    fun handleIntent(intent: Intent?) {
        val currentIntent = intent ?: getIntent()
        val packageName = currentIntent.getStringExtra("package_name")
        if (currentIntent.action != "com.jcapps.adbremote.ACTION_APPLY_ICON_PACK" || packageName == null) {
            return
        }
        val iconPack = getIconPacks(this).firstOrNull { it.packageName == packageName }
        if (iconPack != null) {
            // obf: C1425 case 9
            DialogIconPackApply(iconPack) { iconShapePreviewAdapter.notifyDataSetChanged() }
                .show(supportFragmentManager, DialogIconPackApply::class.java.name)
        }
    }

    // obf: m819 (TimerTask C0566 case 0, 180 ms)
    fun runDelayed(func: () -> Unit) {
        Timer().schedule(180L) {
            // obf: RunnableC0115 case 3
            runOnUiThread { func() }
        }
    }

    // obf: m820
    fun openApps() {
        val intent = Intent(this, AppsActivity::class.java)
        intent.putExtra("tool_type", AppsTab.APPS)
        toolLauncher.launch(intent, ActivityOptionsCompat.makeCustomAnimation(this, R.anim.zoom_enter, R.anim.fade_out_slow))
    }

    // obf: m823 (DialogConnect, onConnect = C1433 case 6)
    fun showConnectDialog() {
        val dialog = DialogConnect()
        dialog.onDeviceConnected = { device ->
            mainViewModel.connect(device)
            loadApps(true)
        }
        dialog.show(supportFragmentManager, DialogConnect::class.java.name)
    }

    /** Routes a key/command to the active connection ([connectionMethod]). */
    // obf: m829
    fun sendCommand(command: Any) {
        when (connectionMethod) {
            ConnectionMethod.SOCKET -> mainViewModel.enqueue(command)
            ConnectionMethod.BINARY -> mainViewModel.deviceHelper.send(command as BinaryCommand)
            ConnectionMethod.SERVER -> appViewModel.apiCommandExecutor.send(command as ApiCommand)
        }
        if (mainViewModel.connectionState.value == ConnectionState.CONNECTED) {
            hasSentCommand = true
        }
    }

    // obf: m830
    private fun setupButtons() {
        buttonManager.listener = backPressHandler
        val fq = binding.content
        buttonManager.setupButtons(fq.btnVolumeMute, fq.btnPower, fq.btnKeyboard, fq.btnTrackpad, fq.btnBack, fq.btnHome, fq.btnVolumeDown, fq.btnVolumeUp, fq.btnNumpad, fq.btnExtension, fq.btnSkipPrevious, fq.btnSkipNext)
    }

    // obf: m831
    fun startCommandProcessing() {
        when (connectionMethod) {
            ConnectionMethod.SOCKET -> mainViewModel.connectSocket(true)
            ConnectionMethod.BINARY -> mainViewModel.deviceHelper.start()
            ConnectionMethod.SERVER -> appViewModel.apiCommandExecutor.start()
        }
    }

    // obf: m833 (suspend lambda C3066, Dispatchers.IO)
    fun loadApps(refresh: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val apps = favApps.getApps(mainViewModel.device.value?.ipAddress)
            val items = ArrayList<Fav>()
            items.addAll(apps.map { Fav.Data(it) }.reversed())
            items.add(Fav.Add)
            withContext(Dispatchers.Main) {
                // obf: C0967
                iconShapePreviewAdapter.submitList(items) {
                    if (refresh) {
                        binding.content.listFavoriteApps.scheduleLayoutAnimation()
                    }
                }
            }
        }
    }

    // obf: m834 (binding f6496 background = surface color or transparent)
    fun setToolbarBackground(elevated: Boolean) {
        binding.root.setBackgroundColor(if (elevated) surfaceColor else 0)
    }

    // obf: m835 (callback C0665 case 0)
    fun openApp(app: App, closeSearch: Boolean) {
        if (closeSearch) {
            binding.searchViewSearchApps.hide()
            binding.appBar.setExpanded(true, false)
        }
        appViewModel.launchApp(this, app) { isSuccess, message ->
            // obf: C0665 case 0
            Logger.log(message)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            if (isSuccess) {
                requestAppReviewIfRecommended(this)
            }
        }
    }
}

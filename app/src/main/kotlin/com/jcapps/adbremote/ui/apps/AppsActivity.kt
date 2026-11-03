package com.jcapps.adbremote.ui.apps

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.core.app.ActivityOptionsCompat
import androidx.appcompat.widget.SearchView
import androidx.core.util.Pair
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.snackbar.Snackbar
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.apps.AppsAdapter
import com.jcapps.adbremote.dao.FavApps
import com.jcapps.adbremote.databinding.ActivityAppsBinding
import com.jcapps.adbremote.dialogs.apps.DialogAppFilter
import com.jcapps.adbremote.model.App
import com.jcapps.adbremote.model.AppAction
import com.jcapps.adbremote.model.AppOpenable
import com.jcapps.adbremote.model.AppState
import com.jcapps.adbremote.model.AppType
import com.jcapps.adbremote.model.AppsTab
import com.jcapps.adbremote.model.TvSupport
import com.jcapps.adbremote.prefs.AppPrefs
import com.jcapps.adbremote.services.AdbService
import com.jcapps.adbremote.ui.BaseActivity
import com.jcapps.adbremote.utils.AdbServiceLauncher
import com.jcapps.adbremote.utils.AppOptions
import com.jcapps.adbremote.utils.Logger
import com.jcapps.adbremote.utils.PlayStore
import com.jcapps.adbremote.utils.requestAppReviewIfRecommended
import com.jcapps.adbremote.viewmodels.AppViewModel
import com.jcapps.adbremote.views.State
import com.jcapps.adbremote.views.StateView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Remote app manager: installed apps ([AppsTab.APPS]) or running processes ([AppsTab.PROCESSES])
 * of the current device via the on-device API server; search, filter, sort, favourites, per-app
 * actions ([com.jcapps.adbremote.model.AppAction]) and "force stop all" for processes.
 */
class AppsActivity : BaseActivity(), SearchView.OnQueryTextListener {
    // obf: C1634

    // obf: f1680 / m872 (C0592 case 5)
    private val binding: ActivityAppsBinding by lazy { ActivityAppsBinding.inflate(layoutInflater) }

    // obf: f1670 (C0592 case 6)
    private val stateView: StateView by lazy { StateView(binding.includeViewState) }

    // obf: f1665 (C0592 case 7)
    private val dataStore: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    // obf: f1683 (C0592 case 8)
    private val favApps: FavApps by lazy { FavApps(dataStore) }

    // obf: f1674 (C0592 default)
    private val appPrefs: AppPrefs by lazy { AppPrefs(dataStore) }

    // obf: f1673 (C0592 case 1; synthetic ctor mask 2 = onConnected default; onDisconnected = C0592 case 3)
    private val adbServiceLauncher: AdbServiceLauncher by lazy {
        AdbServiceLauncher(this, onDisconnected = { viewModel.state.value = State.DISCONNECTED })
    }

    // obf: f1675 / m868 (C0592 case 2: AppsAdapter(this, true, C0613 case 0, C3704 case 0))
    private val adapter: AppsAdapter by lazy {
        AppsAdapter(
            this,
            true,
            // obf: C0613 case 0
            { add, app, position ->
                onOptionSelected(
                    if (add) AppAction.ADD_TO_FAV_APPS.toString() else AppAction.REMOVE_FROM_FAV_APPS.toString(),
                    null,
                    app,
                    position,
                )
            },
            // obf: C3704 case 0 (AppOptions.show inlined; option callback = class ဗတမဃလနမ)
            { sharedElements, app, position, isFav ->
                searchView?.clearFocus()
                AppOptions.show(this, restrictedSystemApps, app, isFav) { option ->
                    onOptionSelected(option.key, sharedElements, app, position)
                }
            },
        )
    }

    // obf: f1671 / m867 (C2517 + C0422 cases = viewModels())
    private val viewModel: AppViewModel by viewModels()

    // obf: f1668
    private var toolType: AppsTab = AppsTab.APPS

    /** IP of the current device ("current_device_ip"). */
    // obf: f1667
    private var ipAddress: String? = null

    /** Full (unfiltered) app list of the last load. */
    // obf: f1669
    private var apps: List<App> = emptyList()

    /** Processes queued by "force stop all" and the index of the one being stopped. */
    // obf: f1679
    private var appsToForceStop: List<App> = emptyList()

    // obf: f1666
    private var forceStopIndex: Int = 0

    /** Run the RecyclerView layout animation after the next list commit. */
    // obf: f1676
    private var animateList: Boolean = false

    // obf: f1677
    private var searchView: SearchView? = null

    // obf: f1685
    private var searchQuery: String? = null

    // obf: f1678
    private var type: AppType = AppType.USER

    // obf: f1684
    private var state: AppState = AppState.ALL

    // obf: f1672
    private var openable: AppOpenable = AppOpenable.ALL

    // obf: f1681
    private var tvSupport: TvSupport = TvSupport.ALL

    /** Sort by name A-Z (menu rj) or Z-A (menu rn). */
    // obf: f1682
    private var sortAscending: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // Fix (rebuild): the original padded the root by all system bars, but the list sits under
        // a scrolling AppBar and is taller than the screen, so the bottom padding had no effect and
        // the navigation bar covered the last rows. The bottom inset now goes to the list itself
        // (clipToPadding=false in the layout).
        val listBottomPadding = binding.list.paddingBottom
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
            )
            v.setPadding(bars.left, bars.top, bars.right, 0)
            binding.list.setPadding(
                binding.list.paddingLeft, binding.list.paddingTop, binding.list.paddingRight, listBottomPadding + bars.bottom,
            )
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        // Rebuild change: the toolbar stays in place (the original scrolled it away, also when the
        // list was empty or fit on the screen).
        (binding.toolbar.layoutParams as com.google.android.material.appbar.AppBarLayout.LayoutParams).scrollFlags = 0
        // obf: AbstractC0786.m2571 = SDK 34 getSerializableExtra(name, AppsTab::class.java)
        val toolType = if (Build.VERSION.SDK_INT >= 34) {
            intent.getSerializableExtra(EXTRA_TOOL_TYPE, AppsTab::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_TOOL_TYPE) as? AppsTab
        }
        if (toolType != null) {
            this.toolType = toolType
            supportActionBar?.setTitle(if (this.toolType == AppsTab.APPS) R.string.manage_apps else R.string.oi)
        }
        // RECON-UNSURE: inlined app extension on SwipeRefreshLayout (leftover checks "swipe", "<this>")
        val swipe = binding.swipeRefresh
        swipe.setProgressBackgroundColorSchemeColor(SurfaceColors.SURFACE_3.getColor(swipe.context))
        val recyclerView = binding.list
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = adapter
        postponeEnterTransition()
        // obf: anonymous OnPreDrawListener တဗဂကဏခထ
        recyclerView.viewTreeObserver.addOnPreDrawListener {
            startPostponedEnterTransition()
            true
        }
        ipAddress = dataStore.getString("current_device_ip", null)
        adapter.appIconLoader.ipAddress = ipAddress
        viewModel.setIpAddress(ipAddress)
        // obf: C3733 default case
        binding.swipeRefresh.setOnRefreshListener { loadApps() }
        lifecycleScope.launch {
            // obf: C4040 -> repeatOnLifecycle(STARTED) body C0277
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // obf: C3122 -> collector C1517 case 0
                    viewModel.apps.collect { list ->
                        if (!AdbService.isRunning) {
                            viewModel.state.value = State.DISCONNECTED
                        } else if (list != null) {
                            Logger.log("all " + list.size + " apps listed.")
                            apps = list
                            filterApps()
                        }
                    }
                }
                launch {
                    // obf: C0478 -> collector C1517 default case
                    viewModel.state.collect { apiState ->
                        if (apiState != null) {
                            if (apiState == State.SUCCESS && apps.isNotEmpty() && adapter.differ.currentList.isEmpty()) {
                                stateView.setState(State.EMPTY)
                            } else {
                                stateView.setState(apiState)
                            }
                            binding.swipeRefresh.isRefreshing = !stateView.isShowing && stateView.state == State.LOADING
                            binding.list.visibility =
                                if (listOf(State.DISCONNECTED, State.EMPTY).contains(apiState)) View.GONE else View.VISIBLE
                        }
                    }
                }
            }
        }
        // obf: C0592 case 4
        updateFavApps { loadApps() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.apps, menu)
        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as SearchView
        this.searchView = searchView
        searchView.setOnQueryTextListener(this)
        // Rebuild fix: after collapsing the search the toolbar lost its Search/Filter icons (and the
        // overflow no longer listed them); rebuilding the menu brings them back. The search item is
        // `always` in menu/a.xml, so it no longer shows up in the overflow while expanded.
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                binding.toolbar.post { invalidateOptionsMenu() }
                return true
            }
        })
        if (toolType == AppsTab.APPS) {
            menu?.findItem(R.id.action_force_stop_all)?.isVisible = false
        }
        menu?.findItem(R.id.sort_by_name)?.let { updateSortItem(it) }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (!super.onOptionsItemSelected(item)) {
                    supportFinishAfterTransition()
                }
            }
            R.id.action_force_stop_all -> showConfirmationDialog(R.string.force_stop_all_processes, R.string.force_stop_all_visible_running) {
                // obf: C0592 case 0
                appsToForceStop = adapter.differ.currentList
                if (appsToForceStop.isNotEmpty()) {
                    forceStopIndex = 0
                    binding.progress.visibility = View.VISIBLE
                    binding.progress.max = appsToForceStop.size
                    binding.progress.progress = 0
                    forceStopCurrentApp()
                }
            }
            R.id.action_filter -> DialogAppFilter(type, state, openable, tvSupport) { type, state, openable, tvSupport ->
                // obf: C3704 case 1
                this.type = type
                this.state = state
                this.openable = openable
                this.tvSupport = tvSupport
                filterApps()
            }.show(supportFragmentManager, DialogAppFilter::class.simpleName)
            // Rebuild change: one toolbar toggle instead of the A-Z / Z-A radio items in the overflow.
            R.id.sort_by_name -> {
                sortAscending = !sortAscending
                updateSortItem(item)
                filterApps()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        adbServiceLauncher.unregisterReceiver()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adbServiceLauncher.registerReceiver()
    }

    // SearchView.OnQueryTextListener — R8 stripped the interface methods and inlined their bodies
    // into SearchView (m47 / C3076 case 5); return values are never used there.
    // RECON-UNSURE: return values.
    override fun onQueryTextSubmit(query: String?): Boolean {
        searchQuery = query
        filterApps()
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        searchQuery = newText
        filterApps()
        return false
    }

    /** Applies search, filters, sort and ad slots to [apps] on IO, then submits on Main. */
    // obf: m864 (R8-static; body = suspend lambda C1553, Main part C2006, commit callback RunnableC3440 case 1)
    private fun filterApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            // obf: C1553 (C3559 = Ref.ObjectRef)
            var list = apps
            if (searchQuery != null) {
                list = list.filter { it.name.contains(searchQuery!!, ignoreCase = true) }
            }
            if (type != AppType.ALL) {
                list = list.filter { it.isSystemApp == (type == AppType.SYSTEM) }
            }
            if (state != AppState.ALL) {
                list = list.filter { it.isEnabled == (state == AppState.ENABLED) }
            }
            if (openable != AppOpenable.ALL) {
                list = list.filter { it.isLauncherAvailable == (openable == AppOpenable.YES) }
            }
            if (tvSupport != TvSupport.ALL) {
                list = list.filter { it.isTvSupported == (tvSupport == TvSupport.SUPPORTED) }
            }
            // obf: C3116 case 2 / case 3
            if (sortAscending) {
                list = list.sortedBy { it.name }
            }
            if (!sortAscending) {
                list = list.sortedByDescending { it.name }
            }
            withContext(Dispatchers.Main) {
                // obf: C2006
                stateView.setState(if (list.isEmpty()) State.EMPTY else State.SUCCESS)
                adapter.differ.submitList(list) {
                    if (animateList) {
                        binding.list.scheduleLayoutAnimation()
                        animateList = false
                    }
                }
            }
        }
    }

    /** Force-stops the current entry of [appsToForceStop] (skips [restrictedSystemApps]). */
    // obf: m865 (callback C0494 case 0)
    private fun forceStopCurrentApp() {
        val app = appsToForceStop[forceStopIndex]
        if (restrictedSystemApps.contains(app.packageName)) {
            onForceStopNext()
        } else {
            viewModel.forceStopApp(this, app) { _, _ -> onForceStopNext() }
        }
    }

    /** Executes the option [key] (an [com.jcapps.adbremote.model.AppAction] name) picked for [app]. */
    // obf: m866 (confirm lambdas = class မစဍဍဌဏဃ cases 0..3, fav refresh = class ဠဂမခဠညပ, results C0494 / C0753)
    private fun onOptionSelected(key: String, sharedElements: List<Pair<View, String>>?, app: App, position: Int) {
        run {
            when (key) {
                AppAction.LAUNCH.toString() -> viewModel.launchApp(this, app, ::onActionResult)
                AppAction.ADD_TO_FAV_APPS.toString() -> {
                    favApps.addApp(ipAddress, app)
                    // obf: class ဠဂမခဠညပ case 0
                    updateFavApps { adapter.notifyItemRangeChanged(position, 1, true) }
                    val root = binding.root
                    Snackbar.make(root, root.resources.getText(R.string.added_favorite_apps), Snackbar.LENGTH_SHORT).show()
                }
                AppAction.REMOVE_FROM_FAV_APPS.toString() -> {
                    favApps.removeApp(ipAddress, app)
                    // obf: class ဠဂမခဠညပ default case
                    updateFavApps { adapter.notifyItemRangeChanged(position, 1, false) }
                    val root = binding.root
                    Snackbar.make(root, root.resources.getText(R.string.removed_favorite_apps), Snackbar.LENGTH_SHORT).show()
                }
                AppAction.INFO.toString() -> {
                    val intent = Intent(this, AppInfoActivity::class.java)
                    intent.putExtra(AppInfoActivity.EXTRA_APP, app)
                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        this,
                        *sharedElements!!.toTypedArray(),
                    )
                    startActivity(intent, options.toBundle())
                }
                AppAction.FORCE_STOP.toString() -> showConfirmationDialog(R.string.force_stop_2, R.string.force_stop_app_may_misbehave) {
                    // obf: class မစဍဍဌဏဃ case 0 (result C0494 case 7)
                    viewModel.forceStopApp(this, app) { success, message ->
                        onActionResult(success, message)
                        if (success) {
                            loadApps()
                        }
                    }
                }
                AppAction.CLEAR_DATA.toString() -> showConfirmationDialog(R.string.delete_all_data, R.string.all_app_data_including_files) {
                    // obf: class မစဍဍဌဏဃ case 1 (result C0494 case 6)
                    viewModel.clearAppData(this, app, ::onActionResult)
                }
                AppAction.DISABLE.toString() -> showConfirmationDialog(R.string.disable_app_2, R.string.disable_app_android_other_apps) {
                    // obf: class မစဍဍဌဏဃ case 2 (result C0494 case 8)
                    viewModel.disableApp(this, app, ::onActionResultReload)
                }
                // obf: result C0494 case 2
                AppAction.ENABLE.toString() -> viewModel.enableApp(this, app, ::onActionResultReload)
                AppAction.UNINSTALL.toString() -> showConfirmationDialog(R.string.uninstall_now, R.string.uninstalling_remove_all_app_data) {
                    // obf: class မစဍဍဌဏဃ default case (result C0494 case 5)
                    // Rebuild addition: system apps get a second, explicit warning.
                    if (app.isSystemApp) {
                        showSystemAppUninstallWarning(app) { viewModel.uninstallApp(this, app, ::onActionResultReload) }
                    } else {
                        viewModel.uninstallApp(this, app, ::onActionResultReload)
                    }
                }
                // obf: result C0494 case 3
                AppAction.OPEN_APP_INFO_SETTINGS.toString() -> viewModel.openAppInfoSettings(this, app, ::onActionResult)
                // obf: result C0494 case 4
                AppAction.OPEN_IN_PLAY_STORE.toString() -> viewModel.openAppInStore(this, app, ::onActionResult)
                AppAction.OPEN_IN_LOCAL_PLAY_STORE.toString() -> with(PlayStore) { launchOnPlayStore(app.packageName) }
            }
        }
        requestAppReviewIfRecommended(this)
    }

    /** Logs and shows [message]; counts a feature use on success. */
    // obf: C0494 cases 1, 3, 4, 6 (identical lambdas, one per call site)
    private fun onActionResult(success: Boolean, message: String) {
        Logger.log(message)
        val root = binding.root
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
        if (success) {
            onFeatureUsed()
        }
    }

    /** Like [onActionResult], but reloads the list first on success. */
    // obf: C0494 cases 2, 5, 8 (identical lambdas, one per call site)
    private fun onActionResultReload(success: Boolean, message: String) {
        Logger.log(message)
        val root = binding.root
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
        if (success) {
            loadApps()
            onFeatureUsed()
        }
    }

    /** Loads installed apps or running processes depending on [toolType]. */
    // obf: m869
    private fun loadApps() {
        animateList = true
        when (toolType) {
            AppsTab.APPS -> viewModel.loadApps(cacheDir, null)
            AppsTab.PROCESSES -> viewModel.loadRunningApps()
        }
    }

    /** Counts an "apps" feature use and maybe asks for a review. */
    // obf: m870
    private fun onFeatureUsed() {
    }

    /** Advances the "force stop all" progress; reloads when done. */
    // obf: m871
    private fun onForceStopNext() {
        if (isDestroyed) {
            return
        }
        forceStopIndex++
        binding.progress.setProgressCompat(forceStopIndex, true)
        if (forceStopIndex != appsToForceStop.size) {
            forceStopCurrentApp()
            return
        }
        binding.progress.visibility = View.GONE
        val root = binding.root
        Snackbar.make(root, root.resources.getText(R.string.processes_have_been_forcibly_stopped), Snackbar.LENGTH_SHORT).show()
        apps = emptyList()
        filterApps()
        loadApps()
    }

    /** Refreshes [AppsAdapter.favApps] on IO, then runs [onUpdated] on Main. */
    // obf: m873 (body = suspend lambda C0618, Main part C0444)
    private fun updateFavApps(onUpdated: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            // obf: C0618
            adapter.favApps = favApps.getApps(ipAddress).map { it.packageName }
            withContext(Dispatchers.Main) {
                // obf: C0444
                onUpdated()
            }
        }
    }

    /** Confirmation dialog: positive button R.string.ok runs [onConfirm]. */
    // obf: m874 (listener = DialogInterfaceOnClickListenerC2999 case 1)
    private fun showConfirmationDialog(title: Int, message: Int, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ -> onConfirm() }
            .show()
    }

    /** Icon and title of the sort toggle follow [sortAscending]. */
    private fun updateSortItem(item: MenuItem) {
        item.setIcon(if (sortAscending) R.drawable.ic_sort_az_asc else R.drawable.ic_sort_az_desc)
        item.setTitle(if (sortAscending) R.string.sort_name_z else R.string.sort_name_z_2)
    }

    /** Rebuild addition: second confirmation before a system app is removed (for user 0). */
    private fun showSystemAppUninstallWarning(app: App, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.system_app_uninstall_title)
            .setMessage(getString(R.string.system_app_uninstall_message, app.name, app.packageName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.system_app_uninstall_confirm) { _, _ -> onConfirm() }
            .show()
    }

    companion object {
        // obf: <clinit> literal "tool_type" — RECON-UNSURE constant name
        const val EXTRA_TOOL_TYPE = "tool_type"

        /** Packages that must not be force-stopped (name proven by the "restrictedSystemApps" check). */
        // obf: f1664
        val restrictedSystemApps: List<String> = listOf("com.android.providers.media.module")
    }
}

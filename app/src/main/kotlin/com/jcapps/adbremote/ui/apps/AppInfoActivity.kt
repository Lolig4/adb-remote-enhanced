package com.jcapps.adbremote.ui.apps

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.apps.AppInfoAdapter
import com.jcapps.adbremote.adapters.apps.AppPermissionsAdapter
import com.jcapps.adbremote.databinding.ActivityAppInfoBinding
import com.jcapps.adbremote.model.App
import com.jcapps.adbremote.model.Info
import com.jcapps.adbremote.ui.BaseActivity
import com.jcapps.adbremote.utils.AppIconLoader
import com.jcapps.adbremote.utils.Logger
import com.jcapps.adbremote.utils.copyToClipboard
import com.jcapps.adbremote.utils.loadAppIcon
import com.jcapps.adbremote.viewmodels.AppViewModel
import com.jcapps.adbremote.views.State
import com.jcapps.adbremote.views.StateView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Details (info rows + permissions) of one [App] of the current device; opened from AppsActivity. */
class AppInfoActivity : BaseActivity() {
    // obf: C1621

    // obf: f1656
    private lateinit var app: App

    // obf: f1661 / m863 (lambda case 0)
    private val binding: ActivityAppInfoBinding by lazy { ActivityAppInfoBinding.inflate(layoutInflater) }

    // obf: f1658 (lambda case 1)
    private val stateView: StateView by lazy { StateView(binding.content.includeViewState) }

    // obf: f1657 (lambda case 2)
    private val dataStore: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    // obf: f1663 (lambda default)
    private val appIconLoader: AppIconLoader by lazy { AppIconLoader(this) }

    // obf: f1660 (C2517 + C1116 cases = viewModels())
    private val viewModel: AppViewModel by viewModels()

    // obf: f1659 (C1026 case 0)
    private val infoAdapter = AppInfoAdapter { info ->
        copyToClipboard(info.value)
        val root = binding.root
        Snackbar.make(root, root.resources.getText(R.string.copied_clipboard), Snackbar.LENGTH_SHORT).show()
    }

    // obf: f1662 (C1026 case 1)
    private val permissionsAdapter = AppPermissionsAdapter { permissionName ->
        copyToClipboard(permissionName)
        val root = binding.root
        Snackbar.make(root, root.resources.getText(R.string.copied_clipboard), Snackbar.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.app_info)
        applySystemBarInsets(binding.root)
        lifecycleScope.launch {
            // obf: C1893 -> repeatOnLifecycle(STARTED) body C1665
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // obf: C0563 -> collector C3346 case 0
                    viewModel.appDetails.collect { details ->
                        if (details == null) return@collect
                        Logger.log("App info of " + app.name + " loaded.")
                        binding.content.textPermissions.visibility = View.VISIBLE
                        infoAdapter.differ.submitList(
                            listOf(
                                Info(R.string.app_name, details.name),
                                Info(R.string.package_name, details.packageName),
                                Info(R.string.version_name, details.versionName),
                                Info(R.string.version_code, details.versionCode.toString()),
                                Info(R.string.app_size, Formatter.formatShortFileSize(this@AppInfoActivity, details.size)),
                                Info(R.string.app_type, getString(if (details.isSystemApp) R.string.system else R.string.user)),
                                Info(R.string.app_state, getString(if (details.isEnabled) R.string.enabled else R.string.disabled)),
                                Info(R.string.openable, getString(if (details.isLauncherAvailable) R.string.yes else R.string.no)),
                                Info(R.string.android_tv_support, getString(if (details.isTvSupported) R.string.supported else R.string.n)),
                                Info(R.string.compile_sdk, details.compileSdk.toString()),
                                Info(R.string.target_sdk, details.targetSdk.toString()),
                                Info(R.string.minimum_sdk, details.minSdk.toString()),
                                Info(R.string.installation_time, formatDate(details.firstInstallTime)),
                                Info(R.string.last_update_time, formatDate(details.lastUpdateTime)),
                                Info(R.string.file_path, details.filePath),
                                Info(R.string.data_path, details.dataPath),
                                Info(R.string.launcher_activity, details.launcherActivity ?: ""),
                                Info(R.string.native_library_path, details.nativeLibraryPath),
                                Info(R.string.user_id_uid, details.uid.toString()),
                            ),
                        )
                        permissionsAdapter.differ.submitList(details.permissions)
                    }
                }
                launch {
                    // obf: C3698 -> collector C3346 case 1
                    viewModel.state.collect { state ->
                        if (state != null) {
                            stateView.setState(state)
                            binding.content.swipeRefresh.isRefreshing = !stateView.isShowing && stateView.state == State.LOADING
                        }
                    }
                }
            }
        }
        // obf: AbstractC0786.m2577 = SDK 34 getParcelableExtra(name, App::class.java)
        val app = if (Build.VERSION.SDK_INT >= 34) {
            intent.getParcelableExtra(EXTRA_APP, App::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<App>(EXTRA_APP)
        }
        if (app != null) {
            this.app = app
            val ipAddress = dataStore.getString("current_device_ip", null)
            appIconLoader.ipAddress = ipAddress
            viewModel.setIpAddress(ipAddress)
            with(binding.content) {
                ViewCompat.setTransitionName(title, "title_" + this@AppInfoActivity.app.packageName)
                ViewCompat.setTransitionName(imageAppIcon, "icon_" + this@AppInfoActivity.app.packageName)
                ViewCompat.setTransitionName(textAppVersionName, "version_" + this@AppInfoActivity.app.packageName)
                val density = resources.displayMetrics.density.toInt()
                val strokeWidth = resources.getDimension(R.dimen.icon_stroke_width)
                val backgroundColor = ContextCompat.getColor(this@AppInfoActivity, R.color.white)
                imageAppIcon.loadAppIcon(
                    this@AppInfoActivity.app.packageName,
                    appIconLoader,
                    density,
                    strokeWidth,
                    backgroundColor,
                )
                title.text = this@AppInfoActivity.app.name
                textAppVersionName.text = textAppVersionName.context.getString(
                    R.string.text,
                    this@AppInfoActivity.app.versionName,
                    this@AppInfoActivity.app.versionCode,
                )
                listPermissions.adapter = infoAdapter
                listAppDetails.adapter = permissionsAdapter
                // obf: C3886 = DividerItemDecoration(context, VERTICAL)
                listAppDetails.addItemDecoration(DividerItemDecoration(this@AppInfoActivity, DividerItemDecoration.VERTICAL))
                // obf: C3733 case 3
                swipeRefresh.setOnRefreshListener { loadAppDetails() }
            }
            loadAppDetails()
        }
    }

    // obf: m862 (viewModel part inlined: state = LOADING; launch C1126)
    private fun loadAppDetails() {
        Logger.log("Loading app info of " + app.name + " ...")
        viewModel.loadAppDetails(app.packageName)
    }

    companion object {
        /** "dd/MM/yyyy HH:mm:ss" in the default locale, or "" on failure. */
        // obf: m861
        fun formatDate(time: Long): String {
            return try {
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(time))
            } catch (e: Exception) {
                ""
            }
        }


        // obf: <clinit> literal "app" — RECON-UNSURE constant name
        const val EXTRA_APP = "app"
    }
}

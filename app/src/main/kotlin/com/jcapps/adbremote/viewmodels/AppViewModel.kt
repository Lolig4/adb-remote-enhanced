package com.jcapps.adbremote.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcapps.adbremote.R
import com.jcapps.adbremote.api.remote.ApiClient
import com.jcapps.adbremote.api.remote.ApiCommandExecutor
import com.jcapps.adbremote.api.remote.ApiService
import com.jcapps.adbremote.api.remote.BaseResponse
import com.jcapps.adbremote.model.App
import com.jcapps.adbremote.model.AppDetails
import com.jcapps.adbremote.model.DeviceInfo
import com.jcapps.adbremote.model.Performance
import com.jcapps.adbremote.ui.BaseActivity
import com.jcapps.adbremote.views.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for the on-device ADB-server API ("API mode"): installed/running apps, app details,
 * device info, performance, app actions, plus the [ApiCommandExecutor] for remote input.
 *
 * Errors are only logged (obf `AbstractC2860.f10440` / `C0656.m2454("ERROR: $e")` = app debug
 * logger whose arguments R8 stripped; not ported) and turn into `null` results.
 *
 * Several small functions were inlined by R8 into their single caller; they are restored here
 * and marked "(inlined at ...)".
 */
class AppViewModel : ViewModel() {
    // obf: C0272

    /** Load state of the current screen. Read/written directly by the screens. */
    // obf: f3440
    val state = MutableStateFlow<State?>(null)

    /** Installed / running apps. */
    // obf: f3438
    val apps = MutableStateFlow<List<App>?>(null)

    // obf: f3442
    private val _appDetails = MutableStateFlow<AppDetails?>(null)

    // obf: f3441
    val appDetails: StateFlow<AppDetails?> = _appDetails.asStateFlow()

    // obf: f3437
    val _performance = MutableStateFlow<Performance?>(null)

    // obf: f3439
    val performance: StateFlow<Performance?> = _performance.asStateFlow()

    // obf: f3433
    val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)

    // obf: f3435
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    /** IP of the connected device running the ADB server. */
    // obf: f3434
    var ipAddress: String? = null
        private set

    // obf: f3436 — api = C2247 case 0 (`getApi()`)
    val apiCommandExecutor = ApiCommandExecutor { getApi() }

    // obf: m1964
    private fun getUnreachableMessage(context: Context): String {
        return context.getString(R.string.device_unreachable_try_again_reconnect)
    }

    /** Launches [app] on the device; [callback] gets (success, message). */
    // obf: m1965
    fun launchApp(context: BaseActivity, app: App, callback: (Boolean, String) -> Unit) {
        // obf: C3326 case 4 / C0582 case 1
        execute(
            request = { getApi().launchApp(app.packageName) },
            onResult = { response ->
                val message = if (response == null) {
                    getUnreachableMessage(context)
                } else if (response.status) {
                    context.getString(R.string.app_launched, app.name)
                } else {
                    response.data ?: getUnreachableMessage(context)
                }
                callback(response != null && response.status, message)
            },
        )
    }

    /** Loads the installed apps (HTTP-cached in [cacheDir]); [filter] post-processes the list. */
    // obf: m1966
    fun loadApps(cacheDir: File, filter: ((List<App>) -> List<App>)?) {
        state.value = State.LOADING
        viewModelScope.launch {
            // obf: C1407
            val result = runCatching {
                ApiClient.create(ipAddress, cacheDir).getApps().data?.let { list ->
                    if (filter != null) filter(list) else list
                }
            }
            // ERROR log (stripped): result.exceptionOrNull()
            val list = result.getOrNull()
            state.value = when {
                list == null -> State.DISCONNECTED
                list.isEmpty() -> State.EMPTY
                else -> State.SUCCESS
            }
            if (list != null) {
                apps.value = list
            }
        }
    }

    /** Loads performance stats; sets LOADING first when [showLoading]. */
    // obf: m1967
    fun loadPerformance(showLoading: Boolean) {
        if (showLoading) {
            state.value = State.LOADING
        }
        viewModelScope.launch {
            // obf: C3461
            val performance = runCatching { getApi().getPerformance().data }.getOrNull()
            if (performance != null) {
                state.value = State.SUCCESS
                _performance.value = performance
            }
        }
    }

    /** Runs [request]; on failure logs and passes null; [onResult] runs on Main. */
    // obf: m1968
    fun <T> execute(request: suspend () -> T, onResult: (T?) -> Unit) {
        viewModelScope.launch {
            // obf: C1167
            val result = runCatching { request() }.getOrNull()
            viewModelScope.launch(Dispatchers.Main) {
                // obf: C2861
                onResult(result)
            }
        }
    }

    /** Force-stops [app] on the device; [callback] gets (success, message). */
    // obf: m1969
    fun forceStopApp(context: Context, app: App, callback: (Boolean, String) -> Unit) {
        // obf: C3326 case 3 / C0582 case 0
        execute(
            request = { getApi().forceStopApp(app.packageName) },
            onResult = { response: BaseResponse<String>? ->
                val message = if (response == null) {
                    getUnreachableMessage(context)
                } else if (response.status) {
                    context.getString(R.string.app_force_stopped, app.name)
                } else {
                    response.data ?: getUnreachableMessage(context)
                }
                callback(response?.status ?: false, message)
            },
        )
    }

    /** Switches to another device: stores [ipAddress] and clears the app list. */
    // obf: m1970
    fun setIpAddress(ipAddress: String?) {
        this.ipAddress = ipAddress
        apps.value = null
    }

    // obf: m1971
    fun getApi(): ApiService {
        return ApiClient.create(ipAddress)
    }

    /** Loads the running apps. */
    // (inlined at C1634 / ApiAppsActivity: state = LOADING; launch C0210)
    fun loadRunningApps() {
        state.value = State.LOADING
        viewModelScope.launch {
            // obf: C0210
            val list = runCatching { getApi().getRunningApps().data }.getOrNull()
            state.value = when {
                list == null -> State.DISCONNECTED
                list.isEmpty() -> State.EMPTY
                else -> State.SUCCESS
            }
            if (list != null) {
                apps.value = list
            }
        }
    }

    /** Loads details of [packageName]. */
    // (inlined at C1621: state = LOADING; launch C1126)
    fun loadAppDetails(packageName: String) {
        state.value = State.LOADING
        viewModelScope.launch {
            // obf: C1126
            val details = runCatching { getApi().getAppInfo(packageName).data }.getOrNull()
            state.value = if (details == null) State.DISCONNECTED else State.SUCCESS
            if (details != null) {
                _appDetails.value = details
            }
        }
    }

    /** Loads device info. */
    // (inlined at DialogDeviceInfo C0961: state = LOADING; launch C2543)
    // RECON-UNSURE: whether `Logger.log("Loading device info ...")` (logged just before) was
    //  inside this function or at the caller — left at the caller.
    fun loadDeviceInfo() {
        state.value = State.LOADING
        viewModelScope.launch {
            // obf: C2543
            val info = runCatching { getApi().getDeviceInfo().data }.getOrNull()
            state.value = if (info == null) State.DISCONNECTED else State.SUCCESS
            if (info != null) {
                _deviceInfo.value = info
            }
        }
    }

    /** Clears the data of [app] on the device; [callback] gets (success, message). */
    // (inlined at confirm lambda class မစဍဍဌဏဃ case 1)
    fun clearAppData(context: Context, app: App, callback: (Boolean, String) -> Unit) {
        // obf: C3326 case 0 / C0753 case 3
        execute(
            request = { getApi().clearAppData(app.packageName) },
            onResult = { response ->
                val message = if (response == null) {
                    getUnreachableMessage(context)
                } else if (response.status) {
                    context.getString(R.string.app_data_cleared, app.name)
                } else {
                    response.data ?: getUnreachableMessage(context)
                }
                callback(response?.status ?: false, message)
            },
        )
    }

    /** Disables [app] on the device (drops the cached app list first); [callback] gets (success, message). */
    // (inlined at confirm lambda class မစဍဍဌဏဃ case 2)
    fun disableApp(context: Context, app: App, callback: (Boolean, String) -> Unit) {
        ApiClient.clearAppsCache(context.cacheDir)
        // obf: C3326 case 1 / C0753 case 5
        execute(
            request = { getApi().disableApp(app.packageName) },
            onResult = { response ->
                val message = if (response == null) {
                    getUnreachableMessage(context)
                } else if (response.status) {
                    context.getString(R.string.app_disabled, app.name)
                } else {
                    response.data ?: getUnreachableMessage(context)
                }
                callback(response?.status ?: false, message)
            },
        )
    }

    /** Enables [app] on the device (drops the cached app list first); [callback] gets (success, message). */
    // (inlined at C1634.m866)
    fun enableApp(context: Context, app: App, callback: (Boolean, String) -> Unit) {
        ApiClient.clearAppsCache(context.cacheDir)
        // obf: C3326 case 2 / C0753 case 0
        execute(
            request = { getApi().enableApp(app.packageName) },
            onResult = { response ->
                val message = if (response == null) {
                    getUnreachableMessage(context)
                } else if (response.status) {
                    context.getString(R.string.app_enabled, app.name)
                } else {
                    response.data ?: getUnreachableMessage(context)
                }
                callback(response?.status ?: false, message)
            },
        )
    }

    /** Uninstalls [app] from the device (drops the cached app list first); [callback] gets (success, message). */
    // (inlined at confirm lambda class မစဍဍဌဏဃ default case)
    fun uninstallApp(context: Context, app: App, callback: (Boolean, String) -> Unit) {
        ApiClient.clearAppsCache(context.cacheDir)
        // obf: C3326 case 7 / C0753 case 4
        // Rebuild change: the server's `app?type=uninstall` always answers "App uninstalled." even when
        // `pm` fails (system apps always fail without `--user 0`). Run `pm` via the server's `run`
        // endpoint instead and report its real result.
        val command = "pm uninstall " + (if (app.isSystemApp) "--user 0 " else "") + app.packageName
        execute(
            request = { getApi().run(command) },
            onResult = { response ->
                val data = response?.data as? Map<*, *>
                val output = (data?.get("output") as? String)?.trim().orEmpty()
                val error = (data?.get("error") as? String)?.trim().orEmpty()
                val exitCode = (data?.get("exit_code") as? Number)?.toInt()
                val success = response?.status == true && exitCode == 0 && output.contains("Success")
                val message = when {
                    response == null -> getUnreachableMessage(context)
                    success -> context.getString(R.string.app_uninstalled, app.name)
                    else -> output.ifEmpty { error }.ifEmpty { getUnreachableMessage(context) }
                }
                callback(success, message)
            },
        )
    }

    /** Opens the system app-info settings of [app] on the device; [callback] gets (success, message). */
    // (inlined at C1634.m866)
    fun openAppInfoSettings(context: Context, app: App, callback: (Boolean, String) -> Unit) {
        // obf: C3326 case 6 / C0753 case 1
        execute(
            request = { getApi().openAppInfoSettings(app.packageName) },
            onResult = { response ->
                val message = if (response == null) {
                    getUnreachableMessage(context)
                } else if (response.status) {
                    context.getString(R.string.app_info_settings_launched, app.name)
                } else {
                    response.data ?: getUnreachableMessage(context)
                }
                callback(response?.status ?: false, message)
            },
        )
    }

    /** Opens [app] in the device's app store; [callback] gets (success, message). */
    // (inlined at C1634.m866)
    fun openAppInStore(context: Context, app: App, callback: (Boolean, String) -> Unit) {
        // obf: C3326 case 5 / C0753 case 2
        execute(
            request = { getApi().openAppInStore(app.packageName) },
            onResult = { response ->
                val message = if (response == null) {
                    getUnreachableMessage(context)
                } else if (response.status) {
                    context.getString(R.string.app_launched_play_store, app.name)
                } else {
                    response.data ?: getUnreachableMessage(context)
                }
                callback(response?.status ?: false, message)
            },
        )
    }
}

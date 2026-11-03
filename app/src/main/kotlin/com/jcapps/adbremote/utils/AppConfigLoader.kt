package com.jcapps.adbremote.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.jcapps.adbremote.Constants
import com.jcapps.adbremote.dialogs.DialogUpdate
import com.jcapps.adbremote.model.AppConfig
import com.jcapps.adbremote.prefs.AppPrefs
import com.jcapps.adbremote.ui.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Loads the remote app configuration (`<website>/api/app_v6.json`, trying every website mirror
 * in turn), caches it in [AppPrefs] and shows [DialogUpdate] for a newer app version.
 *
 * RECON-UNSURE: class/member names (unregistered C3479; previously noted as "ad helper").
 */
class AppConfigLoader(
    // obf: f12406 ("activity")
    private val activity: BaseActivity,
    // obf: f12405 ("configPrefs")
    private val configPrefs: AppPrefs,
) {
    // obf: C3479

    // obf: f12404 — remaining mirrors to try
    private var hosts: MutableList<String> = ArrayList()

    // obf: f12407 — update to show once the activity is resumed
    var pendingUpdate: AppConfig.Update? = null

    // obf: f12408 — cached config applied
    var isCachedConfigLoaded: Boolean = false

    // obf: f12409
    private var host: String = Constants.website

    // obf: f12410
    var job: Job? = null

    // obf: f12411
    var isUpdateShown: Boolean = false

    // obf: f12412
    var isLoaded: Boolean = false

    /** Applies the cached config on the main thread, then fetches the remote one. (MainActivity.startConnection) */
    // (inlined at MainActivity m815: job = lifecycleScope.launch(IO) { C1763 })
    fun start() {
        job = activity.lifecycleScope.launch(Dispatchers.IO) {
            // obf: C1763
            val config = configPrefs.config
            withContext(Dispatchers.Main) {
                // obf: C4022
                job = null
                host = config.preferredWebsite
                val update = config.update
                if (update != null && update.version > 6 && !update.optional) {
                    showUpdate(update)
                }
                isCachedConfigLoaded = true
                load()
            }
        }
    }

    // obf: m6015
    fun load() {
        hosts = Constants.websites.toMutableList()
        if (!hosts.contains(host)) {
            hosts.add(0, host)
        }
        load(hosts.removeAt(0))
    }

    // obf: m6016
    fun load(host: String) {
        val url = StringBuilder(host).append("/api/app_v6.json").toString()
        job = activity.lifecycleScope.launch(Dispatchers.IO) {
            // obf: C3163
            try {
                val client = OkHttpClient.Builder()
                    .addNetworkInterceptor { chain ->
                        // obf: C1212 case 2
                        val request = chain.request().newBuilder()
                            .header("Cache-Control", CacheControl.Builder().maxAge(1, TimeUnit.HOURS).build().toString())
                            .build()
                        chain.proceed(request)
                    }
                    .connectTimeout(3, TimeUnit.SECONDS)
                    // RECON-UNSURE: cache max size was folded away by R8 (same placeholder as ApiClient).
                    .cache(Cache(File(activity.cacheDir, "http_cache"), 10L * 1024 * 1024))
                    .build()
                val body = client.newCall(Request.Builder().url(url).build()).execute().body
                val json = body?.string()
                isLoaded = true
                job = null
                val config = Gson().fromJson(json, AppConfig::class.java)
                config.preferredWebsite = host
                withContext(Dispatchers.Main) {
                    // obf: C0965
                    isLoaded = true
                    configPrefs.saveConfig(config)
                    val update = config.update
                    if (update != null && !isUpdateShown && update.version > 6) {
                        if (update.optional && update.skippable) {
                            val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
                            if (prefs.getLong("ignored_update_version", -1L) != update.version) {
                                showUpdate(update)
                            }
                        } else {
                            showUpdate(update)
                        }
                    }
                }
            } catch (e: Exception) {
                job = null
                if (hosts.isNotEmpty()) {
                    load(hosts.removeAt(0))
                }
            }
        }
    }

    // obf: m6017
    fun showUpdate(update: AppConfig.Update) {
        if (activity.lifecycle.currentState < Lifecycle.State.RESUMED) {
            pendingUpdate = update
            return
        }
        DialogUpdate.show(activity.supportFragmentManager, update)
        isUpdateShown = true
    }
}

package com.jcapps.adbremote

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.jcapps.adbremote.ntp.InMemorySntpCache
import com.jcapps.adbremote.ntp.NtpOptions
import com.jcapps.adbremote.ntp.SntpClient
import com.jcapps.adbremote.utils.applyTheme
import com.jcapps.adbremote.utils.wrapLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Application entry point.
 *
 * Variant 1 of the reconstruction: the native library `libapp.so` is dropped. Its recovered
 * constants now live in [NativeConfig]; `System.loadLibrary("app")` and the 21 `external`
 * declarations (`m787`..`m807`) are not ported.
 *
 * Installs BouncyCastle, applies the stored theme/language and kicks off an initial NTP time sync.
 * (Ads and premium were removed from this build.)
 */
// obf: C0104
class AdbRemoteApp :
    Application(),
    Application.ActivityLifecycleCallbacks {

    /** Shared SNTP time source. obf: f1488 / m810 (lazy = C3079 case 0). */
    // obf: f1488
    val sntpClient: SntpClient by lazy {
        SntpClient(
            NtpOptions(
                syncDispatcher = Dispatchers.IO,
                listenerDispatcher = Dispatchers.Main,
                cache = InMemorySntpCache(), // obf: C1518(11, false)
                elapsedTime = { android.os.SystemClock.elapsedRealtime() }, // obf: C3079(1)
                connectionTimeout = 3.seconds,
                ntpHosts = Constants.ntpHosts.toList(),
                delayBetweenRetryLoop = 3.seconds,
                serverResponseDelayMax = 750.milliseconds,
            ),
        )
    }

    /** Top-most started activity. obf: f1490. */
    // obf: f1490
    var currentActivity: Activity? = null

    init {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }

    override fun attachBaseContext(base: Context?) {
        if (base == null) {
            super.attachBaseContext(null)
            return
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(base)
        applyTheme(prefs.getString("theme", "system")) // obf: AbstractC3745.m6302
        super.attachBaseContext(wrapLocale(base)) // obf: AbstractC3179.m5627
    }

    override fun onCreate() {
        super<Application>.onCreate()
        // Variant 1: `System.loadLibrary("app")` DROPPED — constants in NativeConfig.
        // The native config getters (ex-libapp) obtained the app context via
        // ActivityThread.currentApplication(); mirror that with a process-wide holder.
        appContext = this
        registerActivityLifecycleCallbacks(this)
        CoroutineScope(Dispatchers.IO).launch {
            // C1504
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@AdbRemoteApp)
            if (prefs.getBoolean("use_dynamic_color", false)) {
                withContext(Dispatchers.Main) {
                    // obf: C3669
                    DynamicColors.applyToActivitiesIfAvailable(this@AdbRemoteApp)
                }
            }
        }
        val sntp = sntpClient
        if (sntp.cachedTime() != null) return // obf: m5838
        sntp.sync() // obf: m5839(null, null)
    }

    // ---- Application.ActivityLifecycleCallbacks ----

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        // process-wide app context, set in onCreate; stands in for the native currentApplication()
        lateinit var appContext: android.content.Context
    }
}

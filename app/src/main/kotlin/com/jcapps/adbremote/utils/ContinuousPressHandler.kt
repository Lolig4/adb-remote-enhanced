package com.jcapps.adbremote.utils

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.prefs.JsonPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/** Repeats an action while a button is held: first after [initialDelay], then every [repeatDelay] ms. */
class ContinuousPressHandler(
    context: Context,
    // obf: f11168
    private val lifecycleScope: LifecycleCoroutineScope,
) {
    // obf: C3105
    // obf: f11167
    var job: Job? = null

    // obf: f11170
    private val initialDelay: Long = 500L

    // obf: f11169
    private var repeatDelay: Long = 100L

    // obf: f11171
    private val prefs = JsonPrefs(PreferenceManager.getDefaultSharedPreferences(context))

    init {
        loadSettings()
    }

    // obf: m5542
    fun loadSettings() {
        // RECON-UNSURE: `dataStore` accessor of JsonPrefs (see HapticFeedback)
        repeatDelay = prefs.dataStore.getFloat("continuous_press_delay", repeatDelay.toFloat()).toDouble().roundToLong()
    }

    // obf: m5543 (body = SuspendLambda C3866, inner launch = C0795)
    fun start(func: (CoroutineScope) -> Unit) {
        job?.cancel()
        job = lifecycleScope.launch(Dispatchers.Default) {
            var first = true
            while (isActive) {
                launch(Dispatchers.Main) { func(this) }
                if (first) {
                    delay(initialDelay)
                    first = false
                } else {
                    delay(repeatDelay)
                }
            }
        }
    }

    // RECON-UNSURE: inlined at every call site (job?.cancel(); job = null)
    fun stop() {
        job?.cancel()
        job = null
    }
}

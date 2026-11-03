package com.jcapps.adbremote.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.prefs.JsonPrefs

class HapticFeedback(
    // obf: f1829
    private val context: Context,
) {
    // obf: c2
    // obf: f1828
    private val prefs = JsonPrefs(PreferenceManager.getDefaultSharedPreferences(context))

    // obf: f1831
    private var enabled: Boolean = true

    // obf: f1830
    private var disableOnRelease: Boolean = false

    // obf: f1833
    private var useVibratorApi: Boolean = false

    // obf: f1832
    private var vibrator: Vibrator? = null

    init {
        loadSettings()
    }

    // obf: m1116
    fun loadSettings() {
        // RECON-UNSURE: JsonPrefs exposes its SharedPreferences as `dataStore` (ctor param name);
        //  the original likely used an inline JsonPrefs getter — align with core-app's JsonPrefs.
        enabled = prefs.dataStore.getBoolean("enable_haptic_feedback", true)
        disableOnRelease = prefs.dataStore.getBoolean("disable_haptic_on_release", false)
        useVibratorApi = prefs.dataStore.getBoolean("use_vibrator_api_for_haptic", false)
    }

    // obf: m1117
    fun perform(view: View, isPressed: Boolean) {
        if (!enabled) return
        if (!isPressed && disableOnRelease) return
        if (!useVibratorApi) {
            val feedbackConstant = if (isPressed) {
                1 // HapticFeedbackConstants.VIRTUAL_KEY
            } else if (Build.VERSION.SDK_INT >= 27) {
                8 // HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
            } else {
                3 // HapticFeedbackConstants.KEYBOARD_TAP
            }
            try {
                view.performHapticFeedback(feedbackConstant, 2)
            } catch (_: Exception) {
            }
            return
        }
        if (vibrator == null) {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                ContextCompat.getSystemService(context, Vibrator::class.java)
            }
        }
        val sdk = Build.VERSION.SDK_INT
        if (sdk >= 29) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else if (sdk >= 26) {
            vibrator?.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(20L)
        }
    }
}

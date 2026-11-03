package com.jcapps.adbremote.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.R
import com.jcapps.adbremote.ads.InterstitialAdManager
import com.jcapps.adbremote.premium.PremiumManager
import com.jcapps.adbremote.utils.hasConnected
import com.jcapps.adbremote.utils.wrapLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Base of every activity in the app.
 *
 * Applies the stored UI language (via [wrapLocale] in [attachBaseContext]), turns on
 * edge-to-edge, shows the Up affordance when not the task root, overrides the close
 * transition and performs the one-time `language` preference migration on API 33+.
 */
// obf: AbstractActivityC1418
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!isTaskRoot) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val supportedLanguages = resources.getStringArray(R.array.en_my_entries)
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val appLocales = AppCompatDelegate.getApplicationLocales()
            var language = if (appLocales.isEmpty) {
                Locale.getDefault().language
            } else {
                appLocales[0]?.language ?: "en"
            }
            if (language !in supportedLanguages) {
                language = "en"
            }
            val stored = prefs.getString("language", null)
            if (!prefs.getBoolean("language_migrated", false)) {
                prefs.edit().putBoolean("language_migrated", true).apply()
                if (stored != null) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(stored))
                    language = stored
                }
            }
            if (language == stored) return
            prefs.edit().putString("language", language).apply()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!isTaskRoot && item.itemId == android.R.id.home && !super.onOptionsItemSelected(item)) {
            supportFinishAfterTransition()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.zoom_return, R.anim.fade_out_fast)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.zoom_return, R.anim.fade_out_fast)
        }
    }

    /**
     * Off the main thread: when the user is not premium, a review has been recommended and
     * the ADB service connected in this process, show an interstitial ad on the main thread.
     */
    // obf: m3349 (body = suspend lambda C0560)
    fun maybeRequestReview() {
        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@BaseActivity)
            // obf: C1299.m3244
            val isPremium = PremiumManager.isPremium(application)
            val reviewRecommended = prefs.getBoolean("app_review_recommended", false)
            if (!isPremium && reviewRecommended && hasConnected) {
                withContext(Dispatchers.Main) {
                    // obf: C2018
                    InterstitialAdManager.show(this@BaseActivity)
                }
            }
        }
    }

    companion object {
        /** Pads [view] by the system-bar + display-cutout insets and consumes them. */
        // obf: m3348 (listener = C0583 case 0)
        fun applySystemBarInsets(view: ViewGroup) {
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsetsCompat.CONSUMED
            }
        }
    }
}

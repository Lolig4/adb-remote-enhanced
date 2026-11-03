package com.jcapps.adbremote.ui

import android.app.ActivityOptions
import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jcapps.adbremote.Constants
import com.jcapps.adbremote.R
import com.jcapps.adbremote.api.adb.ServerInstaller
import com.jcapps.adbremote.dao.FavApps
import com.jcapps.adbremote.databinding.ActivitySettingsBinding
import com.jcapps.adbremote.dialogs.home.DialogAdbServerToken
import com.jcapps.adbremote.dialogs.icons.DialogIconPackChooser
import com.jcapps.adbremote.dialogs.icons.DialogIconShapeChooser
import com.jcapps.adbremote.model.Link
import com.jcapps.adbremote.settings.IconPackPreference
import com.jcapps.adbremote.settings.IconShapePreference
import com.jcapps.adbremote.settings.MaterialListPreferenceDialog
import com.jcapps.adbremote.utils.PlayStore
import com.jcapps.adbremote.utils.applyTheme
import com.jcapps.adbremote.utils.formatFileSize
import com.jcapps.adbremote.utils.killAdbServer
import com.jcapps.adbremote.utils.showMarkdownDialog
import java.io.File
import java.util.Timer
import java.util.UUID
import kotlin.concurrent.schedule

class SettingsActivity : BaseActivity() {
    // obf: C1468

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarCollapsing)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings)
        applySystemBarInsets(binding.root)
        // obf: C3853 — RECON-UNSURE: empty RewardedInterstitialAdLoadCallback (pre-load only)
        RewardedInterstitialAd.load(
            this,
            Constants.AD_UNIT_REWARDED_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {},
        )
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame_settings, SettingsFragment())
                .commit()
        }
        if (recreated) {
            setResult(RESULT_OK)
            recreated = false
        }
    }

    class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
        // obf: SharedPreferencesOnSharedPreferenceChangeListenerC0052

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            val clearCache = findPreference<Preference>("clear_cache") ?: return
            val cacheSize = getCacheSize()
            clearCache.summary = cacheSize
            // obf: C1536 case 0
            clearCache.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.clear_cache_2, cacheSize))
                    .setMessage(R.string.all_app_cache_such_app)
                    .setNegativeButton(R.string.cancel, null)
                    // obf: DialogInterfaceOnClickListenerC0807 case 0
                    .setPositiveButton(R.string.clear_cache) { _, _ ->
                        val serverFile = File(requireContext().filesDir, ServerInstaller.ASSET_SERVER_PATH)
                        if (serverFile.exists()) {
                            serverFile.delete()
                        }
                        requireContext().cacheDir?.deleteRecursively()
                        requireContext().externalCacheDir?.deleteRecursively()
                        findPreference<Preference>("clear_cache")?.summary = getCacheSize()
                        Snackbar.make(requireView(), getString(R.string.freed_device, cacheSize), Snackbar.LENGTH_SHORT).show()
                    }
                    .show()
                false
            }
            val resetAdbConfig = findPreference<Preference>("reset_adb_config") ?: return
            // obf: C3733 case 2
            resetAdbConfig.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.reset_adb_configurations)
                    .setMessage(R.string.users_settings_not_deleted_existing)
                    .setNegativeButton(R.string.cancel, null)
                    // obf: DialogInterfaceOnClickListenerC2999 case 0
                    .setPositiveButton(R.string.reset) { _, _ ->
                        // obf: C2247 case 5
                        killAdbServer { resetAdbConfiguration() }
                    }
                    .show()
                false
            }
            val adbServerToken = findPreference<Preference>("adb_server_token") ?: return
            val prefs = preferenceManager.sharedPreferences ?: return
            adbServerToken.isEnabled = !prefs.getBoolean("use_default_adb_server_token", false)
            var token = prefs.getString("adb_server_token", null)
            if (token == null) {
                token = UUID.randomUUID().toString()
                prefs.edit().putString("adb_server_token", token).apply()
            }
            adbServerToken.summary = token
            // obf: C1536 case 1
            adbServerToken.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val dialog = DialogAdbServerToken()
                // obf: C2192 case 8
                dialog.onTokenSaved = { adbServerToken.summary = it }
                dialog.show(requireActivity().supportFragmentManager, DialogAdbServerToken::class.java.name)
                false
            }
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (sharedPreferences == null || key == null) return
            when (key) {
                // obf: C0566 case 1 (TimerTask)
                "language" -> Timer().schedule(200L) {
                    // obf: RunnableC3749
                    requireActivity().runOnUiThread {
                        if (Build.VERSION.SDK_INT < 33) {
                            recreated = true
                            ActivityCompat.recreate(requireActivity())
                        } else {
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(sharedPreferences.getString("language", "en")),
                            )
                        }
                    }
                }
                "use_dynamic_color" -> {
                    val application: Application = requireActivity().application
                    if (sharedPreferences.getBoolean("use_dynamic_color", false)) {
                        DynamicColors.applyToActivitiesIfAvailable(application)
                    } else {
                        DynamicColors.applyToActivitiesIfAvailable(
                            application,
                            DynamicColorsOptions.Builder().setThemeOverlay(R.style.Theme_AdbRemote_NoActionBar).build(),
                        )
                    }
                    // obf: C2696 case 1 (TimerTask)
                    Timer().schedule(300L) {
                        // obf: RunnableC0081
                        requireActivity().runOnUiThread {
                            recreated = true
                            ActivityCompat.recreate(requireActivity())
                        }
                    }
                }
                "theme" -> {
                    applyTheme(sharedPreferences.getString("theme", "system"))
                    ActivityCompat.recreate(requireActivity())
                }
                "use_default_adb_server_token" -> {
                    val useDefault = sharedPreferences.getBoolean("use_default_adb_server_token", false)
                    val token = if (useDefault) {
                        Constants.DEFAULT_ADB_SERVER_TOKEN
                    } else {
                        UUID.randomUUID().toString()
                    }
                    val preference = findPreference<Preference>("adb_server_token") ?: return
                    sharedPreferences.edit().putString("adb_server_token", token).apply()
                    preference.summary = token
                    preference.isEnabled = !useDefault
                }
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            when (preference.key) {
                "translations" -> with(PlayStore) {
                    requireContext().launchLink(Link(Constants.translationsUrl, null))
                }
                "icon_shape" -> {
                    val dialog = DialogIconShapeChooser()
                    val iconShapePreference = preference as IconShapePreference
                    dialog.onSelected = {
                        iconShapePreference.iconData = it
                        iconShapePreference.notifyIconChanged()
                    }
                    dialog.show(childFragmentManager, DialogIconShapeChooser::class.java.name)
                }
                "icon_style" -> {
                    val dialog = DialogIconPackChooser()
                    val iconPackPreference = preference as IconPackPreference
                    dialog.onSelected = { iconPackPreference.setIconPack(it.packageName) }
                    dialog.show(childFragmentManager, DialogIconPackChooser::class.java.name)
                }
                "developer" -> with(PlayStore) { requireContext().launchLink(Constants.facebookProfileLink) }
                "terms" -> with(PlayStore) { requireContext().launchLink(Link(Constants.appTermsUrl, null)) }
                "privacy_policy" -> with(PlayStore) {
                    requireContext().launchLink(Link(Constants.privacyPolicyUrl, null))
                }
                "acknowledgement" -> showMarkdownDialog(
                    viewLifecycleOwner.lifecycleScope,
                    requireContext(),
                    "acknowledgement.md",
                )
            }
            return super.onPreferenceTreeClick(preference)
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            if (preference !is ListPreference) {
                super.onDisplayPreferenceDialog(preference)
                return
            }
            val dialog = MaterialListPreferenceDialog()
            dialog.arguments = Bundle(1).apply { putString("key", preference.key) }
            @Suppress("DEPRECATION")
            dialog.setTargetFragment(this, 0)
            dialog.show(parentFragmentManager, MaterialListPreferenceDialog::class.java.name)
        }

        /**
         * Wipes caches, the local `.android` ADB key dir, the extracted server jar, all favourite
         * apps and the device list, then restarts the app.
         */
        // obf: C2247 case 5 (R8-inlined Activity restart helper)
        private fun resetAdbConfiguration() {
            requireContext().cacheDir?.deleteRecursively()
            requireContext().externalCacheDir?.deleteRecursively()
            val adbDir = File(requireContext().filesDir, ".android")
            if (adbDir.exists() && adbDir.isDirectory) {
                adbDir.deleteRecursively()
            }
            val serverFile = File(requireContext().filesDir, ServerInstaller.ASSET_SERVER_PATH)
            if (serverFile.exists()) {
                serverFile.delete()
            }
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            FavApps(prefs).clear()
            prefs.edit().remove("adb_devices").apply()
            val activity = requireActivity()
            try {
                val launchIntent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)!!
                activity.startActivity(
                    Intent.makeRestartActivityTask(launchIntent.component),
                    ActivityOptions.makeBasic().toBundle(),
                )
                activity.setResult(RESULT_CANCELED)
                activity.finishAffinity()
            } catch (_: Exception) {
            }
        }

        // obf: m844
        private fun getCacheSize(): String {
            val cacheSize = requireContext().cacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
            val externalCacheSize =
                requireContext().externalCacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
            return formatFileSize(cacheSize + externalCacheSize)
        }
    }

    companion object {
        /** Set before recreating so that the next onCreate reports RESULT_OK. RECON-UNSURE: name */
        // obf: f1612
        var recreated: Boolean = false
    }
}

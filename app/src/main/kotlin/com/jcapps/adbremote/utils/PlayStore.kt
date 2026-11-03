package com.jcapps.adbremote.utils

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.jcapps.adbremote.R
import com.jcapps.adbremote.model.Link

/**
 * Device identity + "open link / Play Store" helpers.
 *
 * The Context helpers were member extension functions of this object in the original (static
 * first parameter named `<this>`, INSTANCE retained): call them inside `with(PlayStore) { ... }`.
 */
object PlayStore {
    // obf: C2548

    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    /** "Manufacturer Model", or just the model when it already starts with the manufacturer. */
    // obf: m4833
    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        if (model.startsWith(manufacturer, ignoreCase = true)) {
            return model
        }
        return "$manufacturer $model"
    }

    // obf: m4834
    @SuppressLint("HardwareIds")
    fun Context.getAndroidId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    /** Opens the Play Store page of [packageName]; falls back to any browser. */
    // obf: m4835
    fun Context.launchOnPlayStore(packageName: String) {
        val url = "https://play.google.com/store/apps/details?id=$packageName"
        launchUrl(url, PLAY_STORE_PACKAGE) {
            // obf: C0538 case 4
            launchUrl(url, null) {
                Toast.makeText(this, R.string.no_app_found_open_link, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // obf: m4836
    fun Context.launchUrl(url: String, packageName: String?, onFailure: () -> Unit) {
        try {
            var intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (packageName != null) {
                intent = intent.setPackage(packageName)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            onFailure()
        }
    }

    /** Opens [link]'s url; on failure its fallback url; else toasts "No app found". */
    // obf: m4837
    fun Context.launchLink(link: Link) {
        launchUrl(link.url, null) {
            // obf: C0538 case 3
            if (link.fallbackUrl != null) {
                launchUrl(link.fallbackUrl, null) {
                    Toast.makeText(this, R.string.no_app_found_open_link, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.no_app_found_open_link, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

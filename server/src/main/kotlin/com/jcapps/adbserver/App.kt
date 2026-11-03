package com.jcapps.adbserver

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.google.gson.annotations.SerializedName

/**
 * One installed app, as returned by `/apps` and `/running-apps` (JSON field names as in the dex).
 * The app side parses it into `com.jcapps.adbremote.model.App`.
 */
// obf: C0105 (toString "App(name=..., isEnabled=..., ...)" -> data class)
data class App(
    // obf: f217 / m136
    @field:SerializedName("name") var name: String,
    // obf: f216 / m142
    @field:SerializedName("is_enabled") var isEnabled: Boolean,
    // obf: f221 / m141
    @field:SerializedName("is_launcher_available") var isLauncherAvailable: Boolean,
    // obf: f219 / m139 (the only final field)
    @field:SerializedName("is_tv_supported") val isTvSupported: Boolean,
    // obf: f223 / m137
    @field:SerializedName("is_system_app") var isSystemApp: Boolean,
    // obf: f222 / m135
    @field:SerializedName("package_name") var packageName: String,
    // obf: f218 / m140
    @field:SerializedName("version_code") var versionCode: Long,
    // obf: f220 / m138
    @field:SerializedName("version_name") var versionName: String,
)

/**
 * Builds the [App] model; null if the package has no ApplicationInfo or anything throws.
 */
// obf: AbstractC0353.m385
fun PackageInfo.toApp(pkgManager: PackageManager): App? {
    return try {
        val applicationInfo = this.applicationInfo ?: return null
        val name = applicationInfo.loadLabel(pkgManager).toString()
        val packageName = applicationInfo.packageName ?: String()
        val isTvSupported = applicationInfo.banner > 0
        val isEnabled = applicationInfo.enabled
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) this.longVersionCode else this.versionCode.toLong()
        val versionName = this.versionName ?: String()
        val flags = applicationInfo.flags
        App(
            name,
            isEnabled,
            pkgManager.resolveMainActivity("android.intent.category.LAUNCHER", packageName) != null ||
                pkgManager.resolveMainActivity("android.intent.category.LEANBACK_LAUNCHER", packageName) != null,
            isTvSupported,
            // FLAG_SYSTEM (1) or FLAG_UPDATED_SYSTEM_APP (128)
            (flags and 1) != 0 || (flags and 128) != 0,
            packageName,
            versionCode,
            versionName,
        )
    } catch (e: Exception) {
        null
    }
}

/**
 * Resolves the MAIN activity of [packageName] in [category].
 * Flags 139776 = MATCH_ALL | MATCH_UNINSTALLED_PACKAGES | MATCH_DISABLED_COMPONENTS.
 */
// obf: AbstractC0353.m388
fun PackageManager.resolveMainActivity(category: String, packageName: String): ResolveInfo? {
    val intent = Intent("android.intent.action.MAIN")
    intent.addCategory(category)
    intent.setPackage(packageName)
    return resolveActivity(intent, 139776)
}

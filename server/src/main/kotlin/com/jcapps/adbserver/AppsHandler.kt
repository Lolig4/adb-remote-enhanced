package com.jcapps.adbserver

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.jcapps.adbserver.parser.AdaptiveIconParser
import com.jcapps.adbserver.parser.ApkFile
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import java.io.File
import java.io.FileOutputStream

/**
 * App endpoints. One class with four modes (R8 kept them as a single class switching on an int):
 *
 * | route         | mode           | original int |
 * |---------------|----------------|--------------|
 * | /app-icon     | [Mode.ICON]         | 0 |
 * | /app          | [Mode.APP]          | 1 |
 * | /apps         | [Mode.APPS]         | 2 |
 * | /running-apps | [Mode.RUNNING_APPS] | 3 |
 */
// obf: C0243 (f506 = mode, f507 = context)
class AppsHandler(param: Param, private val context: Context, private val mode: Mode) : BaseHandler(param) {

    // obf: int f506 (0..3)
    enum class Mode { ICON, APP, APPS, RUNNING_APPS }

    // obf: mo143
    override fun handle(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        when (mode) {
            Mode.ICON -> sendAppIcon(request, response)
            Mode.APP -> handleApp(request, response)
            Mode.APPS -> {
                val pkgManager = context.packageManager
                val apps = ArrayList<App>()
                MiscUtils.ignoreExceptions { listInstalledApps(pkgManager, apps) } // obf: C0279 mode 0
                response.sendSuccess(apps)
            }
            Mode.RUNNING_APPS -> {
                val runningApps = ArrayList<App>()
                MiscUtils.ignoreExceptions { listRunningApps(runningApps) } // obf: C0279 mode 2
                response.sendSuccess(runningApps)
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // /apps, /running-apps
    // ------------------------------------------------------------------------------------------

    /** Every installed package (incl. uninstalled-for-user ones, flag 8192), unsorted. */
    // obf: C0279.mo100 case 0 (captures pkgManager, $apps)
    private fun listInstalledApps(pkgManager: PackageManager?, apps: ArrayList<App>) {
        val installedPackages = pkgManager!!.getInstalledPackages(8192)
        for (packageInfo in installedPackages) {
            val app = packageInfo?.toApp(pkgManager) ?: continue
            apps.add(app)
        }
    }

    /** Packages of all running processes, de-duplicated, sorted by name. */
    // obf: C0279.mo100 default case (captures this$0, $runningApps)
    private fun listRunningApps(runningApps: ArrayList<App>) {
        val pkgManager = context.packageManager
        val activityManager = context.getSystemService("activity") as ActivityManager
        val processes = activityManager.runningAppProcesses
        val seen = ArrayList<String>()
        if (processes != null) {
            for (process in processes) {
                val pkgList = process?.pkgList ?: continue
                for (pkg in pkgList) {
                    if (!seen.contains(pkg)) {
                        seen.add(pkg!!)
                        // obf: C0345.mo100
                        MiscUtils.ignoreExceptions {
                            val packageInfo = pkgManager!!.getPackageInfo(pkg, 8192)
                            val app = packageInfo?.toApp(pkgManager)
                            if (app != null) runningApps.add(app)
                        }
                    }
                }
            }
        }
        runningApps.sortBy { it.name } // obf: C0136 mode 3 (compareValues on name)
    }

    // ------------------------------------------------------------------------------------------
    // /app?package_name=..&type=..
    // ------------------------------------------------------------------------------------------

    // obf: m258
    private fun handleApp(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val packageName = request.query.getString("package_name")
        val type = request.query.getString("type")
        if (packageName == null || packageName.isBlank()) {
            response.sendMissingQueries("package_name")
            return
        }
        // Fixed (rebuild): the name goes unquoted into pm/am commands; reject anything that is not a
        // package name (the original passed e.g. "a; reboot" straight to the shell).
        if (!isValidPackageName(packageName)) {
            response.sendError("Invalid package name.")
            return
        }
        if (type == null || type.isBlank()) {
            response.sendMissingQueries("type")
            return
        }
        when (type) {
            "enable" -> runCommand(response, "App enabled.", "Failed to enable.", "pm enable $packageName")
            "launch" -> launchApp(response, packageName)
            "info-settings" -> runCommand(
                response,
                "Launched app info settings.",
                "Failed to launch app info settings.",
                "am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$packageName",
            )
            // BUG(original): no `--user 0`, so for system apps `pm uninstall` fails (only updates
            // could be removed with -k/--user); combined with the exit-code check in runCommand
            // the client is told "App uninstalled." anyway.
            "uninstall" -> runCommand(response, "App uninstalled.", "Failed to uninstall.", "pm uninstall $packageName")
            "info" -> sendAppInfo(response, packageName)
            "stop" -> runCommand(response, "Force stopped.", "Failed to force stop.", "am force-stop $packageName")
            "clear" -> runCommand(response, "App data cleared.", "Failed to clear data.", "pm clear $packageName")
            "store" -> runCommand(
                response,
                "Launched Play Store.",
                "Failed to launch Play Store.",
                "am start -a android.intent.action.VIEW -p com.android.vending -d market://details?id=$packageName",
            )
            "disable" -> runCommand(response, "App disabled.", "Failed to disable.", "pm disable-user $packageName")
            else -> response.sendError("Unexpected type.")
        }
    }

    /** `type=launch` */
    // obf: m258 case "launch"
    private fun launchApp(response: AsyncHttpServerResponse, packageName: String) {
        try {
            val pkgManager = context.packageManager!!
            val launchIntent = pkgManager.getLaunchIntentForPackage(packageName)
                ?: pkgManager.getLeanbackLaunchIntentForPackage(packageName)
            val component = launchIntent?.component
            if (component == null) {
                response.sendError("No launch intent found.")
                return
            }
            val className = component.className
            // RECON-UNSURE: why "$" (nested activity classes) switches to action+package; kept as is.
            val command = if (className.contains("$")) {
                "am start -a " + launchIntent!!.action + " -p " + launchIntent.`package`
            } else {
                "am start -n " + component.packageName + '/' + component.className
            }
            // BUG(original): exitCode >= 0 is true for every finished command (am returns 0 even on
            // many errors, and 1 is >= 0 as well); only Shell's -1 (exception) counts as failure.
            if (Shell.run(command, true).exitCode >= 0) {
                response.sendSuccess("App launched.")
            } else {
                response.sendError("Failed to launch.")
            }
        } catch (e: Exception) {
            response.sendError("No launch intent found.")
        }
    }

    /** `type=info` */
    // obf: m258 case "info"
    private fun sendAppInfo(response: AsyncHttpServerResponse, packageName: String) {
        var permissions: List<String> = emptyList() // obf: C0166.f339 (EmptyList)
        // BUG(original): "%s" is never formatted, the client gets the literal placeholder.
        val failedMessage = "Failed to get app info for %s."
        try {
            val pkgManager = context.packageManager!!
            val packageInfo = pkgManager.getPackageInfo(packageName, 8192)
            val app = packageInfo?.toApp(pkgManager)
            if (packageInfo == null || app == null) {
                response.sendError(failedMessage)
                return
            }
            val applicationInfo = packageInfo.applicationInfo
            if (applicationInfo == null) {
                response.sendError(failedMessage)
                return
            }
            val targetSdk = applicationInfo.targetSdkVersion
            val sdkInt = Build.VERSION.SDK_INT
            val minSdk = if (sdkInt >= 24) applicationInfo.minSdkVersion else -1
            val compileSdk = if (sdkInt >= 31) applicationInfo.compileSdkVersion else targetSdk
            val filePath = applicationInfo.sourceDir ?: String()
            val size = File(filePath).length()
            val dataPath = applicationInfo.dataDir ?: String()
            val nativeLibraryPath = applicationInfo.nativeLibraryDir ?: String()
            val firstInstallTime = packageInfo.firstInstallTime
            val lastUpdateTime = packageInfo.lastUpdateTime
            val uid = applicationInfo.uid
            val launcherActivity = pkgManager.resolveMainActivity("android.intent.category.LAUNCHER", packageName)
                ?.activityInfo?.name ?: String()
            try {
                val requestedPermissions = pkgManager.getPackageInfo(packageName, 4096).requestedPermissions
                if (requestedPermissions != null) {
                    val list = ArrayList<String>(requestedPermissions.size)
                    for (permission in requestedPermissions) {
                        list.add(permission ?: String())
                    }
                    permissions = list.sorted() // obf: AbstractC0020.m46
                }
            } catch (e: Exception) {
            }
            response.sendSuccess(
                AppInfo(
                    app.name,
                    app.isEnabled,
                    app.isLauncherAvailable,
                    app.isTvSupported,
                    app.isSystemApp,
                    app.packageName,
                    app.versionCode,
                    app.versionName,
                    compileSdk,
                    minSdk,
                    targetSdk,
                    filePath,
                    size,
                    nativeLibraryPath,
                    dataPath,
                    firstInstallTime,
                    lastUpdateTime,
                    uid,
                    launcherActivity,
                    permissions,
                )
            )
        } catch (e: Exception) {
            response.sendError(failedMessage)
        }
    }

    /** Runs [command] (output not read) and answers with [successMessage] / [failureMessage]. */
    // obf: m259
    fun runCommand(response: AsyncHttpServerResponse, successMessage: String, failureMessage: String, command: String) {
        // BUG(original): pm returns 1 on failure, which is >= 0 → always reports success
        if (Shell.run(command, false).exitCode >= 0) {
            response.sendSuccess(successMessage)
        } else {
            response.sendError(failureMessage)
        }
    }

    /** Java package-name syntax (letters, digits, '_', dot-separated); "android" is valid too. */
    private fun isValidPackageName(name: String): Boolean = PACKAGE_NAME_REGEX.matches(name)

    // ------------------------------------------------------------------------------------------
    // /app-icon?package_name=..[&icon_only]
    // ------------------------------------------------------------------------------------------

    /**
     * Sends the app's banner (TV apps, unless `icon_only` is present) or icon as an image file.
     * Icons are cached as `<package>.<ext>` in [CACHE_DIR]. Falls back to extracting the icon
     * from the APK when PackageManager yields nothing usable.
     */
    // obf: mo143 case 0
    private fun sendAppIcon(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val packageName = request.query.getString("package_name")
        val iconOnly = request.query.containsKey("icon_only")
        if (packageName == null || packageName.isBlank()) {
            response.sendMissingQueries("package_name")
            return
        }
        // Fixed (rebuild): the name is also used as icon cache file name (no "../" escapes).
        if (!isValidPackageName(packageName)) {
            response.sendError("Invalid package name.")
            return
        }
        val cacheDir = File(CACHE_DIR)
        if (!cacheDir.isDirectory) {
            cacheDir.mkdir()
        }
        // BUG(original): prefix match, so "com.foo" also hits a cached "com.foobar.png"; and the
        // cache ignores icon_only (a cached banner is returned for an icon request and vice versa)
        // and is never invalidated after app updates.
        var cached: File? = null
        if (cacheDir.exists()) {
            val files = cacheDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.name.startsWith(packageName)) {
                        cached = file
                        break
                    }
                }
            }
        }
        if (cached != null) {
            response.sendImageFile(cached)
            return
        }

        // Only NameNotFoundException is caught here; anything else propagates (as in the dex).
        val packageContext = try {
            context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        } catch (e: PackageManager.NameNotFoundException) {
            context
        }
        val drawable: Drawable? = try {
            val pkgManager = packageContext.packageManager
            val applicationInfo = pkgManager.getApplicationInfo(packageName, 0)
            if (!iconOnly && applicationInfo.banner > 0) {
                applicationInfo.loadBanner(pkgManager)
            } else {
                applicationInfo.loadIcon(pkgManager)
            }
        } catch (e: Exception) {
            null
        }
        val bitmap: Bitmap? = if (drawable == null) {
            null
        } else if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            // BUG(original): outside any try; drawables without intrinsic size (e.g. ColorDrawable,
            // -1) make createBitmap throw out of the handler.
            val created = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(created)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            created
        }
        if (bitmap != null) {
            val iconFile = File(cacheDir, "$packageName.png")
            if (!bitmap.isRecycled) {
                try {
                    FileOutputStream(iconFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } catch (e: Exception) {
                }
            }
            // BUG(original): sent even when writing failed / the bitmap was recycled (404 then).
            response.sendImageFile(iconFile)
            return
        }

        val apkPath: String? = try {
            val pkgContext = try {
                context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
            } catch (e: PackageManager.NameNotFoundException) {
                context
            }
            pkgContext.packageManager.getApplicationInfo(packageName, 0).publicSourceDir
        } catch (e: Exception) {
            null
        }
        val iconFile = if (apkPath != null) getIconFromApk(context, apkPath, cacheDir, packageName) else null
        if (iconFile != null) {
            response.sendImageFile(iconFile)
        } else {
            response.sendError("Failed to generate icon for $packageName.")
        }
    }

    /** Resources of the APK at [apkPath] (falls back to the server's own). */
    private fun apkResources(context: Context, apkPath: String): android.content.res.Resources {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getPackageArchiveInfo(apkPath, 0)!!.applicationInfo!!
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath
            pm.getResourcesForApplication(appInfo)
        } catch (e: Exception) {
            context.resources
        }
    }

    /**
     * Extracts the launcher icon from the APK at [apkPath] and stores it as `[iconName].<ext>` in
     * [parent]. Order: last raster icon path (copied verbatim); else the last XML icon: an
     * adaptive icon is composed from its foreground/background, any other drawable XML is
     * inflated directly. Returns the written file or null.
     */
    // obf: inlined into mo143 case 0 (parameter names from the dex: context, apkPath, parent, iconName)
    private fun getIconFromApk(context: Context, apkPath: String, parent: File, iconName: String): File? {
        val apkFile = try {
            ApkFile(apkPath)
        } catch (e: Exception) {
            return null
        }
        // Fixed (rebuild): inflate XML icons with the APK's own resources so @color/@drawable
        // references resolve (the original used the server's resources -> such icons were null).
        val resources = apkResources(context, apkPath)
        try {
            val iconPaths = apkFile.getIconPaths()

            // 1) raster icon: copy the bytes as they are
            val rasterIcon = iconPaths.lastOrNull { !it.path.endsWith(".xml") }
            var savedFile: File? = null
            if (rasterIcon != null) {
                val path = rasterIcon.path
                val data = apkFile.getFileData(path)
                if (data != null) {
                    val extension = path.substringAfterLast('.', "").takeIf { it.isNotEmpty() } ?: "png"
                    val file = File(parent, "$iconName.$extension")
                    savedFile = try {
                        FileOutputStream(file).use { it.write(data) }
                        file
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            if (savedFile != null) {
                return savedFile
            }

            // 2) XML icon
            val xmlIcon = iconPaths.lastOrNull { it.path.endsWith(".xml") } ?: return null
            val xmlData = apkFile.getFileData(xmlIcon.path) ?: return null
            val adaptiveIconParser = AdaptiveIconParser()
            apkFile.parseBinaryXml(xmlData, adaptiveIconParser)
            val foreground = adaptiveIconParser.foreground
            if (foreground == null) {
                // not an adaptive icon: inflate the drawable XML itself
                val drawable = IconUtils.getDrawableFromXml(resources, xmlData) ?: return null
                return IconUtils.saveBitmap(File(parent, "$iconName.png"), IconUtils.drawableToBitmap(drawable))
            }
            val foregroundData = apkFile.getFileData(foreground) ?: return null
            val foregroundIsXml = adaptiveIconParser.foreground!!.endsWith(".xml", ignoreCase = true)
            if (Build.VERSION.SDK_INT >= 26) {
                val foregroundDrawable: Drawable = (
                    if (foregroundIsXml) {
                        IconUtils.getDrawableFromXml(resources, foregroundData)
                    } else {
                        val decoded = try {
                            BitmapFactory.decodeByteArray(foregroundData, 0, foregroundData.size)
                        } catch (e: Exception) {
                            null
                        }
                        if (decoded != null) BitmapDrawable(context.resources, decoded) else null
                    }
                    ) ?: return null

                adaptiveIconParser.background // obf: m261 evaluated twice in the dex
                val backgroundDrawable: Drawable?
                if (adaptiveIconParser.background.endsWith(".xml", ignoreCase = true)) {
                    val backgroundData = apkFile.getFileData(adaptiveIconParser.background) ?: return null
                    backgroundDrawable = IconUtils.getDrawableFromXml(resources, backgroundData)
                } else if (adaptiveIconParser.background.startsWith("#")) {
                    val color = Color.parseColor(adaptiveIconParser.background)
                    backgroundDrawable = BitmapDrawable(
                        context.resources,
                        IconUtils.solidBitmap(foregroundDrawable.intrinsicWidth, foregroundDrawable.intrinsicHeight, color),
                    )
                } else {
                    val backgroundData = apkFile.getFileData(adaptiveIconParser.background)
                    val backgroundBitmap: Bitmap = (
                        if (backgroundData == null) {
                            // no background found: white
                            IconUtils.solidBitmap(foregroundDrawable.intrinsicWidth, foregroundDrawable.intrinsicHeight, -1)
                        } else {
                            try {
                                BitmapFactory.decodeByteArray(backgroundData, 0, backgroundData.size)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        ) ?: return null
                    backgroundDrawable = BitmapDrawable(context.resources, backgroundBitmap)
                }
                // obf: AbstractC0309.m316 (no-op API outline marker) + m317
                val adaptiveIcon = AdaptiveIconDrawable(backgroundDrawable, foregroundDrawable)
                return IconUtils.saveBitmap(File(parent, "$iconName.png"), IconUtils.drawableToBitmap(adaptiveIcon))
            } else {
                // pre-O: no AdaptiveIconDrawable, use the foreground alone
                val bitmap: Bitmap = (
                    if (foregroundIsXml) {
                        IconUtils.getDrawableFromXml(resources, foregroundData)?.let { IconUtils.drawableToBitmap(it) }
                    } else {
                        try {
                            BitmapFactory.decodeByteArray(foregroundData, 0, foregroundData.size)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    ) ?: return null
                return IconUtils.saveBitmap(File(parent, "$iconName.png"), bitmap)
            }
        } catch (e: Exception) {
            return null
        } finally {
            apkFile.close()
        }
    }

    companion object {
        private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$")

        /** Icon cache directory (shared with the screenshots). */
        // obf: AbstractC0077.f167
        val CACHE_DIR: String = DeviceHandler.CACHE_DIR
    }
}

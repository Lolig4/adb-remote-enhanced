package com.jcapps.adbremote.ui.install

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** Rebuild addition: reads label / package / version / icon of APK and bundle files on the phone. */
object ApkInfoReader {

    val EXTENSIONS = setOf("apk", "apks", "xapk", "apkm")

    data class Info(
        val label: String,
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long,
        val icon: Drawable?,
    )

    fun isBundle(file: File) = file.extension.lowercase() in setOf("apks", "xapk", "apkm")

    fun read(file: File, pm: PackageManager, tempDir: File): Info {
        val fallback = Info(file.name, null, null, -1, null)
        return try {
            if (isBundle(file)) {
                val base = extractBaseApk(file, tempDir) ?: return fallback
                try {
                    readApk(base, pm) ?: fallback
                } finally {
                    base.delete()
                }
            } else {
                readApk(file, pm) ?: fallback
            }
        } catch (e: Exception) {
            fallback
        }
    }

    private fun readApk(apk: File, pm: PackageManager): Info? {
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(apk.path, 0) ?: return null
        val app = info.applicationInfo ?: return null
        app.sourceDir = apk.path
        app.publicSourceDir = apk.path
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        return Info(
            label = app.loadLabel(pm).toString(),
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCode,
            icon = runCatching { app.loadIcon(pm) }.getOrNull(),
        )
    }

    /** Extracts the base APK of a bundle into [tempDir]; null if none is found. */
    private fun extractBaseApk(bundle: File, tempDir: File): File? {
        ZipFile(bundle).use { zip ->
            val apks = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".apk", true) }.toList()
            if (apks.isEmpty()) return null
            val manifestPackage = zip.getEntry("manifest.json")?.let { entry ->
                runCatching { JSONObject(zip.getInputStream(entry).bufferedReader().readText()).optString("package_name") }
                    .getOrNull()
            }
            val base = apks.firstOrNull { it.name.substringAfterLast('/') == "base.apk" }
                ?: apks.firstOrNull { !manifestPackage.isNullOrEmpty() && it.name.substringAfterLast('/') == "$manifestPackage.apk" }
                ?: apks.firstOrNull { val n = it.name.lowercase(); "split" !in n && "config." !in n }
                ?: apks.first()
            tempDir.mkdirs()
            val out = File.createTempFile("base", ".apk", tempDir)
            zip.getInputStream(base).use { input -> out.outputStream().use { input.copyTo(it) } }
            return out
        }
    }
}

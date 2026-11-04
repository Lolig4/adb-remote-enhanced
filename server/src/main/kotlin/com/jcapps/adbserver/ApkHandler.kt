// Rebuild addition: sideload feature
package com.jcapps.adbserver

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.koushikdutta.async.AsyncServer
import com.koushikdutta.async.ByteBufferList
import com.koushikdutta.async.callback.CompletedCallback
import com.koushikdutta.async.callback.DataCallback
import com.koushikdutta.async.http.body.AsyncHttpRequestBody
import com.koushikdutta.async.http.body.JSONObjectBody
import com.koushikdutta.async.http.body.MultipartFormDataBody
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Sideload endpoints:
 *
 * | route               | mode               |
 * |---------------------|--------------------|
 * | GET  /apk/files     | [Mode.FILES]       |
 * | GET  /apk/icon      | [Mode.ICON]        |
 * | GET  /apk/installed | [Mode.INSTALLED]   |
 * | POST /apk/upload    | [Mode.UPLOAD]      |
 * | POST /apk/install   | [Mode.INSTALL]     |
 *
 * Slow work (parsing archives, extracting bundles, `pm install`) runs on [WORKER]; the response is
 * then sent from the AsyncServer thread again.
 */
class ApkHandler(param: Param, private val context: Context, private val mode: Mode) : BaseHandler(param) {

    enum class Mode { FILES, ICON, INSTALLED, UPLOAD, INSTALL }

    override fun handle(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        when (mode) {
            Mode.FILES -> listFiles(response)
            Mode.ICON -> sendApkIcon(request, response)
            Mode.INSTALLED -> sendInstalled(request, response)
            Mode.UPLOAD -> upload(request, response)
            Mode.INSTALL -> install(request, response)
        }
    }

    // ------------------------------------------------------------------------------------------
    // GET /apk/files
    // ------------------------------------------------------------------------------------------

    private fun listFiles(response: AsyncHttpServerResponse) {
        WORKER.execute {
            val result = ArrayList<ApkFileInfo>()
            try {
                val downloadDir = DOWNLOAD_DIRS.map { File(it) }.firstOrNull { it.isDirectory }
                if (downloadDir != null) {
                    val files = ArrayList<File>()
                    collectInstallable(downloadDir, 1, files)
                    for (file in files) {
                        result.add(describe(file))
                    }
                }
                result.sortByDescending { it.modified }
            } catch (e: Exception) {
                println(e.stackTraceToString())
            }
            onServerThread { response.sendSuccess(result) }
        }
    }

    /** Files with an installable extension, [depth] 1 = directly in the Download dir, max [MAX_DEPTH]. */
    private fun collectInstallable(dir: File, depth: Int, out: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                if (depth < MAX_DEPTH) collectInstallable(child, depth + 1, out)
            } else if (isInstallable(child.name)) {
                out.add(child)
            }
        }
    }

    private fun describe(file: File): ApkFileInfo {
        val bundle = isBundle(file.name)
        val parsed = try {
            withBaseApk(file) { apkPath -> parseArchive(apkPath) }
        } catch (e: Exception) {
            null
        }
        val installedVersionCode = parsed?.packageName?.let { installedPackage(it) }?.let { versionCodeOf(it) } ?: -1L
        return ApkFileInfo(
            path = file.absolutePath,
            name = file.name,
            size = file.length(),
            modified = file.lastModified(),
            isBundle = bundle,
            label = parsed?.label ?: file.name,
            packageName = parsed?.packageName,
            versionName = parsed?.versionName,
            versionCode = parsed?.versionCode ?: -1L,
            installedVersionCode = installedVersionCode,
        )
    }

    /** Label/package/version of an APK file via `getPackageArchiveInfo`; null if unparsable. */
    private fun parseArchive(apkPath: String): ArchiveInfo? {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(apkPath, 0) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = apkPath
        appInfo.publicSourceDir = apkPath
        val packageName = info.packageName ?: appInfo.packageName ?: return null
        val label = try {
            appInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            null
        }
        return ArchiveInfo(
            label = label?.takeIf { it.isNotBlank() } ?: File(apkPath).name,
            packageName = packageName,
            versionName = info.versionName,
            versionCode = versionCodeOf(info),
        )
    }

    // ------------------------------------------------------------------------------------------
    // GET /apk/icon?path=..
    // ------------------------------------------------------------------------------------------

    private fun sendApkIcon(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val path = request.query.getString("path")
        if (path == null || path.isBlank()) {
            response.sendMissingQueries("path")
            return
        }
        val file = resolveAllowed(path)
        if (file == null) {
            response.sendError("Invalid path.")
            return
        }
        if (!file.isFile) {
            response.sendError("File not found.")
            return
        }
        WORKER.execute {
            val png = try {
                withBaseApk(file) { apkPath -> loadIconPng(apkPath) }
            } catch (e: Exception) {
                null
            }
            onServerThread {
                if (png != null) {
                    response.send("image/png", png)
                } else {
                    response.sendError("No icon found.")
                }
            }
        }
    }

    /**
     * App icon of the APK at [apkPath] as PNG bytes: PackageManager first, then the resource-table
     * parser of [AppsHandler.getIconFromApk] (adaptive / XML icons the archive resources cannot load).
     */
    private fun loadIconPng(apkPath: String): ByteArray? {
        val pm = context.packageManager
        val appInfo: ApplicationInfo? = try {
            pm.getPackageArchiveInfo(apkPath, 0)?.applicationInfo
        } catch (e: Exception) {
            null
        }
        if (appInfo != null && appInfo.icon != 0) {
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath
            val png = try {
                encodePng(drawableToBitmap(appInfo.loadIcon(pm)))
            } catch (e: Exception) {
                null
            }
            if (png != null) return png
        }
        val tempDir = File(stagingDir(), "icon_" + UUID.randomUUID())
        try {
            if (!tempDir.mkdirs()) return null
            val iconFile = AppsHandler(param, context, AppsHandler.Mode.ICON)
                .getIconFromApk(context, apkPath, tempDir, "icon") ?: return null
            val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath) ?: return null
            return encodePng(bitmap)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** Like [IconUtils.drawableToBitmap], but drawables without an intrinsic size get 192 px. */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) return IconUtils.drawableToBitmap(drawable)
        val bitmap = Bitmap.createBitmap(ICON_FALLBACK_SIZE, ICON_FALLBACK_SIZE, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, ICON_FALLBACK_SIZE, ICON_FALLBACK_SIZE)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun encodePng(bitmap: Bitmap): ByteArray? {
        if (bitmap.isRecycled) return null
        val out = ByteArrayOutputStream()
        return if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) && out.size() > 0) out.toByteArray() else null
    }

    // ------------------------------------------------------------------------------------------
    // GET /apk/installed?package_name=..
    // ------------------------------------------------------------------------------------------

    private fun sendInstalled(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val packageName = request.query.getString("package_name")
        if (packageName == null || packageName.isBlank()) {
            response.sendMissingQueries("package_name")
            return
        }
        val info = installedPackage(packageName.trim())
        if (info == null) {
            response.sendError("Not installed.")
        } else {
            response.sendSuccess(InstalledVersion(versionCodeOf(info), info.versionName ?: ""))
        }
    }

    // ------------------------------------------------------------------------------------------
    // POST /apk/upload?session=..&name=..
    // ------------------------------------------------------------------------------------------

    /**
     * Streams the first file part of the multipart body to `STAGING_DIR/<session>/<name>` (same
     * pattern as ClipboardHandler SET_IMAGE). Further parts are left to AndroidAsync's defaults
     * (file parts are discarded).
     */
    private fun upload(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val rawSession = request.query.getString("session")
        val rawName = request.query.getString("name")
        val missing = listOfNotNull(
            "session".takeIf { rawSession.isNullOrBlank() },
            "name".takeIf { rawName.isNullOrBlank() },
        )
        if (missing.isNotEmpty()) {
            response.sendMissingQueries(*missing.toTypedArray())
            return
        }
        val session = sanitizeName(rawSession)
        val name = sanitizeName(rawName)
        if (session == null || name == null) {
            response.sendError("Invalid session or name.")
            return
        }
        val body = request.getBody<AsyncHttpRequestBody<*>>()
        if (body !is MultipartFormDataBody) {
            response.sendError("Body must be multipart form data.")
            return
        }
        val target = File(File(STAGING_DIR, session), name)
        try {
            var errMessage: String? = null
            var output: FileOutputStream? = null
            var fileStarted = false

            request.setEndCallback(CompletedCallback { exc ->
                MiscUtils.ignoreExceptions { output?.close() }
                output = null
                if (exc != null || errMessage != null) {
                    MiscUtils.ignoreExceptions { target.delete() }
                    response.sendError(errMessage ?: exc?.toString() ?: "Upload failed.")
                    return@CompletedCallback
                }
                if (!fileStarted) {
                    response.sendError("Missing file part.")
                    return@CompletedCallback
                }
                response.sendSuccess(target.absolutePath)
            })

            body.setMultipartCallback(MultipartFormDataBody.MultipartCallback { part ->
                if (fileStarted || errMessage != null) return@MultipartCallback
                val contentType = part.contentType
                val looksLikeFile = part.isFile || (contentType != null && !contentType.startsWith("text/"))
                if (!looksLikeFile) return@MultipartCallback // plain form field, ignored
                fileStarted = true
                val dir = target.parentFile!!
                if (!dir.isDirectory && !dir.mkdirs()) {
                    errMessage = "Failed to create directory: " + dir.absolutePath + '.'
                    return@MultipartCallback
                }
                val out = try {
                    FileOutputStream(target)
                } catch (e: Exception) {
                    errMessage = e.toString()
                    return@MultipartCallback
                }
                output = out
                body.setDataCallback(DataCallback { _, bb: ByteBufferList ->
                    try {
                        if (output != null) {
                            while (bb.size() > 0) {
                                val buffer = bb.remove()
                                out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
                                ByteBufferList.reclaim(buffer)
                            }
                        }
                    } catch (e: Exception) {
                        MiscUtils.ignoreExceptions {
                            out.close()
                            target.delete()
                        }
                        output = null
                        errMessage = e.toString()
                    } finally {
                        bb.recycle()
                    }
                })
            })
        } catch (e: Exception) {
            println(e.stackTraceToString())
            MiscUtils.ignoreExceptions { target.delete() }
            response.sendError(e.toString())
        }
    }

    // ------------------------------------------------------------------------------------------
    // POST /apk/install
    // ------------------------------------------------------------------------------------------

    private fun install(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val body = request.getBody<AsyncHttpRequestBody<*>>()
        if (body !is JSONObjectBody) {
            response.sendError("Body must be JSON.")
            return
        }
        val installRequest: InstallRequest? = try {
            Gson().fromJson(body.get()?.toString(), InstallRequest::class.java)
        } catch (e: Exception) {
            null
        }
        if (installRequest == null) {
            response.sendError("Install request needs params.")
            return
        }
        WORKER.execute {
            val error: String? = try {
                synchronized(INSTALL_LOCK) { performInstall(installRequest) }
            } catch (e: Exception) {
                println(e.stackTraceToString())
                e.toString()
            }
            onServerThread {
                if (error == null) response.sendSuccess("App installed.") else response.sendError(error)
            }
        }
    }

    /** Runs the whole install; null on success, else the error message. */
    private fun performInstall(installRequest: InstallRequest): String? {
        var extractDir: File? = null
        try {
            val paths = installRequest.paths?.filterNotNull()?.filter { it.isNotBlank() }.orEmpty()
            if (paths.isEmpty()) return "Missing paths."
            val files = ArrayList<File>()
            for (path in paths) {
                val file = resolveAllowed(path.trim()) ?: return "Invalid path."
                if (!file.isFile) return "File not found: " + file.name + '.'
                files.add(file)
            }
            val apks: List<File>
            if (files.any { isBundle(it.name) }) {
                if (files.size != 1) return "A bundle must be installed on its own."
                val dir = File(stagingDir(), "extract_" + UUID.randomUUID().toString().replace("-", ""))
                extractDir = dir
                if (!dir.mkdirs()) return "Failed to create directory: " + dir.absolutePath + '.'
                apks = when (val extracted = extractBundle(files[0], dir)) {
                    is Extracted.Apks -> extracted.files
                    is Extracted.Error -> return extracted.message
                }
            } else {
                if (files.any { !it.name.lowercase(Locale.ROOT).endsWith(".apk") }) return "Unsupported file type."
                // Android 11+: system_server may not read /sdcard (SELinux, fuse) -> stage the APKs
                // under /data/local/tmp first; uploaded files already live there.
                if (files.all { it.absolutePath.startsWith("$STAGING_DIR/") }) {
                    apks = files
                } else {
                    val dir = File(stagingDir(), "copy_" + UUID.randomUUID().toString().replace("-", ""))
                    extractDir = dir
                    if (!dir.mkdirs()) return "Failed to create directory: " + dir.absolutePath + '.'
                    apks = files.mapIndexed { index, file ->
                        val copy = File(dir, "${index}_" + (sanitizeName(file.name) ?: "app.apk"))
                        try {
                            file.inputStream().use { input -> FileOutputStream(copy).use { input.copyTo(it, 64 * 1024) } }
                        } catch (e: IOException) {
                            return "Failed to copy " + file.name + ": " + (e.message ?: e.toString())
                        }
                        copy
                    }
                }
            }
            return installApks(apks, installRequest.allowDowngrade == true)
        } finally {
            extractDir?.let { dir -> MiscUtils.ignoreExceptions { dir.deleteRecursively() } }
            sanitizeName(installRequest.cleanupSession)?.let { session ->
                MiscUtils.ignoreExceptions { File(STAGING_DIR, session).deleteRecursively() }
            }
        }
    }

    private sealed class Extracted {
        class Apks(val files: List<File>) : Extracted()
        class Error(val message: String) : Extracted()
    }

    /**
     * Extracts the APKs of a bundle into [dir]. Root-level `*.apk` entries are used; bundles without
     * any (bundletool `.apks`) fall back to all apk entries except `standalones/`. For `.xapk`, the
     * `Android/obb/` entries are copied to `/sdcard/Android/obb/`.
     */
    private fun extractBundle(bundle: File, dir: File): Extracted {
        val zip = try {
            ZipFile(bundle)
        } catch (e: Exception) {
            return Extracted.Error(UNSUPPORTED_BUNDLE)
        }
        zip.use {
            val entries = try {
                zip.entries().asSequence().filter { !it.isDirectory }.toList()
            } catch (e: Exception) {
                return Extracted.Error(UNSUPPORTED_BUNDLE)
            }
            val apkEntries = entries.filter { it.name.lowercase(Locale.ROOT).endsWith(".apk") && !hasUnsafeSegment(it.name) }
            val rootApks = apkEntries.filter { !it.name.contains('/') }
            val chosen = rootApks.ifEmpty { apkEntries.filter { !it.name.startsWith("standalones/") } }
            if (chosen.isEmpty()) return Extracted.Error(UNSUPPORTED_BUNDLE)

            val apks = ArrayList<File>()
            chosen.forEachIndexed { index, entry ->
                val fileName = sanitizeName(entry.name.substringAfterLast('/')) ?: "split.apk"
                val out = File(dir, "${index}_$fileName")
                copyEntry(zip, entry, out)?.let { return Extracted.Error(it) }
                apks.add(out)
            }

            if (bundle.name.lowercase(Locale.ROOT).endsWith(".xapk")) {
                val obbEntries = entries.filter { it.name.startsWith(OBB_PREFIX) && !hasUnsafeSegment(it.name) }
                for (entry in obbEntries) {
                    val out = File(EXTERNAL_STORAGE, entry.name)
                    val parent = out.parentFile!!
                    if (!parent.isDirectory && !parent.mkdirs()) {
                        return Extracted.Error("Failed to copy OBB files: cannot create " + parent.absolutePath + '.')
                    }
                    copyEntry(zip, entry, out)?.let { return Extracted.Error("Failed to copy OBB files: $it") }
                }
            }
            return Extracted.Apks(apks)
        }
    }

    /** Copies one zip entry; null on success, else an error message (zip errors = unsupported bundle). */
    private fun copyEntry(zip: ZipFile, entry: ZipEntry, out: File): String? {
        val input = try {
            zip.getInputStream(entry)
        } catch (e: Exception) {
            return UNSUPPORTED_BUNDLE
        }
        return try {
            input.use { inStream -> FileOutputStream(out).use { inStream.copyTo(it, 64 * 1024) } }
            null
        } catch (e: ZipException) {
            MiscUtils.ignoreExceptions { out.delete() }
            UNSUPPORTED_BUNDLE
        } catch (e: IOException) {
            MiscUtils.ignoreExceptions { out.delete() }
            "Failed to extract bundle: " + (e.message ?: e.toString())
        }
    }

    /**
     * `pm install` for one APK, a `pm install-create/-write/-commit` session for a split set.
     * Null on success, else pm's message.
     */
    private fun installApks(apks: List<File>, allowDowngrade: Boolean): String? {
        val flags = if (allowDowngrade) "-r -d" else "-r"
        if (apks.size == 1) {
            val result = Shell.run("pm install $flags " + shellQuote(apks[0].absolutePath), true)
            return if (pmSucceeded(result)) null else pmError(result)
        }
        val totalSize = apks.sumOf { it.length() }
        val create = Shell.run("pm install-create $flags -S $totalSize", true)
        val session = SESSION_ID_REGEX.find(create.output)?.groupValues?.get(1)
            ?: SESSION_ID_REGEX.find(create.error)?.groupValues?.get(1)
        if (create.exitCode != 0 || session == null) return pmError(create)
        apks.forEachIndexed { index, apk ->
            val splitName = "${index}_" + (sanitizeName(apk.name) ?: "split.apk")
            val write = Shell.run(
                "pm install-write -S ${apk.length()} $session " + shellQuote(splitName) + ' ' + shellQuote(apk.absolutePath),
                true,
            )
            if (!pmSucceeded(write)) {
                Shell.run("pm install-abandon $session", true)
                return pmError(write)
            }
        }
        val commit = Shell.run("pm install-commit $session", true)
        if (pmSucceeded(commit)) return null
        Shell.run("pm install-abandon $session", true)
        return pmError(commit)
    }

    private fun pmSucceeded(result: CommandResult): Boolean =
        result.exitCode == 0 && (result.output.contains("Success") || result.error.contains("Success"))

    /** The "Failure [...]" / "Error: ..." line of pm's output if there is one, else all of it. */
    private fun pmError(result: CommandResult): String {
        val text = listOf(result.output, result.error).filter { it.isNotBlank() }.joinToString("\n").trim()
        val line = text.lines().map { it.trim() }.firstOrNull { it.startsWith("Failure") || it.startsWith("Error") }
        return line ?: text.ifEmpty { "Install failed." }
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /**
     * Runs [block] with the path of the APK to inspect: the file itself, or for a bundle its base APK
     * extracted to a temp file in the staging dir (deleted afterwards).
     */
    private fun <T> withBaseApk(file: File, block: (String) -> T?): T? {
        if (!isBundle(file.name)) return block(file.absolutePath)
        val temp = extractBaseApk(file) ?: return null
        try {
            return block(temp.absolutePath)
        } finally {
            MiscUtils.ignoreExceptions { temp.delete() }
        }
    }

    private fun extractBaseApk(bundle: File): File? {
        val temp = File(stagingDir(), "probe_" + UUID.randomUUID() + ".apk")
        return try {
            ZipFile(bundle).use { zip ->
                val entry = findBaseEntry(zip) ?: return null
                zip.getInputStream(entry).use { input -> FileOutputStream(temp).use { input.copyTo(it, 64 * 1024) } }
            }
            temp
        } catch (e: Exception) {
            MiscUtils.ignoreExceptions { temp.delete() }
            null
        }
    }

    /**
     * Base APK of a bundle: `base.apk`, else the APK named after xapk `manifest.json` "package_name",
     * else bundletool's `base-master.apk`, else the first APK without "split"/"config" in its name,
     * else the largest APK.
     */
    private fun findBaseEntry(zip: ZipFile): ZipEntry? {
        val apks = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.lowercase(Locale.ROOT).endsWith(".apk") }
            .toList()
        if (apks.isEmpty()) return null
        fun fileName(entry: ZipEntry) = entry.name.substringAfterLast('/').lowercase(Locale.ROOT)
        apks.firstOrNull { fileName(it) == "base.apk" }?.let { return it }
        val manifestPackage = zip.getEntry("manifest.json")?.let { entry ->
            try {
                val text = zip.getInputStream(entry).use { it.reader(Charsets.UTF_8).readText() }
                JsonParser.parseString(text).asJsonObject.get("package_name")?.asString
            } catch (e: Exception) {
                null
            }
        }
        if (manifestPackage != null) {
            val wanted = "$manifestPackage.apk".lowercase(Locale.ROOT)
            apks.firstOrNull { fileName(it) == wanted }?.let { return it }
        }
        apks.firstOrNull { fileName(it) == "base-master.apk" }?.let { return it }
        apks.firstOrNull { !fileName(it).contains("split") && !fileName(it).contains("config") }?.let { return it }
        return apks.maxByOrNull { it.size }
    }

    /** Installed package for user 0 (the server's user), or null. */
    private fun installedPackage(packageName: String): PackageInfo? = try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        val flags = info.applicationInfo?.flags
        if (flags != null && flags and ApplicationInfo.FLAG_INSTALLED == 0) null else info
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    /** Sends from the AsyncServer thread (the work ran on [WORKER]). */
    private fun onServerThread(block: () -> Unit) {
        AsyncServer.getDefault().post { block() }
    }

    /** Parsed `getPackageArchiveInfo` result. */
    private class ArchiveInfo(
        val label: String,
        val packageName: String,
        val versionName: String?,
        val versionCode: Long,
    )

    companion object {
        const val STAGING_DIR = "/data/local/tmp/adbremote_apks"
        private const val EXTERNAL_STORAGE = "/sdcard"
        private const val OBB_PREFIX = "Android/obb/"
        private const val UNSUPPORTED_BUNDLE = "Unsupported bundle."
        private const val MAX_DEPTH = 2
        private const val ICON_FALLBACK_SIZE = 192

        /** Download dir; the first existing one is listed (both are the same directory). */
        private val DOWNLOAD_DIRS = listOf("/sdcard/Download", "/storage/emulated/0/Download")

        /** Roots /apk/icon and /apk/install accept paths under. */
        private val ALLOWED_ROOTS = DOWNLOAD_DIRS + STAGING_DIR

        private val INSTALLABLE_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm")
        private val BUNDLE_EXTENSIONS = setOf("apks", "xapk", "apkm")
        private val SESSION_ID_REGEX = Regex("\\[(\\d+)]")

        private val INSTALL_LOCK = Any()

        private val WORKER: ExecutorService = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "apk-worker").apply { isDaemon = true }
        }

        private fun extensionOf(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

        fun isInstallable(name: String) = extensionOf(name) in INSTALLABLE_EXTENSIONS

        fun isBundle(name: String) = extensionOf(name) in BUNDLE_EXTENSIONS

        private fun stagingDir(): File = File(STAGING_DIR).apply { if (!isDirectory) mkdirs() }

        private fun hasUnsafeSegment(path: String) = path.split('/', '\\').any { it == ".." }

        /**
         * Session / file name restricted to `[A-Za-z0-9._-]`: other characters become '_', runs of
         * dots collapse to one, leading dots are dropped. Null if nothing usable remains.
         */
        fun sanitizeName(raw: String?): String? {
            if (raw == null) return null
            val cleaned = raw.trim()
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .replace(Regex("\\.{2,}"), ".")
                .trimStart('.')
            return cleaned.takeIf { it.isNotEmpty() && it != "." }
        }

        /** The file for [path] if it is absolute, has no `.`/`..` segments and lies under [ALLOWED_ROOTS]. */
        fun resolveAllowed(path: String?): File? {
            if (path == null || !path.startsWith("/")) return null
            if (path.split('/').any { it == ".." || it == "." }) return null
            if (ALLOWED_ROOTS.any { path.startsWith("$it/") }) return File(path)
            val canonical = try {
                File(path).canonicalPath
            } catch (e: Exception) {
                return null
            }
            val canonicalRoots = ALLOWED_ROOTS.mapNotNull {
                try {
                    File(it).canonicalPath
                } catch (e: Exception) {
                    null
                }
            }
            return if (canonicalRoots.any { canonical.startsWith("$it/") }) File(path) else null
        }

        private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    }
}

/** One entry of GET /apk/files. */
data class ApkFileInfo(
    @field:SerializedName("path") val path: String,
    @field:SerializedName("name") val name: String,
    @field:SerializedName("size") val size: Long,
    @field:SerializedName("modified") val modified: Long,
    @field:SerializedName("is_bundle") val isBundle: Boolean,
    @field:SerializedName("label") val label: String,
    @field:SerializedName("package_name") val packageName: String?,
    @field:SerializedName("version_name") val versionName: String?,
    @field:SerializedName("version_code") val versionCode: Long,
    @field:SerializedName("installed_version_code") val installedVersionCode: Long,
)

/** Response of GET /apk/installed. */
data class InstalledVersion(
    @field:SerializedName("version_code") val versionCode: Long,
    @field:SerializedName("version_name") val versionName: String,
)

/** Body of POST /apk/install. */
data class InstallRequest(
    @field:SerializedName("paths") val paths: List<String?>?,
    @field:SerializedName("allow_downgrade") val allowDowngrade: Boolean?,
    @field:SerializedName("cleanup_session") val cleanupSession: String?,
)

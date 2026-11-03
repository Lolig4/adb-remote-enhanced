package com.jcapps.adbserver

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import java.io.File

/**
 * `/supported-apps?mime_type=..&(uri=..|file_path={json})`: lists the activities that can VIEW the
 * given uri (or file) with the given mime type.
 */
// obf: C0329 (f725 = param, f726 = context)
class SupportedAppsHandler(param: Param, private val context: Context) : BaseHandler(param) {

    // obf: mo143
    override fun handle(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        // (the dex re-assigns null when the value is null: a no-op)
        val mimeType: String? = request.query.getString("mime_type")
        val uriString = request.query.getString("uri")
        // RECON-UNSURE: obf C0036 = Gson JsonSyntaxException (the exception declared by fromJson).
        val filePath: IntentParams.FilePath? = try {
            param.gson.fromJson(request.query.getString("file_path"), IntentParams.FilePath::class.java)
        } catch (e: JsonSyntaxException) {
            null
        }
        val uri: Uri? = if (uriString != null) {
            try {
                Uri.parse(uriString)
            } catch (e: Exception) {
                null
            }
        } else if (filePath != null) {
            Uri.fromFile(File(filePath.resolveParent(), filePath.child))
        } else {
            null
        }
        try {
            val pkgManager = context.packageManager
            val intent = Intent("android.intent.action.VIEW")
            intent.setDataAndType(uri, mimeType)
            val sdkInt = Build.VERSION.SDK_INT
            val queried = if (sdkInt >= 33) {
                // obf: AbstractC0057 (API-33 outline): ResolveInfoFlags.of(MATCH_ALL)
                pkgManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(131072L))
            } else if (sdkInt >= 23) {
                pkgManager.queryIntentActivities(intent, 131072) // MATCH_ALL
            } else {
                pkgManager.queryIntentActivities(intent, 65536) // MATCH_DEFAULT_ONLY
            }
            val resolveInfos = queried!! // obf: AbstractC0353.m366 (NPE)
            val supportedApps = ArrayList<SupportedApp>()
            for (resolveInfo in resolveInfos) {
                val supportedApp: SupportedApp? = if (resolveInfo == null) {
                    null
                } else {
                    val name = resolveInfo.loadLabel(pkgManager).toString()
                    val activityInfo = resolveInfo.activityInfo
                    val packageName: String? = activityInfo?.packageName
                    val activityName: String? = activityInfo?.name
                    val componentName = "$packageName/$activityName"
                    if (packageName == null) null else SupportedApp(name, packageName, componentName)
                }
                if (supportedApp != null) {
                    supportedApps.add(supportedApp)
                }
            }
            response.sendSuccess(supportedApps)
        } catch (e: Exception) {
            response.sendError(e.toString())
        }
    }

    /** `parent` if not blank, else external storage, else `fallback_parent`. */
    // obf: inlined into mo143 (receiver param "<this>")
    private fun IntentParams.FilePath.resolveParent(): File {
        val parentPath = parent?.takeIf { !it.isBlank() }
        if (parentPath != null) {
            return File(parentPath)
        }
        val externalStorage = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        return externalStorage ?: File(fallbackParent)
    }
}

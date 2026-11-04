package com.jcapps.adbremote.ui.install

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.api.adb.ServerInstaller
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Rebuild addition: client for the server's sideload endpoints (`/apk/...`, see
 * reco/docs or server ApkHandler). Blocking calls — use them off the main thread.
 */
class SideloadApi(ipAddress: String) {

    class ApiException(message: String) : IOException(message)

    private val baseUrl = "http://$ipAddress:${ServerInstaller.SERVER_CONSTANT}/"
    private val gson = Gson()
    // uploads and installs of big apps take minutes
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(15, TimeUnit.MINUTES)
        .build()

    private fun request(path: String) = Request.Builder()
        .url(baseUrl + path)
        .header("token", NativeConfig.apiInterceptorToken)

    /** Executes [request] and returns the envelope's `data`; throws [ApiException] for status false. */
    private fun call(request: Request): JsonObject {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("Empty response")
            val json = gson.fromJson(body, JsonObject::class.java) ?: throw IOException("Empty response")
            if (json.get("status")?.asBoolean != true) {
                throw ApiException(json.get("message")?.takeIf { !it.isJsonNull }?.asString ?: "Request failed.")
            }
            return json
        }
    }

    fun listTvFiles(): List<TvApkFile> {
        val json = call(request("apk/files").get().build())
        return gson.fromJson(json.get("data"), object : TypeToken<List<TvApkFile>>() {}.type) ?: emptyList()
    }

    /** Version code installed on the TV, null when the package is not installed. */
    fun installedVersion(packageName: String): Pair<Long, String?>? = try {
        val data = call(request("apk/installed?package_name=" + enc(packageName)).get().build()).getAsJsonObject("data")
        data.get("version_code").asLong to data.get("version_name")?.takeIf { !it.isJsonNull }?.asString
    } catch (e: ApiException) {
        null
    }

    /** Uploads [file] as [name] into the staging session [session]; returns its path on the TV. */
    fun upload(session: String, name: String, file: File, onProgress: (Long) -> Unit): String {
        val fileBody = object : RequestBody() {
            override fun contentType() = "application/vnd.android.package-archive".toMediaType()
            override fun contentLength() = file.length()
            override fun writeTo(sink: BufferedSink) {
                file.source().use { source ->
                    var total = 0L
                    while (true) {
                        val read = source.read(sink.buffer, 256 * 1024)
                        if (read == -1L) break
                        sink.flush()
                        total += read
                        onProgress(total)
                    }
                }
            }
        }
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", name, fileBody)
            .build()
        val json = call(request("apk/upload?session=" + enc(session) + "&name=" + enc(name)).post(multipart).build())
        return json.get("data").asString
    }

    /** Installs [paths] on the TV; returns the server message. Throws [ApiException] with pm's error. */
    fun install(paths: List<String>, allowDowngrade: Boolean, cleanupSession: String?): String {
        val body = JsonObject().apply {
            add("paths", gson.toJsonTree(paths))
            addProperty("allow_downgrade", allowDowngrade)
            if (cleanupSession != null) addProperty("cleanup_session", cleanupSession)
        }
        val json = call(
            request("apk/install").post(body.toString().toRequestBody("application/json".toMediaType())).build(),
        )
        return json.get("data")?.takeIf { !it.isJsonNull }?.asString ?: "App installed."
    }

    fun iconUrl(path: String) = baseUrl + "apk/icon?path=" + enc(path)

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}

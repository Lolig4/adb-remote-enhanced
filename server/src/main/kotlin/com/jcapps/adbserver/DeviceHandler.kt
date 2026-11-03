package com.jcapps.adbserver

import android.os.Build
import com.google.gson.annotations.SerializedName
import com.koushikdutta.async.http.body.AsyncHttpRequestBody
import com.koushikdutta.async.http.body.UrlEncodedFormBody
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import java.io.File

/**
 * GET /device-info, GET /ping, POST /run, GET /screenshot (one class, switched on [mode]).
 */
// obf: C0242 (f505 = mode int: 0 device-info, 1 ping, 2 run, 3 screenshot)
class DeviceHandler(param: Param, private val mode: Mode) : BaseHandler(param) {

    enum class Mode { DEVICE_INFO, PING, RUN, SCREENSHOT }

    // obf: mo143
    override fun handle(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        when (mode) {
            Mode.DEVICE_INFO -> deviceInfo(response)
            Mode.PING -> ping(response)
            Mode.RUN -> runCommand(request, response)
            Mode.SCREENSHOT -> screenshot(response)
        }
    }

    // obf: mo143 case 0
    private fun deviceInfo(response: AsyncHttpServerResponse) {
        val result = Shell.run(
            "cat /proc/cpuinfo; echo '====='; cat /proc/meminfo; echo '====='; dumpsys battery",
            true,
        )
        val empty = String()
        var cpuInfo = empty
        var memoryInfo = empty
        var batteryInfo = empty
        // Deliberately kept from the original: only exitCode < 0 (Shell exception) is a failure. A
        // non-zero exit (e.g. `dumpsys battery` on a box without battery) still returns the parts
        // that worked. (The original also checks
        // `output != null`, which is always true.)
        if (result.exitCode >= 0) {
            val parts = result.output.split("=====")
            cpuInfo = parts.getOrNull(0) ?: empty
            memoryInfo = parts.getOrNull(1) ?: empty
            batteryInfo = parts.getOrNull(2) ?: empty
        }
        response.sendSuccess(
            DeviceInfo(
                androidVersion = Build.VERSION.RELEASE,
                sdkVersion = Build.VERSION.SDK_INT,
                incremental = Build.VERSION.INCREMENTAL,
                device = Build.DEVICE,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                brand = Build.BRAND,
                board = Build.BOARD,
                supportedABIs = Build.SUPPORTED_ABIS.toList(), // obf: AbstractC0340.m337
                cpuInfo = cpuInfo,
                memoryInfo = memoryInfo,
                batteryInfo = batteryInfo,
            ),
        )
    }

    // obf: mo143 case 1
    private fun ping(response: AsyncHttpServerResponse) {
        response.sendSuccess(
            Ping(
                applicationId = "com.jcapps.adbserver",
                versionName = "1.1.0",
                author = "Kaung Khant Kyaw",
                description = "Copyright © 2024-present Kaung Khant Kyaw.\nADB Server app is a part of ADB Remote app. Contact the developer for more information.\n\nhttps://play.google.com/store/apps/details?id=com.jcapps.adbremote",
                github = "https://github.com/kaungkhantjc",
            ),
        )
    }

    // obf: mo143 case 2
    private fun runCommand(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val body: AsyncHttpRequestBody<*>? = request.getBody()
        if (body !is UrlEncodedFormBody) {
            response.sendError("Body must be Form URL encoded.")
            return
        }
        val command = body.get()?.getString("command")
        if (command == null || command.isBlank()) {
            response.sendError("command is invalid.")
        } else {
            response.sendSuccess(Shell.run(command, true))
        }
    }

    // obf: mo143 case 3
    private fun screenshot(response: AsyncHttpServerResponse) {
        val dir = File(CACHE_DIR)
        if (!dir.isDirectory) {
            dir.mkdir()
        }
        val file = File(dir, "screenshot.png")
        val result = Shell.run("screencap " + file.path, true)
        // BUG(original): see deviceInfo — the exit-code check only catches -1; success is really
        // decided by the file length. A stale screenshot.png from an earlier call is never deleted,
        // so a failing screencap still "succeeds" with the old image.
        if (result.exitCode >= 0 && file.length() > 0) {
            response.headers.set("Content-Type", "image/png")
            response.sendFile(file)
        } else {
            response.sendError(result.error) // original: error ?: String() (never null)
        }
    }

    // obf: C0289 ("DeviceInfo")
    data class DeviceInfo(
        @field:SerializedName("android_version") val androidVersion: String,
        @field:SerializedName("sdk_version") val sdkVersion: Int,
        @field:SerializedName("incremental") val incremental: String,
        @field:SerializedName("device") val device: String,
        @field:SerializedName("manufacturer") val manufacturer: String,
        @field:SerializedName("model") val model: String,
        @field:SerializedName("brand") val brand: String,
        @field:SerializedName("board") val board: String,
        @field:SerializedName("supported_ABIs") val supportedABIs: List<String>,
        @field:SerializedName("cpu_info") val cpuInfo: String,
        @field:SerializedName("memory_info") val memoryInfo: String,
        @field:SerializedName("battery_info") val batteryInfo: String,
    )

    // obf: C0241 ("Ping")
    data class Ping(
        @field:SerializedName("application_id") val applicationId: String,
        @field:SerializedName("version_code") val versionCode: Int = 2,
        @field:SerializedName("version_name") val versionName: String,
        @field:SerializedName("author") val author: String,
        @field:SerializedName("description") val description: String,
        @field:SerializedName("github") val github: String,
    )

    companion object {
        /**
         * Cache directory for screenshots and app icons.
         * Fixed (rebuild): the original used the RELATIVE path "sdcard/Android/.cache", which only
         * worked because the server happens to start with cwd "/"; now absolute.
         */
        // obf: AbstractC0077.f167
        @JvmField
        val CACHE_DIR: String = "/sdcard/Android/.cache"
    }
}

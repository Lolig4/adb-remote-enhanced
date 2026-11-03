package com.jcapps.adbremote.utils

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.android.material.imageview.ShapeableImageView
import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.R
import com.jcapps.adbremote.api.adb.ServerInstaller

/**
 * Loads app icons from the on-device API server (`/app-icon?package_name=...&icon_only`) of the
 * device [ipAddress] with Coil.
 */
class AppIconLoader(
    // obf: f7721
    val context: Context,
) {
    // obf: C1872

    /** IP of the current device; set by the screens before loading. */
    // obf: f7720
    var ipAddress: String? = null

    // obf: f7722 — C2916 = ImageLoader.Builder: m5312 = respectCacheHeaders(false), m5323 = crossfade(100)
    private val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .respectCacheHeaders(false)
        .crossfade(100)
        .build()

    // obf: m4051 (static on the instance = R8; no default args)
    fun load(icon: ShapeableImageView, packageName: String) {
        val url = "http://" + ipAddress + ':' + ServerInstaller.SERVER_CONSTANT +
            "/app-icon?package_name=" + packageName + "&icon_only"
        val token: String = NativeConfig.apiInterceptorToken
        val request = ImageRequest.Builder(context)
            .target(icon)
            .placeholder(R.drawable.ic_app_placeholder)
            .error(R.drawable.ic_app_placeholder)
            .data(url)
            .addHeader("token", token)
            .build()
        imageLoader.enqueue(request)
    }
}

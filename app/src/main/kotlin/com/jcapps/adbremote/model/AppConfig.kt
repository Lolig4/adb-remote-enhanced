package com.jcapps.adbremote.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.jcapps.adbremote.Constants
import kotlinx.parcelize.Parcelize

/**
 * Remote app configuration fetched from `api/app_v6.json`.
 *
 * RECON-UNSURE: the decompiled `toString` reads `Config(...)`; the registry fixes the name as
 * `AppConfig`. All properties default (so Gson can build it with the no-arg constructor), with
 * the website/links defaulting to the [Constants] values.
 */
// obf: C3073
data class AppConfig(
    // obf: f11072 / m5474
    @SerializedName("update") val update: Update? = null,
    // obf: f11071 / m5472 / m5470
    @SerializedName("preferred_website") var preferredWebsite: String = Constants.website,
    // obf: f11074 / m5471
    @SerializedName("telegram_channel") val telegramChannel: Link = Constants.telegramLink,
    // obf: f11073 / m5468
    @SerializedName("facebook_page") val facebookPage: Link = Constants.facebookPageLink,
    // obf: f11076 / m5469
    @SerializedName("ad") val ad: Ad? = null,
    // obf: f11075 / m5473
    @SerializedName("strict") val isStrictMode: Boolean = true,
) {

    /** In-app update descriptor. */
    // obf: C3074
    @Parcelize
    data class Update(
        // obf: f11077 / m5476
        @SerializedName("title") val title: String,
        // obf: f11082 / m5480
        @SerializedName("version") val version: Long,
        // obf: f11079 / m5479
        @SerializedName("message") val message: String,
        // obf: f11078 / m5481
        @SerializedName("optional") val optional: Boolean,
        // obf: f11081 / m5475
        @SerializedName("skippable") val skippable: Boolean,
        // obf: f11083 / m5478
        @SerializedName("package_name") val packageName: String,
        // obf: f11080 / m5477
        @SerializedName("download") val download: String,
    ) : Parcelable

    /** Promotional ad descriptor. */
    // obf: C3075
    @Parcelize
    data class Ad(
        // obf: f11084 / m5485
        @SerializedName("image") val image: String,
        // obf: f11088 / m5483
        @SerializedName("countdown") val countdown: Int,
        // obf: f11086 / m5484
        @SerializedName("skippable") val skippable: Boolean,
        // obf: f11085 / m5482
        @SerializedName("type") val type: Int,
        // obf: f11087 / m5486
        @SerializedName("link") val link: Link,
    ) : Parcelable
}

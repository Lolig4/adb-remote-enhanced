package com.jcapps.adbremote

import com.jcapps.adbremote.model.Link

/**
 * App-wide constants: default ADB-server token, remote config path, guide pages, AdMob unit ids,
 * support links and website URLs.
 *
 * Variant 1: the link/URL values that originally came from `libapp.so` (`C0104.mNNN()`) are
 * read from [NativeConfig].
 */
object Constants {
    // obf: C2120

    /** Default token of the on-device ADB server ("use_default_adb_server_token"). */
    const val DEFAULT_ADB_SERVER_TOKEN = "d6188332-6ef0-4ea1-bab1-b139110d54f6"

    /** Remote app configuration, relative to [website]. */
    const val APP_CONFIG_PATH = "api/app_v6.json"

    // ---- guide pages (relative to [website]) ----
    const val GUIDE_HOW_TO_CONNECT = "guide/how-to-connect"
    const val GUIDE_PREMIUM_ACCESS = "premium-access"
    const val GUIDE_WIFI_PAIRING_MODE = "guide/wifi-pairing-mode"
    const val GUIDE_HOW_TO_USE_FILES = "guide/how-to-use-files"
    const val GUIDE_HOW_TO_USE_CLIPBOARD = "guide/how-to-use-clipboard"
    const val GUIDE_HOW_TO_USE_NTP = "guide/how-to-use-ntp"

    // ---- support links ----
    // obf: f8531
    val telegramLink = Link(NativeConfig.telegramDeepLink, NativeConfig.telegramUrl)

    // obf: f8538
    val facebookPageLink = Link(NativeConfig.facebookPageDeepLink, NativeConfig.facebookPageUrl)

    // obf: f8536
    val facebookProfileLink = Link(NativeConfig.facebookProfileDeepLink, NativeConfig.facebookProfileUrl)

    // ---- websites ----
    /** All known mirrors of the website / API (first = default). */
    // obf: f8541
    val websites: Array<String> = NativeConfig.apiFallbackUrls

    /** Default website (first mirror, or "" when none). */
    // obf: f8540
    val website: String = websites.firstOrNull() ?: ""

    // obf: f8535
    val termsMarkdownUrl: String = "$website/terms.md"

    // obf: f8537
    val appTermsUrl: String = "$website/app-terms"

    // obf: f8530
    val privacyPolicyUrl: String = "$website/app-privacy-policy"

    // obf: f8532
    val applyIconPackUrl: String = "$website/developer/apply-icon-pack"

    // obf: f8533
    val translationsUrl: String = NativeConfig.translationsUrl

    /** Default NTP servers. */
    // obf: f8539
    val ntpHosts: Array<String> = arrayOf(
        "time.android.com",
        "time.google.com",
        "time.aws.com",
        "pool.ntp.org",
        "time.nist.gov",
    )
}

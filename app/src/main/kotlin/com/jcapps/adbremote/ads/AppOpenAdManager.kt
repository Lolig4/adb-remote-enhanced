package com.jcapps.adbremote.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.jcapps.adbremote.AdbRemoteApp
import com.jcapps.adbremote.Constants
import com.jcapps.adbremote.utils.FeatureUsage

/**
 * AdMob App Open ad of [app]: loaded ads are reused for 4 hours (NTP-safe time) and reloaded
 * after being shown or failing to show.
 */
class AppOpenAdManager(
    // obf: f7232
    private val app: AdbRemoteApp,
) {
    // obf: C4081

    // obf: f7228
    private var isLoadingAd: Boolean = false

    // obf: f7229
    private var appOpenAd: AppOpenAd? = null

    // obf: f7230
    private var loadTime: Long = 0L

    // obf: f7231
    private var isShowingAd: Boolean = false

    private fun now(): Long = app.sntpClient.cachedTime()?.timestamp ?: System.currentTimeMillis()

    /** Shows the loaded ad if it is fresh, otherwise loads a new one (without showing it). */
    // obf: m3874
    fun show(activity: Activity) {
        if (isShowingAd) {
            return
        }
        if (appOpenAd != null && now() - loadTime < 14400000) {
            // obf: C2379 case 0
            appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isShowingAd = false
                    load(false, activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    appOpenAd = null
                    isShowingAd = false
                    load(false, activity)
                }

                override fun onAdShowedFullScreenContent() {
                    FeatureUsage.isFullScreenAdShown = true
                }
            }
            isShowingAd = true
            appOpenAd?.show(activity)
            // obf: C0823.f4884
            InterstitialAdManager.lastShownTime = System.currentTimeMillis()
            return
        }
        load(false, activity)
    }

    /** Loads an ad unless loading or a fresh one is cached; shows it on load when [showOnLoad]. */
    // obf: m3875
    fun load(showOnLoad: Boolean, activity: Activity) {
        if (isLoadingAd) {
            return
        }
        if (appOpenAd != null && now() - loadTime < 14400000) {
            return
        }
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        // obf: C2864
        AppOpenAd.load(
            activity,
            Constants.AD_UNIT_APP_OPEN,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                }

                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = now()
                    if (showOnLoad) {
                        show(activity)
                    }
                }
            },
        )
    }
}

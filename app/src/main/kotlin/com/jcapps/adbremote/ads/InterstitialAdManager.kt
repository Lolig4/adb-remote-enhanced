package com.jcapps.adbremote.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.jcapps.adbremote.Constants
import com.jcapps.adbremote.utils.FeatureUsage

/**
 * Process-wide holder of the AdMob interstitial / rewarded-interstitial ads with a 2-minute
 * show throttle. RECON-UNSURE: object/member names (only the static fields survived).
 */
object InterstitialAdManager {
    // obf: C0823 (INSTANCE f4881)

    // obf: f4880
    @JvmStatic
    var interstitialAd: InterstitialAd? = null

    // obf: f4882
    @JvmStatic
    var rewardedInterstitialAd: RewardedInterstitialAd? = null

    // obf: f4883
    @JvmStatic
    var isLoading: Boolean = false

    // obf: f4884
    @Volatile
    @JvmStatic
    var lastShownTime: Long = 0L

    /** Loads an interstitial unless one is loading or cached. (inlined: C2418, C1321, m2595; "context") */
    fun load(context: Context) {
        if (isLoading || interstitialAd != null) {
            return
        }
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            Constants.AD_UNIT_INTERSTITIAL,
            adRequest,
            // obf: C0915
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoading = false
                    interstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    isLoading = false
                    this@InterstitialAdManager.interstitialAd = interstitialAd
                    FeatureUsage.isInterstitialAdLoaded = true
                }
            },
        )
    }

    /** Shows the cached interstitial (at most every 2 minutes), otherwise starts loading one. */
    // obf: m2595
    fun show(activity: Activity) {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastShownTime < 120000) {
                return
            }
            lastShownTime = now
            val ad = interstitialAd
            if (ad != null) {
                // obf: C1321 default case
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        load(activity)
                    }

                    override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                        interstitialAd = null
                    }
                }
                interstitialAd?.show(activity)
                return
            }
            load(activity)
        }
    }

    /** Pre-loads a rewarded interstitial. (inlined: SettingsActivity, MainActivity C2497) */
    fun loadRewarded(context: Context) {
        RewardedInterstitialAd.load(
            context,
            Constants.AD_UNIT_REWARDED_INTERSTITIAL,
            AdRequest.Builder().build(),
            // obf: C3853
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedInterstitialAd = null
                }

                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                    FeatureUsage.isInterstitialAdLoaded = true
                }
            },
        )
    }

    /** Shows the rewarded interstitial if cached, otherwise loads one. (inlined into C2497; "activity") */
    fun showRewarded(activity: Activity) {
        val ad = rewardedInterstitialAd
        if (ad == null) {
            loadRewarded(activity)
            return
        }
        // obf: C1772
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedInterstitialAd = null
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                rewardedInterstitialAd = null
            }
        }
        // obf: C0583 case 6 — empty OnUserEarnedRewardListener
        rewardedInterstitialAd?.show(activity) { }
        lastShownTime = System.currentTimeMillis()
    }
}

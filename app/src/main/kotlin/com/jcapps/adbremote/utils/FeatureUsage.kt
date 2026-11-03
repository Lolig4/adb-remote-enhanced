package com.jcapps.adbremote.utils

import android.content.SharedPreferences
import com.google.gson.Gson
import com.jcapps.adbremote.model.Feature
import com.jcapps.adbremote.prefs.AppPrefs
import java.util.Collections
import java.util.EnumSet

/**
 * Tracks which [Feature]s were used in this session (persisted as JSON under "used_features")
 * and decides whether a feature may still be used for free.
 *
 * The four volatile flags are set by the ad callbacks; any of them being set unlocks all
 * features for the session. RECON-UNSURE: the flag names (only their setters survive):
 *  - [isFullScreenAdShown]: FullScreenContentCallback.onAdShowedFullScreenContent (C2379 case 0)
 *  - [isNativeAdLoaded]: native ad loaded (C0206 / C3733)
 *  - [isInterstitialAdLoaded]: InterstitialAd loaded (C0915 / C3853)
 *  - [isRewardEarned]: reward earned (C0330)
 */
object FeatureUsage {
    // obf: AbstractC0763

    // obf: f4766
    @Volatile
    var isFullScreenAdShown: Boolean = false

    // obf: f4767
    @Volatile
    var isNativeAdLoaded: Boolean = false

    // obf: f4768
    @Volatile
    var isInterstitialAdLoaded: Boolean = false

    // obf: f4769
    @Volatile
    var isRewardEarned: Boolean = false

    // obf: f4770
    val usedFeatures: MutableSet<Feature> = Collections.synchronizedSet(EnumSet.noneOf(Feature::class.java))

    /** True when [feature] may be used (not yet used, an ad unlocked it, or non-strict config). */
    // obf: m2558
    fun canUseFeature(prefs: AppPrefs, feature: Feature): Boolean {
        // RECON-UNSURE: `prefs.config` = AppPrefs.m6507() (owner: AppPrefs skeleton, core-app).
        return !usedFeatures.contains(feature) || isRewardEarned || isInterstitialAdLoaded ||
            isFullScreenAdShown || isNativeAdLoaded || !prefs.config.isStrictMode
    }

    /** Marks [feature] as used and persists the set under "used_features". */
    // obf: m2559
    fun trackFeatureUsage(dataStore: SharedPreferences, feature: Feature) {
        val set = usedFeatures
        if (set.add(feature)) {
            val gson = Gson()
            dataStore.edit().putString("used_features", gson.toJson(set)).apply()
        }
    }
}

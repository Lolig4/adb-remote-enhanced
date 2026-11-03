package com.jcapps.adbremote.utils

import android.content.Context
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatRatingBar
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.jcapps.adbremote.model.App
import com.jcapps.adbremote.Constants
import com.jcapps.adbremote.ui.BaseActivity

// obf: AbstractC3420.m5959 (forNativeAd = C3733 case 15, listener = C1535)
fun loadNativeAd(
    context: BaseActivity,
    startMuted: Boolean,
    onLoaded: (NativeAd, Int) -> Unit,
    onFailed: () -> Unit,
) {
    val builder = AdLoader.Builder(context, Constants.AD_UNIT_NATIVE)
        .forNativeAd { nativeAd -> onLoaded(nativeAd, 0) }
        .withAdListener(object : com.google.android.gms.ads.AdListener() {
            override fun onAdFailedToLoad(p0: LoadAdError) {
                onFailed()
            }
        })
    if (startMuted) {
        val videoOptions = VideoOptions.Builder().setStartMuted(true).build()
        builder.withNativeAdOptions(NativeAdOptions.Builder().setVideoOptions(videoOptions).build())
    } else {
        builder.withNativeAdOptions(NativeAdOptions.Builder().build())
    }
    builder.build().loadAd(AdRequest.Builder().build())
}

/* ---- added by main-a (OPEN_ITEMS "custom utils/NativeAd.kt"; needed by MainActivity) ---- */

// obf: AbstractC3420.f12246 — native ads loaded into list slots
val nativeAds: ArrayList<NativeAd> = ArrayList()

/* ---- added by apps (AppsAdapter.loadAds) ---- */

/** True while the batch [loadNativeAds] AdLoader is still loading. */
// obf: AbstractC3420.f12245
var isLoadingNativeAds: Boolean = false

/**
 * Loads native ads until [nativeAds] holds [count] ads (one batch request at a time). [onLoaded] /
 * [onFailed] get the ad position (index into [nativeAds]) of each result.
 */
// obf: AbstractC3420 (R8-inlined into C0206; leftover checks "context"/"onLoaded"/"onFailed";
//  forNativeAd = anonymous OnNativeAdLoadedListener, ad listener = C1839). RECON-UNSURE: name.
fun loadNativeAds(
    context: Context,
    count: Int,
    onLoaded: (NativeAd, Int) -> Unit,
    onFailed: (Int) -> Unit,
) {
    if (count > nativeAds.size && !isLoadingNativeAds) {
        val size = count - nativeAds.size
        var adPosition = nativeAds.size
        isLoadingNativeAds = true
        // obf: C3559 (Ref.ObjectRef)
        var adLoader: AdLoader? = null
        val loader = AdLoader.Builder(context, Constants.AD_UNIT_NATIVE)
            .forNativeAd { ad ->
                FeatureUsage.isNativeAdLoaded = true
                nativeAds.add(ad)
                onLoaded(ad, adPosition)
                adPosition++
                isLoadingNativeAds = adLoader?.isLoading ?: false
            }
            .withAdListener(object : AdListener() {
                // obf: C1839
                override fun onAdFailedToLoad(p0: LoadAdError) {
                    onFailed(adPosition)
                    adPosition++
                    isLoadingNativeAds = adLoader?.isLoading ?: false
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
        adLoader = loader
        loader.loadAds(AdRequest.Builder().build(), size)
    }
}

/** Destroys and forgets all list native ads. */
// obf: AbstractC3420.m5956
fun destroyNativeAds() {
    for (nativeAd in nativeAds) {
        try {
            nativeAd.destroy()
        } catch (e: Exception) {
        }
    }
    nativeAds.clear()
}

/** Inserts up to 5 ad placeholder rows (index 2, then every 6th). */
// obf: AbstractC3420.m5957
fun insertAdPlaceholders(apps: List<App>): ArrayList<App> {
    val list = ArrayList(apps)
    var adPosition = 0
    var index = 2
    while (index < list.size) {
        list.add(index, App(true, adPosition, "", false, false, false, false, "", 0L, ""))
        index += 6
        adPosition++
        if (adPosition >= 5) {
            break
        }
    }
    return list
}

/** Binds [nativeAd] to an ad layout's views. */
// obf: AbstractC3420.m5958
fun populateNativeAd(
    nativeAd: NativeAd,
    nativeAdView: NativeAdView,
    icon: AppCompatImageView,
    headline: MaterialTextView,
    adStars: AppCompatRatingBar,
    adPrice: MaterialTextView,
    adStore: MaterialTextView,
    callToAction: MaterialButton,
) {
    headline.background = null
    icon.background = null
    callToAction.isEnabled = true
    if (nativeAd.icon == null) {
        icon.visibility = View.GONE
    } else {
        icon.setImageDrawable(nativeAd.icon?.drawable)
        icon.visibility = View.VISIBLE
    }
    headline.text = nativeAd.headline
    if (nativeAd.callToAction == null) {
        callToAction.visibility = View.GONE
    } else {
        callToAction.visibility = View.VISIBLE
    }
    if (nativeAd.price == null) {
        adPrice.visibility = View.INVISIBLE
    } else {
        adPrice.visibility = View.VISIBLE
        adPrice.text = nativeAd.price
    }
    if (nativeAd.store != null) {
        nativeAdView.storeView = adStore
        adStore.visibility = View.VISIBLE
        adStore.text = nativeAd.store
    } else if (nativeAd.advertiser == null) {
        adStore.visibility = View.INVISIBLE
    } else {
        nativeAdView.advertiserView = adStore
        adStore.visibility = View.VISIBLE
        adStore.text = nativeAd.advertiser
    }
    if (nativeAd.starRating == null) {
        adStars.visibility = View.INVISIBLE
    } else {
        adStars.visibility = View.VISIBLE
        adStars.rating = nativeAd.starRating!!.toFloat()
    }
    nativeAdView.setNativeAd(nativeAd)
}

package com.jcapps.adbremote.utils

import android.app.Activity
import androidx.preference.PreferenceManager
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.Timer
import kotlin.concurrent.schedule

/*
 * In-app review prompt. The two flags were R8-merged into the helper class `z8`; the prompt
 * helper itself was inlined at every call site (MainActivity C1425 cases 20/21/27, C0808 cases
 * 10/11 — leftover parameter check "activity"), its timer task is C2696 case 0 and the UI-thread
 * body RunnableC0115 case 0. RECON-UNSURE: all names in this file.
 */

/** Set once the ADB service reported "connected" in this process (AdbService, C2247 case 6). */
// obf: z8.f2570
@Volatile
var hasConnected: Boolean = false

/** Set once a command was sent while connected (MainActivity.sendCommand). */
// obf: z8.f2572
@Volatile
var hasSentCommand: Boolean = false

/**
 * After a successful session (connected + used), asks Google Play for the in-app review flow
 * once (300 ms delay) unless it was already recommended.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun requestAppReviewIfRecommended(activity: Activity) {
    if (hasConnected && hasSentCommand) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        if (!prefs.getBoolean("app_review_recommended", false)) {
            // obf: C2696 case 0
            Timer().schedule(300L) {
                if (!activity.isDestroyed) {
                    activity.runOnUiThread { launchAppReview(activity) }
                }
            }
        }
    }
}

/** Starts the Play in-app review flow and marks the review as recommended. */
// obf: RunnableC0115 case 0 (+ C3733 / C3629 case 1 success listeners)
fun launchAppReview(activity: Activity) {
    val reviewManager = ReviewManagerFactory.create(activity.applicationContext ?: activity)
    reviewManager.requestReviewFlow().addOnSuccessListener { reviewInfo ->
        reviewManager.launchReviewFlow(activity, reviewInfo)
    }
    PreferenceManager.getDefaultSharedPreferences(activity).edit()
        .putBoolean("app_review_recommended", true)
        .apply()
}

package com.jcapps.adbremote.utils

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.transition.TransitionManager

/* App top-level Fragment helpers (R8-merged into helper class AbstractC3745). RECON-UNSURE names. */

/** True while the fragment is attached with a view. */
// obf: AbstractC3745.m6308
fun Fragment.isSafe(): Boolean =
    !isRemoving && activity != null && !isDetached && isAdded && view != null

/** Starts an auto transition on the fragment view's parent. */
// obf: AbstractC3745.m6296
fun Fragment.beginDelayedTransition() {
    TransitionManager.beginDelayedTransition(requireView().parent as ViewGroup, null)
}

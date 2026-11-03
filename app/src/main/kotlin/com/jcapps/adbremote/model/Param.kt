package com.jcapps.adbremote.model

/**
 * Parameters of one [com.jcapps.adbremote.views.StateView] state: lottie padding, lottie asset
 * name and optional message string resource.
 *
 * RECON-UNSURE: `padding` is proven by toString, `anim` by the copy() parameter-name check;
 * the third name has no plaintext (decrypt key without comment) — `message` is a guess.
 */
data class Param(
    // obf: f4984
    val padding: Int,
    // obf: f4983
    val anim: String,
    // obf: f4985
    val message: Int?,
) {
    // obf: C0853
}

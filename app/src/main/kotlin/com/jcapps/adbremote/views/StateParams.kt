package com.jcapps.adbremote.views

import com.jcapps.adbremote.model.Param

/**
 * Per-state presentation (lottie animation, padding, optional message) for a [StateView].
 *
 * RECON-UNSURE: the decompiled `toString` reads `Params(...)` — the original type may have been
 * named `Params` (possibly nested in StateView); the registry fixes the name as `StateParams`.
 */
// obf: C2593
data class StateParams(
    // obf: f9776
    val disconnected: Param,
    // obf: f9775
    val loading: Param,
    // obf: f9777
    val empty: Param,
)

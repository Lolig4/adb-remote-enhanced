package com.jcapps.adbremote.model

import androidx.annotation.StringRes

/**
 * A titled value row (title = string resource, value = text).
 *
 * RECON-UNSURE: the decompiled `toString` reads `Data(title=..., value=...)`, so the original was
 * probably a nested `Data` class of some holder; the registry fixes the name as `TitleValue`.
 */
data class TitleValue(
    // obf: f6196
    @StringRes val title: Int,
    // obf: f6195
    val value: String,
) {
    // obf: C1293
}

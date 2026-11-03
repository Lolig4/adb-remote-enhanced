package com.jcapps.adbremote.model

import androidx.annotation.StringRes

data class Info(
    @StringRes val title: Int,
    val value: String,
) {
    // obf: C2776
}

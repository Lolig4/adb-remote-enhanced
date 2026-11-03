package com.jcapps.adbremote.model

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class Tool(
    // obf: f4055
    val type: ToolType,
    // obf: f4054
    @StringRes val name: Int,
    // obf: f4057
    @DrawableRes val icon: Int,
    // obf: f4056
    @ColorRes val iconColor: Int,
) {
    // obf: C0493
}

package com.jcapps.adbremote.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.jcapps.adbremote.R

/**
 * A remote-control button that can be placed on the home screen grid (stored under `btn_rowX_Y`
 * by [key]) or shown in the extra-buttons sheet.
 *
 * RECON-UNSURE: constant identifiers (R8 passes the same string as both enum name and key; core-adb
 * models ConnectionMethod the same way as `UPPER("value")`), and the names of the two flags
 * (semantics recovered from ButtonManager: haptic on press / repeat while held).
 */
enum class VirtualButton(
    // obf: f12082
    val key: String,
    // obf: f12086
    @DrawableRes val icon: Int,
    // obf: f12084
    @StringRes val title: Int,
    // obf: f12083
    val hapticFeedback: Boolean = true,
    // obf: f12085
    val continuousPress: Boolean = false,
) {
    // obf: EnumC3372
    // obf: f12080
    MUTE("mute", R.drawable.ic_volume_mute, R.string.mute),

    // obf: f12072
    POWER("power", R.drawable.ic_power, R.string.power),

    // obf: f12077
    KEYBOARD("keyboard", R.drawable.ic_keyboard, R.string.keyboard, hapticFeedback = false),

    // obf: f12078
    TRACKPAD("trackpad", R.drawable.ic_trackpad, R.string.trackpad, hapticFeedback = false),

    // obf: f12081
    NUMPAD("numpad", R.drawable.ic_numpad, R.string.numpad, hapticFeedback = false),

    // obf: f12068
    BACK("back", R.drawable.ic_back, R.string.back),

    // obf: f12079
    HOME("home", R.drawable.ic_home, R.string.home),

    // obf: f12073
    VOLUME_DOWN("volume_down", R.drawable.ic_volume_down, R.string.volume_down, continuousPress = true),

    // obf: f12071
    VOLUME_UP("volume_up", R.drawable.ic_volume_up, R.string.volume_up, continuousPress = true),

    // obf: EF16
    MENU("menu", R.drawable.ic_menu, R.string.menu),

    // obf: f12070
    EXTRA_BUTTONS("extra_buttons", R.drawable.ic_extension, R.string.extra_buttons, hapticFeedback = false),

    // obf: f12075
    PREVIOUS("previous", R.drawable.ic_skip_previous, R.string.previous_2, continuousPress = true),

    // obf: f12074
    NEXT("next", R.drawable.ic_skip_next, R.string.next_2, continuousPress = true),

    // obf: EF495
    TAB("tab", R.drawable.ic_tab, R.string.tab_2),

    // obf: EF530
    PLAY_PAUSE("play_pause", R.drawable.ic_play_pause, R.string.play_pause),

    // obf: EF563
    STOP("stop", R.drawable.ic_stop, R.string.stop_2),

    // obf: EF596
    SCREENSHOT("screenshot", R.drawable.ic_screenshot, R.string.screenshot),

    // obf: EF629
    F1("f1", R.drawable.ic_f1, R.string.f1),

    // obf: EF664
    F2("f2", R.drawable.ic_f2, R.string.f2),

    // obf: EF699
    F3("f3", R.drawable.ic_f3, R.string.f3),

    // obf: EF734
    F4("f4", R.drawable.ic_f4, R.string.f4),

    // obf: EF767
    F5("f5", R.drawable.ic_f5, R.string.f5),

    // obf: EF800
    F6("f6", R.drawable.ic_f6, R.string.f6),

    // obf: EF833
    F7("f7", R.drawable.ic_f7, R.string.f7),

    // obf: EF866
    F8("f8", R.drawable.ic_f8, R.string.f8),

    // obf: EF899
    F9("f9", R.drawable.ic_f9, R.string.f9),

    // obf: EF932
    F10("f10", R.drawable.ic_f10, R.string.f10),

    // obf: EF967
    F11("f11", R.drawable.ic_f11, R.string.f11),

    // obf: EF1002
    F12("f12", R.drawable.ic_f12, R.string.f12),
}

package com.jcapps.adbremote.model

import com.google.gson.annotations.SerializedName

/** Per-device Files-screen settings: which app/mode to open files remotely with. */
class FilesSettings {
    // obf: C3993

    // obf: f13970 / m6592 / m6594
    @SerializedName("open_remotely_using")
    var openRemotelyUsing: IntentParams.RunType = IntentParams.RunType.SHELL

    // obf: f13969 / m6593 / m6595 — default = MxPlayerDecodeMode.HW_PLUS.value (2)
    @SerializedName("mx_player_decode_mode")
    var mxPlayerDecodeMode: Int = MxPlayerDecodeMode.HW_PLUS.value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilesSettings) return false
        return openRemotelyUsing == other.openRemotelyUsing && mxPlayerDecodeMode == other.mxPlayerDecodeMode
    }

    override fun hashCode(): Int = openRemotelyUsing.hashCode() * 31 + mxPlayerDecodeMode

    override fun toString(): String =
        "FilesSettings(openRemotelyUsing=$openRemotelyUsing, mxPlayerDecodeMode=$mxPlayerDecodeMode)"
}

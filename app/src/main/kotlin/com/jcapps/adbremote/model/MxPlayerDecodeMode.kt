package com.jcapps.adbremote.model

/**
 * MX Player "decode_mode" intent extra values (HW=1, HW+=2, SW=4). The original enum carried the
 * raw int in its constructor; constant names are reconstructed from the MX Player API.
 */
enum class MxPlayerDecodeMode(val value: Int) {
    // obf: EnumC1960 (RECON-UNSURE: original constant names were obfuscated)
    HW(1),
    HW_PLUS(2),
    SW(4);

    companion object {
        // obf: companion (C2352 f8022) — lookup used in FileViewHolder
        fun fromValue(value: Int): MxPlayerDecodeMode? = entries.firstOrNull { it.value == value }
    }
}

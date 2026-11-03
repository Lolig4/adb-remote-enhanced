package com.jcapps.adbremote.utils

import android.view.KeyEvent
import com.jcapps.adbremote.model.VirtualKey

/**
 * Translates Android [KeyEvent]s to HID keyboard usages, tracking one held modifier key.
 * (R8 merged this into the multi-purpose class C0713, constructor case 11.)
 */
class HidKeyMapper {
    // obf: C0713#11
    // obf: f4614 — currently held Android modifier keyCode
    private var modifierKey: Int? = null

    // obf: f4615 — CTRL_LEFT/RIGHT, SHIFT_LEFT/RIGHT, ALT_LEFT/RIGHT
    private val modifierKeys = setOf(113, 114, 59, 60, 57, 58)

    // obf: m2508
    fun map(keyEvent: KeyEvent): VirtualKey? {
        val keyCode = keyEvent.keyCode
        val action = keyEvent.action
        val hidKey = toHidKey(keyCode) ?: return null
        val virtualKey = VirtualKey(hidKey, modifierKey?.let { toHidKey(it) })
        if (action == KeyEvent.ACTION_DOWN) {
            if (modifierKey == null && keyCode in modifierKeys) {
                modifierKey = keyCode
            }
            return virtualKey
        }
        if (action == KeyEvent.ACTION_UP && modifierKey == keyCode) {
            modifierKey = null
        }
        return null
    }

    companion object {
        /** Android keyCode -> HID keyboard/keypad usage id. */
        // obf: m2500
        fun toHidKey(keyCode: Int): Int? = when (keyCode) {
            in 29..54 -> keyCode - 25
            in 8..16 -> keyCode + 22
            7 -> 39
            66 -> 40
            111 -> 41
            67 -> 42
            61 -> 43
            62 -> 44
            69 -> 45
            70 -> 46
            71 -> 47
            72 -> 48
            73 -> 49
            74 -> 51
            75 -> 52
            68 -> 53
            55 -> 54
            56 -> 55
            76 -> 56
            115 -> 57
            131 -> 58
            132 -> 59
            133 -> 60
            134 -> 61
            135 -> 62
            136 -> 63
            137 -> 64
            138 -> 65
            139 -> 66
            140 -> 67
            141 -> 68
            142 -> 69
            209 -> 58
            64 -> 62
            120 -> 70
            116 -> 71
            121 -> 72
            124 -> 73
            122 -> 74
            92 -> 75
            112 -> 76
            123 -> 77
            93 -> 78
            22 -> 79
            21 -> 80
            20 -> 81
            19 -> 82
            143 -> 83
            154 -> 84
            155 -> 85
            156 -> 86
            157 -> 87
            145 -> 89
            146 -> 90
            147 -> 91
            148 -> 92
            149 -> 93
            150 -> 94
            151 -> 95
            152 -> 96
            153 -> 97
            144 -> 98
            158 -> 99
            82 -> 101
            26 -> 102
            161 -> 103
            277 -> 123
            278 -> 124
            279 -> 125
            164 -> 127
            24 -> 128
            25 -> 129
            159 -> 133
            217 -> 135
            215 -> 136
            216 -> 137
            214 -> 138
            213 -> 139
            218 -> 144
            212 -> 145
            211 -> 148
            162 -> 182
            163 -> 183
            113 -> 224
            59 -> 225
            57 -> 226
            114 -> 228
            60 -> 229
            58 -> 230
            85 -> 232
            86 -> 233
            88 -> 234
            87 -> 235
            129 -> 236
            125 -> 242
            223 -> 248
            250 -> 250
            210 -> 251
            else -> null
        }
    }
}

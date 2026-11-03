package com.jcapps.adbremote.utils

class UnsupportedCharacterException(
    // obf: f6963
    val char: Char,
    // obf: f6962
    val passed: String,
    message: String,
) : Exception(message) {
    // obf: C1580
}

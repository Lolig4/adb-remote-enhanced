package com.jcapps.adbremote.ntp

import java.util.Locale

/** Thrown by [SntpClient] when an NTP server response fails validation. */
class InvalidNtpServerResponseException : Exception {
    // obf: C1864

    constructor(detailMessage: String) : super(detailMessage)

    constructor(actual: Int, expected: Int, format: String, property: String) :
        super(String.format(Locale.ENGLISH, format, property, actual, expected))
}

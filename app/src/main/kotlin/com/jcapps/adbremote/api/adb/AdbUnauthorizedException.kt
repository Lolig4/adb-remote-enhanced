package com.jcapps.adbremote.api.adb

/**
 * Thrown during the ADB AUTH handshake when the device never answers (socket timeout), i.e. the
 * user ignored the on-device USB-debugging prompt. Message at the throw site:
 * "User ignored USB Debugging prompt."
 */
// obf: C2513 (claimed core-adb)
class AdbUnauthorizedException(message: String) : Exception(message)

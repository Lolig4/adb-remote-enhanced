package com.jcapps.adbremote.model

/** Mutable validation flags for the FTP-settings input form ([DialogFtpSettings]). */
class InputValidation {
    // obf: C3799

    // obf: f13393
    var usernameValidated: Boolean = false
    // obf: f13392
    var portValidated: Boolean = false
    // obf: f13395
    var customRootDirValidated: Boolean = false
    // obf: f13394
    var fallbackRootDirValidated: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InputValidation) return false
        return usernameValidated == other.usernameValidated && portValidated == other.portValidated &&
            customRootDirValidated == other.customRootDirValidated &&
            fallbackRootDirValidated == other.fallbackRootDirValidated
    }

    override fun hashCode(): Int {
        var result = if (usernameValidated) 1231 else 1237
        result = result * 31 + if (portValidated) 1231 else 1237
        result = result * 31 + if (customRootDirValidated) 1231 else 1237
        return result * 31 + if (fallbackRootDirValidated) 1231 else 1237
    }

    override fun toString(): String =
        "InputValidation(usernameValidated=$usernameValidated, portValidated=$portValidated, " +
            "customRootDirValidated=$customRootDirValidated, " +
            "fallbackRootDirValidated=$fallbackRootDirValidated)"
}

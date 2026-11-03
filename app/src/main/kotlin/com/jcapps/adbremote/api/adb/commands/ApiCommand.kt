package com.jcapps.adbremote.api.adb.commands

/**
 * Command queued to the on-device server API connection (FQN from string evidence
 * "com.jcapps.adbremote.api.adb.commands.ApiCommand").
 * RECON-UNSURE: subclass names (only their single fields survived).
 */
sealed class ApiCommand {
    // obf: AbstractC0662
    class KeyEvent(
        // obf: f10682
        val keyCode: Int,
    ) : ApiCommand() {
        // obf: C2950
    }

    class Text(
        // obf: f6925
        val text: String,
    ) : ApiCommand() {
        // obf: C1561
    }
}

package com.jcapps.adbremote.api.adb.commands

/**
 * Command queued to the adb-binary connection (FQN from string evidence
 * "com.jcapps.adbremote.api.adb.commands.BinaryCommand").
 * RECON-UNSURE: subclass names (only their single fields survived).
 */
sealed class BinaryCommand {
    // obf: AbstractC1335
    class KeyEvent(
        // obf: f9973
        val keyCode: Int,
    ) : BinaryCommand() {
        // obf: C2669
    }

    class Text(
        // obf: f11287
        val text: String,
    ) : BinaryCommand() {
        // obf: C3147
    }
}

package com.jcapps.adbremote.transfer

/**
 * Callbacks of a [FileTransfer] into the progress sheet
 * ([com.jcapps.adbremote.dialogs.files.DialogFileProgress] is the only implementation).
 *
 * RECON-UNSURE: R8 stripped every method of this interface (obf InterfaceC4050 is empty) and
 * devirtualized the calls to the dialog; the members below are reconstructed from those call
 * sites (their bodies live in DialogFileProgress).
 */
interface TransferListener {
    // obf: InterfaceC4050

    /** Updates one item's percent and processed-size label. */
    // obf: C2631.m5015
    fun onFileProgress(id: Int, percent: Int, processedSize: String)

    /** Marks one item as paused (cancelled) or failed with [e]'s message. */
    // obf: inlined into AbstractC4056.m6636
    fun onFileError(id: Int, e: Exception)

    /** The transfer stopped; offers to resume. */
    // obf: C0561 body (C2631.m5020(RESUME))
    fun onTransferPaused()

    /** Overall progress; [completed] = every item processed. */
    // obf: C1482 body
    fun onTotalProgress(percent: Int, completed: Boolean)

    /** Asks the user what to do with an existing [name]; answers (action, applyToAll). */
    // obf: C2631.m5018
    fun showConflictDialog(name: String, callback: (ConflictAction, Boolean) -> Unit)
}

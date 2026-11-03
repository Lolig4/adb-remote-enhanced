package com.jcapps.adbremote.transfer

/**
 * Failure of a single transfer item: the item is marked as failed and the transfer continues with
 * the next one (any other exception pauses the transfer). RECON-UNSURE: class name.
 */
class TransferException : Exception {
    // obf: C3242

    constructor(message: String) : super(message)

    constructor(e: Throwable) : super(e)
}

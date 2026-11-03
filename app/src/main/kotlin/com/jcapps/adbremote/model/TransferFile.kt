package com.jcapps.adbremote.model

/**
 * Base of the two transfer-item kinds shown in the progress sheet. The primary constructor
 * validates (name, fileName) exactly as the original; the actual values are provided by the
 * subclasses through the abstract members.
 *
 * NOTE: the obfuscated base class (AbstractC1652) was an R8 merge of this sealed class with an
 * unrelated font-compat callback and a bag of static zip/crypto/text helpers — only the three
 * abstract members below (overridden by both subclasses) belong to TransferFile.
 */
sealed class TransferFile(name: String, fileName: String) {
    // obf: AbstractC1652

    // obf: mo3829
    abstract val id: Int
    // obf: mo3830
    abstract val name: String
    // obf: mo3827
    abstract val size: Long
}

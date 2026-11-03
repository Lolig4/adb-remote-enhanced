package com.jcapps.adbremote.provider.common

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A validated MIME type string (MaterialFiles MimeType). A `@JvmInline value class` in the
 * original: the artifact has a synthetic boxing constructor and its constants are plain Strings.
 */
@Parcelize
@JvmInline
value class MimeType(val value: String) : Parcelable {
    // obf: C3552

    companion object {
        // obf: f12632
        val ANY = "*/*".asMimeType()

        // obf: f12631
        val DIRECTORY = "vnd.android.document/directory".asMimeType()

        // obf: f12630
        val GENERIC = "application/octet-stream".asMimeType()
    }
}

// obf: AbstractC0999.m2924
fun String.asMimeType(): MimeType {
    require(isValidMimeType)
    return MimeType(this)
}

fun String.asMimeTypeOrNull(): MimeType? = if (isValidMimeType) MimeType(this) else null

// obf: AbstractC0999.m2914
private val String.isValidMimeType: Boolean
    get() {
        val slashIndex = indexOf('/')
        if (slashIndex == -1 || slashIndex !in 1 until length) {
            return false
        }
        val semicolonIndex = indexOf(';')
        if (semicolonIndex != -1) {
            if (semicolonIndex !in slashIndex + 2 until length) {
                return false
            }
        }
        val plusIndex = indexOf('+')
        if (plusIndex != -1 && (semicolonIndex == -1 || plusIndex <= semicolonIndex)) {
            val end = if (semicolonIndex != -1) semicolonIndex - 1 else length
            if (plusIndex !in slashIndex + 2 until end) {
                return false
            }
        }
        return true
    }

/** The "+suffix" of a structured-syntax MIME type, or null (inlined in FilesAdapter). */
val MimeType.suffix: String?
    get() {
        val plusIndex = value.indexOf('+')
        if (plusIndex == -1) return null
        val semicolonIndex = value.indexOf(';')
        if (semicolonIndex != -1 && plusIndex > semicolonIndex) return null
        return value.substring(plusIndex + 1, if (semicolonIndex != -1) semicolonIndex else value.length)
    }

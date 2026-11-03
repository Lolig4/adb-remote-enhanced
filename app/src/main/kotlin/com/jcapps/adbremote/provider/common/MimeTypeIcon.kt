package com.jcapps.adbremote.provider.common

import androidx.annotation.DrawableRes
import com.jcapps.adbremote.R

/**
 * Document/MIME icon category mapping to a drawable (MaterialFiles style). Constant NAMES are
 * reconstructed from the MIME->icon table in [com.jcapps.adbremote.utils.MimeTypes] and the
 * FilesAdapter icon selection (directory / symlink / generic fallback).
 *
 * RECON-UNSURE: original constant names were obfuscated; declaration order and drawables are faithful.
 */
enum class MimeTypeIcon(
    // obf: f3153
    @DrawableRes val resource: Int,
) {
    // obf: EnumC0200
    APK(R.drawable.ic_file_apk),            // f3147
    ARCHIVE(R.drawable.ic_file_archive),        // f3136
    AUDIO(R.drawable.ic_file_audio),          // f3134
    CALENDAR(R.drawable.ic_file_calendar),       // f3145
    CERTIFICATE(R.drawable.ic_file_certificate),    // f3151
    CODE(R.drawable.ic_code),           // f3138
    CONTACT(R.drawable.ic_file_contact),        // f3148
    DIRECTORY(R.drawable.ic_folder),      // f3149
    SYMBOLIC_LINK(R.drawable.ic_file_symlink),  // f3152 (RECON-UNSURE name; used for FTPFile.isSymbolicLink in FilesAdapter)
    DOCUMENT(R.drawable.ic_file_document),       // f3129
    EBOOK(R.drawable.ic_file_ebook),          // f3150
    EMAIL(R.drawable.ic_file_email),          // f3140
    FONT(R.drawable.ic_file_font),           // f3137
    GENERIC(R.drawable.ic_file_generic),        // f3132 (fallback used in FilesAdapter)
    IMAGE(R.drawable.ic_file_image),          // f3143
    PDF(R.drawable.ic_file_pdf),            // f3142
    TEXT(R.drawable.ic_file_text),           // f3146
    EXCEL(R.drawable.ic_file_excel),          // f3131
    POWERPOINT(R.drawable.ic_file_powerpoint),     // f3139
    PRESENTATION(R.drawable.ic_file_presentation),   // f3141
    SPREADSHEET(R.drawable.ic_file_spreadsheet),    // f3135
    VIDEO(R.drawable.ic_file_video),          // f3133
    WORD(R.drawable.ic_file_word);           // f3144
}

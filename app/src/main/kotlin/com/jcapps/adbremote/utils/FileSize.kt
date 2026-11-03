package com.jcapps.adbremote.utils

import java.util.Locale

// obf: AbstractC0104.m1725
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
    val divisors = longArrayOf(
        1, 1000, 1000000, 1000000000, 1000000000000L, 1000000000000000L, 1000000000000000000L,
    )
    val index = minOf((63 - java.lang.Long.numberOfLeadingZeros(size)) / 10, 6)
    return String.format(Locale.getDefault(), "%.2f %s", size.toDouble() / divisors[index], units[index])
}

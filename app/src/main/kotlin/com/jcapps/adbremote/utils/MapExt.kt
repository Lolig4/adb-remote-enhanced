package com.jcapps.adbremote.utils

/** Swaps keys and values. */
// obf: AbstractC0104.m1732 — RECON-UNSURE: function/file name
fun <K, V> Map<K, V>.reversed(): Map<V, K> = entries.associate { (key, value) -> value to key }

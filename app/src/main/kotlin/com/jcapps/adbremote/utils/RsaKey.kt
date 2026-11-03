package com.jcapps.adbremote.utils

import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * ADB RSA key helpers (app top-level functions R8-merged into helper class `eb` / inlined into
 * MainViewModel.connectSocket). RECON-UNSURE: file and function names.
 */

/** Reads the PEM text of the adb private key, or null when it cannot be read. */
// (inlined into MainViewModel.m1892; leftover parameter check "privateKeyFile")
fun readPrivateKey(privateKeyFile: File): String? = try {
    privateKeyFile.reader(Charsets.UTF_8).use { it.readText() }
} catch (e: Exception) {
    null
}

/** Decodes a PKCS#8 PEM private key ("-----BEGIN PRIVATE KEY-----") with the BC provider. */
// obf: eb.m1179
@OptIn(ExperimentalEncodingApi::class)
fun parsePrivateKey(pem: String): PrivateKey? {
    val base64 = pem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\\s+".toRegex(), "")
    val bytes = Base64.decode(base64)
    return KeyFactory.getInstance("RSA", "BC").generatePrivate(PKCS8EncodedKeySpec(bytes))
}

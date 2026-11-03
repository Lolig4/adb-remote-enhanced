package com.jcapps.adbremote.utils

import android.security.keystore.KeyGenParameterSpec
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256/GCM encryption with a key kept in the AndroidKeyStore under alias "ADBRemote". */
object SecureStorage {
    // obf: C3491

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "ADBRemote"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** Base64(iv + ciphertext). */
    // obf: m6020
    fun encrypt(input: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.DEFAULT)
    }

    /** Inverse of [encrypt]; null on any failure. */
    // obf: m6021
    fun decrypt(encryptedInput: String): String? {
        return try {
            val decoded = Base64.decode(encryptedInput, Base64.DEFAULT)
            val iv = decoded.copyOf(12)
            val encrypted = decoded.copyOfRange(12, decoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    // obf: m6022
    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE)
        keyStore.load(null)
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        entry?.secretKey?.let { return it }
        val keyGenerator = KeyGenerator.getInstance("AES", KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            3, // KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes("GCM")
            .setEncryptionPaddings("NoPadding")
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}

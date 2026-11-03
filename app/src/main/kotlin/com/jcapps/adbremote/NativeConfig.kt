package com.jcapps.adbremote

/**
 * Reconstruction of the values that the original app hid in `libapp.so` and read through 21
 * `external`/`native` methods on the Application class (obf `C0104`, methods `m787`..`m807`).
 *
 * Variant 1 of the rebuild: the native library is dropped (`System.loadLibrary("app")` and the
 * `external` declarations are gone), and the values — recovered by emulating `libapp.so` — live
 * here as plain Kotlin. These values are not secret-by-design: they shipped verbatim in every APK.
 * Dropping the library also removes the native `JNI_OnLoad` integrity/anti-tamper check.
 *
 * Mapping of the original native methods is in reco/data/native_method_map.tsv.
 * Each member below is annotated with its original `mNNN`. Replace `C0104.mNNN(...)` call sites
 * with the matching member here.
 */
object NativeConfig {

    // ---- support / social links (originally C2120 "Constants") ----
    const val telegramDeepLink = "tg://resolve?domain=adbRemote"              // obf: m805
    const val telegramUrl = "https://t.me/adbRemote"                          // obf: m796
    const val facebookPageDeepLink = "fb://page/425618483963397"              // obf: m791
    const val facebookPageUrl = "https://www.facebook.com/adbRemote"          // obf: m800
    const val facebookProfileDeepLink = "fb://profile/100012183557720"        // obf: m789
    const val facebookProfileUrl = "https://www.facebook.com/kaungkhantkyawprofile" // obf: m795
    const val translationsUrl = "https://github.com/kaungkhantjc/adb-remote-translations" // obf: m794

    // ---- website mirrors (originally C0416 "ApiClient"; the premium backend m792/m793/m799 was removed) ----
    /** Website mirrors (Constants.websites). obf: m802 (returned String[]). */
    val apiFallbackUrls = arrayOf(
        "https://adbremote.netlify.app",
        "https://adbremote.onrender.com",
        "https://adbremote.web.app",
        "https://adbremote.vercel.app",
        "https://adbremote.pages.dev",
    )
    /**
     * obf: m797 — the `token` header the app sends on every request to the on-device server
     * (http://<ip>:3125/…). RESOLVED by JNI-traced emulation: m797 returns the per-install
     * `adb_server_token` that m798 reads from the default SharedPreferences and caches in a native
     * global. It is NOT a baked-in constant; it is the same random UUID the server was started with
     * (see ServerInstaller / AdbService, m798). Empty string when the pref is unset.
     */
    fun adbServerToken(context: android.content.Context): String =
        serverTokenPrefs(context).getString("adb_server_token", "") ?: ""

    /**
     * The preferences file m798 reads the token from: decrypt(serverTokenAesKeyBase64) + "_preferences".
     * That is the default-preferences file only while the package is com.jcapps.adbremote; m797 returns
     * what m798 read, so both use this file (keeps header and server token equal for other app ids).
     */
    private fun serverTokenPrefs(context: android.content.Context): android.content.SharedPreferences {
        val packageName = aesEcb(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.Cipher::class.java.name,
            24,
            android.util.Base64.decode(serverTokenAesKeyBase64, android.util.Base64.DEFAULT),
        )?.let { String(it) } ?: ""
        return context.getSharedPreferences(packageName + "_preferences", 0)
    }

    /** Zero-arg form matching the original native m797(): uses the process-wide app context. */
    val apiInterceptorToken: String
        get() = adbServerToken(AdbRemoteApp.appContext)

    // ---- on-device server / crypto ----
    /** Main class inside assets/server_v1.1.0.jar, launched via app_process. */
    const val serverMainClass = "com.jcapps.adbserver.ADBServer"
    /**
     * Base64 AES/ECB/PKCS5 ciphertext used inside m798 (NOT a key): decrypted with the key
     * `Cipher::class.java.name` ("javax.crypto.Cipher") right-padded with '0' to 24 bytes (AES-192) it yields
     * "com.jcapps.adbremote", the prefix of the default-preferences file name. obf: m798.
     */
    const val serverTokenAesKeyBase64 = "rSzXquw/O4FXRIsMG/rmz5bAOucbpJqQdB1pcjW+zTQ="
    const val serverTokenCipher = "AES/ECB/PKCS5Padding"
    /** 16-char key/IV (AES-128). obf: m803 (used in C0198). */
    const val aes128Key = "y7bK&0lvJh8p1qLU"

    // ---- virtual-HID templates sent to the on-device server (obf: m787/m790/m804 register,
    //      m807/m801/m788 report). %s are filled at runtime. ----
    const val hidRegisterKeyboard = """{"id":0,"command":"register","bus":"usb","descriptor":[5,1,9,6,161,1,5,7,25,0,41,255,21,0,37,255,149,2,117,8,129,0,192]}""" // obf: m787
    const val hidRegisterMouse = """{"id":1,"command":"register","bus":"usb","descriptor":[5,1,9,2,161,1,9,1,161,0,5,9,9,1,9,2,21,0,37,1,149,8,117,1,129,2,5,1,9,48,9,49,9,56,21,129,37,127,117,8,149,3,129,6,192,192]}""" // obf: m804
    const val hidRegisterConsumer = """{"id":2,"command":"register","bus":"usb","descriptor":[5,12,9,1,161,1,21,0,37,1,117,1,149,5,10,162,1,10,35,2,10,36,2,9,233,9,234,129,6,149,1,117,3,129,3,192]}""" // obf: m790
    const val hidReportKeyboard = """{"id":0,"command":"report","report":[%s,%s]}"""       // obf: m807
    const val hidReportMouse = """{"id":1,"command":"report","report":[%s,%s,%s,0]}"""      // obf: m801
    const val hidReportConsumer = """{"id":2,"command":"report","report":[%s]}"""           // obf: m788

    /**
     * Server launch argument: `<serverMainClass> "<Base64(AES/ECB/PKCS5(token))>"`.
     *
     * Recovered by emulating libapp.so (m798 @0x893fc, JNI calls traced):
     * 1. prefs = getSharedPreferences(decrypt(serverTokenAesKeyBase64) + "_preferences", 0)
     *    (= "com.jcapps.adbremote_preferences", the default preferences)
     * 2. token = prefs.getString("adb_server_token", null); when null a UUID is generated and
     *    stored with `edit().putString("adb_server_token", uuid).apply()`
     * 3. key = serverMainClass right-padded with '0' to 32 bytes (memset '0' + strncpy), "AES"
     * 4. Base64.encodeToString(AES/ECB/PKCS5Padding encrypt(token bytes), DEFAULT).trim()
     * The native code also calls UUID.randomUUID().toString() once more and discards it.
     * A JNI exception (ExceptionCheck/ExceptionClear) is swallowed by the native code.
     */
    // obf: m798
    fun serverAppClassNameWithEncryptedToken(context: android.content.Context): String? {
        val prefs = serverTokenPrefs(context)
        var token = prefs.getString("adb_server_token", null)
        if (token == null) {
            token = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("adb_server_token", token).apply()
        }
        val encrypted = aesEcb(javax.crypto.Cipher.ENCRYPT_MODE, serverMainClass, 32, token.toByteArray())
            ?: return null // RECON-UNSURE: native error path not traced (exception swallowed)
        val encoded = android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT).trim()
        return "$serverMainClass \"$encoded\""
    }

    /** AES/ECB/PKCS5Padding with [keyText] right-padded with '0' to [keySize] bytes; null on a Java exception. */
    private fun aesEcb(mode: Int, keyText: String, keySize: Int, data: ByteArray): ByteArray? = try {
        val key = keyText.take(keySize).padEnd(keySize, '0').toByteArray()
        val cipher = javax.crypto.Cipher.getInstance(serverTokenCipher)
        cipher.init(mode, javax.crypto.spec.SecretKeySpec(key, "AES"))
        cipher.doFinal(data)
    } catch (e: Exception) {
        null
    }
}

package com.jcapps.adbserver

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.koushikdutta.async.AsyncServer
import com.koushikdutta.async.http.server.AsyncHttpServer
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Entry point of the on-device server, started by the app over adb:
 * `app_process ... com.jcapps.adbserver.ADBServer <encryptedToken>`.
 *
 * Starts an AndroidAsync HTTP server on port [PORT] (3125) whose endpoints all require the
 * `token` header (see [BaseHandler]).
 */
// obf: ADBServer (class name is kept by R8 and is also the AES key material, see decryptToken)
object ADBServer {

    /** obf: AbstractC0077.f168 = `(int) Math.pow(5.0, 5)` = 3125. */
    const val PORT: Int = 3125

    @JvmStatic
    fun main(args: Array<String>) {
        // obf: C0191(0).mo100() with System.err muted (hidden-API reflection warnings)
        val originalErr = System.err
        System.setErr(PrintStream(ByteArrayOutputStream()))
        val context: Context? = Workarounds.apply()
        System.setErr(originalErr)
        // Never null in practice (FakeContext is a singleton); kept from the original.
        if (context == null) {
            println("Failed to start ADBServer.\n")
            return
        }
        println("ADBServer started.\n")
        val encryptedToken = if (args.isNotEmpty()) args[0] else null

        val server = AsyncHttpServer() // obf: C0159 (+ mListeners f330, mListenCallback C0284 f329)
        // Without an argument the original decrypts "". RECON-UNSURE: whether doFinal() on empty
        // input throws (-> random UUID nobody knows, every request gets "Invalid token.") or
        // returns "" (-> token "", still never equal to a missing header) depends on the crypto
        // provider; either way behaviour is kept as in the original.
        val token = try {
            decryptToken(encryptedToken ?: "")
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
        val param = Param(Gson(), token) // obf: C0174(new C0344() = Gson(), token)

        // Route order exactly as in the original (AsyncHttpServerRouter get = m216, post = m219).
        server.post("/ftp/start", FtpHandler(param, FtpHandler.Action.START))          // C0171 + EnumC0177.f371
        server.post("/ftp/stop", FtpHandler(param, FtpHandler.Action.STOP))            // C0171 + EnumC0177.f370
        server.get("/ntp", NtpHandler(param, NtpHandler.Action.GET))                   // C0171 + EnumC0288.f614
        server.post("/ntp", NtpHandler(param, NtpHandler.Action.SET))                  // C0171 + EnumC0288.f613
        server.post("/ntp/delete", NtpHandler(param, NtpHandler.Action.DELETE))        // C0171 + EnumC0288.f616
        server.get("/supported-apps", SupportedAppsHandler(param, context))            // C0329
        server.get("/clipboard", ClipboardHandler(param, ClipboardHandler.Action.GET)) // C0171 + EnumC0060.f136
        server.post("/clipboard/text", ClipboardHandler(param, ClipboardHandler.Action.SET_TEXT))   // EnumC0060.f135
        server.post("/clipboard/image", ClipboardHandler(param, ClipboardHandler.Action.SET_IMAGE)) // EnumC0060.f138
        server.post("/intent", IntentHandler(param))                                   // C0118
        server.get("/ping", DeviceHandler(param, DeviceHandler.Mode.PING))             // C0242(1)
        server.get("/app-icon", AppsHandler(param, context, AppsHandler.Mode.ICON))    // C0243(0)
        server.get("/apps", AppsHandler(param, context, AppsHandler.Mode.APPS))        // C0243(2)
        server.get("/app", AppsHandler(param, context, AppsHandler.Mode.APP))          // C0243(1)
        server.get("/running-apps", AppsHandler(param, context, AppsHandler.Mode.RUNNING_APPS)) // C0243(3)
        server.get("/performance", PerformanceHandler(param, context))                 // C0240
        server.post("/run", DeviceHandler(param, DeviceHandler.Mode.RUN))              // C0242(2)
        server.get("/screenshot", DeviceHandler(param, DeviceHandler.Mode.SCREENSHOT)) // C0242(3)
        server.get("/device-info", DeviceHandler(param, DeviceHandler.Mode.DEVICE_INFO)) // C0242(0)

        // Rebuild addition: sideload feature (see ApkHandler)
        server.get("/apk/files", ApkHandler(param, context, ApkHandler.Mode.FILES))
        server.get("/apk/icon", ApkHandler(param, context, ApkHandler.Mode.ICON))
        server.get("/apk/installed", ApkHandler(param, context, ApkHandler.Mode.INSTALLED))
        server.post("/apk/upload", ApkHandler(param, context, ApkHandler.Mode.UPLOAD))
        server.post("/apk/install", ApkHandler(param, context, ApkHandler.Mode.INSTALL))

        // R8 inlined AsyncHttpServer.listen(AsyncServer, int) -> AsyncServer.listen(null, port,
        // mListenCallback) here: C0219.f443 = AsyncServer.getDefault(), RunnableC0168 = the listen
        // runnable (ServerSocketChannel bound to the wildcard address, OP_ACCEPT, mListeners.add).
        try {
            server.listen(AsyncServer.getDefault(), PORT)
        } catch (e: Exception) {
        }
        keepAlive()
    }

    /**
     * Decrypts the token passed as the first program argument:
     * Base64 -> AES/ECB/PKCS5Padding with the key = this class's name, padded with '0' to the AES
     * key length and cut to it ("com.jcapps.adbserver.ADBServer00", 32 bytes UTF-8).
     * The app encrypts with the same scheme, so the class name must not change.
     */
    // obf: AbstractC0353.m360
    @JvmStatic
    fun decryptToken(encrypted: String): String {
        val name = ADBServer::class.java.name
        val keyLength = if (name.length <= 32 && name.length <= 24) {
            if (name.length > 16) 24 else 16
        } else {
            32
        }
        val key = name.padEnd(keyLength, '0').take(keyLength)
        val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
        val decoded = Base64.decode(encrypted, Base64.DEFAULT)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted, Charsets.UTF_8)
    }

    /** Keeps the main thread (and so the process) alive; the server runs on AsyncServer's thread. */
    // obf: AbstractC0182.start
    // Fixed (rebuild): the original busy-waited (`while (true) {}`), burning one CPU core at 100%
    // for the whole lifetime of the server. Sleeping keeps the process alive just as well.
    @JvmStatic
    private fun keepAlive() {
        while (true) {
            Thread.sleep(Long.MAX_VALUE)
        }
    }
}

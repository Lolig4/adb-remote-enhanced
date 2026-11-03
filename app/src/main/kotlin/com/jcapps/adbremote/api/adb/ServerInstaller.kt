package com.jcapps.adbremote.api.adb

import android.content.Context
import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.api.adb.commands.AdbBinaryCommand
import com.jcapps.adbremote.model.CommandResult
import com.jcapps.adbremote.model.CommandStatus
import com.jcapps.adbremote.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.lingala.zip4j.io.inputstream.ZipInputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference

/**
 * Pushes the bundled on-device server (`assets/server/server_v1.1.0.jar`) to
 * `/data/local/tmp/.server_v1.1.0.jar` via the [AdbBinary], retrying on timeout.
 */
// obf: C1410
class ServerInstaller(
    context: Context,
    // obf: f6527 ("adb")
    private val adb: AdbBinary,
) {
    // obf: f6526
    private val contextRef: WeakReference<Context> = WeakReference(context)

    // obf: f6528 — active push coroutine
    var job: Job? = null

    /**
     * Extracts the bundled server jar (password-protected zip asset, key [NativeConfig.aes128Key])
     * to `files/server/server_v1.1.0.jar` (deleting stale `files/apps` and other server files) and
     * pushes it via [pushServer]. R8-inlined into MainViewModel's connect callback (C2097 case 3);
     * the coroutine body is C0198, the path callback C0753 default case. RECON-UNSURE name.
     */
    // obf: (inlined) C0198 + C0753 case 12
    fun install(serial: String, callback: (Boolean) -> Unit) {
        job = CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            val onServerFile: (String?) -> Unit = { path ->
                if (path == null) {
                    Logger.log("Failed to prepare adb server.")
                    callback(false)
                } else {
                    Logger.log("Making things ready ...")
                    pushServer(0, path, serial, callback)
                }
            }
            val context = contextRef.get() ?: return@launch
            val appsDir = File(context.filesDir, "apps")
            if (appsDir.isDirectory) {
                appsDir.deleteRecursively()
                appsDir.delete()
            }
            val serverDir = File(context.filesDir, "server")
            if (!serverDir.exists()) {
                serverDir.mkdir()
            }
            val serverFile = File(serverDir, "server_v1.1.0.jar")
            serverDir.listFiles()?.forEach { file ->
                if (file != serverFile) {
                    file.delete()
                }
            }
            if (serverFile.exists()) {
                onServerFile(serverFile.path)
            } else {
                val password = NativeConfig.aes128Key
                try {
                    context.assets.open("server_v1.1.0.jar").use { inputStream ->
                        val zipInputStream = ZipInputStream(inputStream, password.toCharArray())
                        while (true) {
                            val entry = zipInputStream.nextEntry ?: break
                            if (entry.fileName == "server_v1.1.0.jar") {
                                FileOutputStream(serverFile).use { zipInputStream.copyTo(it) }
                                break
                            }
                            // NOTE: original reports a failure for every non-matching entry.
                            onServerFile(null)
                        }
                        zipInputStream.close()
                        onServerFile(serverFile.path)
                    }
                } catch (e: Exception) {
                    onServerFile(null)
                }
            }
        }
    }

    /**
     * Pushes the server jar for [serial]; on command TIMEOUT retries up to 3 times (logging
     * "Still working on it ..."), otherwise reports success when the push exited 1 and its output
     * contains "1 file pushed".
     *
     * Command: `adb -s <serial> push <serverFilePath> /data/local/tmp/.server_v1.1.0.jar`
     */
    // obf: m3345
    fun pushServer(attempt: Int, serverFilePath: String, serial: String, callback: (Boolean) -> Unit) {
        adb.runCommands(
            listOf(AdbBinaryCommand.Command("adb -s $serial push $serverFilePath /data/local/tmp/.server_v1.1.0.jar")),
            3000L,
        ) { result ->
            if (result.type == CommandStatus.TIMEOUT) {
                if (attempt >= 3) {
                    callback(false)
                } else {
                    Logger.log("Still working on it ...")
                    pushServer(attempt + 1, serverFilePath, serial, callback)
                }
            } else {
                callback(
                    result.type == CommandStatus.OK && result.exitCode == 1 &&
                        result.output?.contains("1 file pushed") == true,
                )
            }
        }
    }

    companion object {
        // obf: f6524 — path inside assets
        val ASSET_SERVER_PATH: String = "server" + File.separator + "server_v1.1.0.jar"

        // obf: f6525 — 5^5 = 3125 (RECON-UNSURE: purpose; appears unused at the push site)
        const val SERVER_CONSTANT: Int = 3125

        // placeholder referenced by pushServer / CommandResult for the result mapping
        private val resultType: Class<CommandResult> = CommandResult::class.java
    }
}

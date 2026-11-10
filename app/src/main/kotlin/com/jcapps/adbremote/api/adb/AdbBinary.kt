package com.jcapps.adbremote.api.adb

import android.content.Context
import android.os.Build
import com.jcapps.adbremote.api.adb.commands.AdbBinaryCommand
import com.jcapps.adbremote.model.CommandResult
import com.jcapps.adbremote.model.CommandStatus
import com.jcapps.adbremote.model.ProcessStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.CancellationException

/**
 * Runs the bundled `adb` binary (`libfs.so`/`libfs64.so` shipped in the APK's nativeLibraryDir)
 * and the on-device server. HOME = filesDir, TMPDIR = cacheDir; the ADB key lives at
 * `filesDir/.android/adbkey`.
 */
// obf: C3193
class AdbBinary(context: Context) {

    // obf: f11378 / f11380 / f11384 — worker threads
    private var thread1: Thread? = null
    private var thread2: Thread? = null
    private var serverThread: Thread? = null

    // obf: f11379 — absolute path to the bundled adb binary
    private val adbBinaryPath: String

    // obf: f11381 — IO coroutine scope for runCommands
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    // obf: f11382 — currently running shell stream, if any
    private var currentStream: ProcessStream? = null

    // obf: f11383 — cacheDir path (TMPDIR)
    private val cacheDirPath: String

    // obf: f11385 — filesDir path (HOME)
    val filesDirPath: String

    // obf: f11387 — filesDir/.android
    private val androidDir: File

    // obf: f11386 — filesDir/.android/adbkey (RSA private key, PEM)
    val adbKeyFile: File

    init {
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        adbBinaryPath = if (Build.VERSION.SDK_INT >= 30) {
            if (nativeLibraryDir.contains("x86")) {
                nativeLibraryDir + File.separator + "libfs.so"
            } else {
                nativeLibraryDir + File.separator + "libfs64.so"
            }
        } else {
            nativeLibraryDir + File.separator + "libfs.so"
        }
        filesDirPath = context.filesDir.path
        cacheDirPath = context.cacheDir.path
        androidDir = File(filesDirPath, ".android")
        adbKeyFile = File(androidDir, "adbkey")
    }

    // obf: m5723 — read an InputStream fully to a String; null on error
    private fun readStream(inputStream: InputStream): String? = try {
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 8192).use { it.readText() }
    } catch (e: Exception) {
        null
    }

    // obf: m5724 — write a line to the shell, appending '\n' when missing
    @Throws(IOException::class)
    private fun writeLine(out: DataOutputStream, line: String) {
        val withNewline = if (line.endsWith("\n")) line else line + "\n"
        out.writeBytes(withNewline)
        out.flush()
    }

    // obf: m5725 — stop: close the current stream and interrupt all worker threads
    fun stop() {
        currentStream?.let { s ->
            try { s.stdIn.close() } catch (e: Exception) {}
            try { s.stdOut.close() } catch (e: Exception) {}
            try { s.stdError.close() } catch (e: Exception) {}
        }
        try { serverThread?.interrupt() } catch (e: Exception) {}
        try { thread1?.interrupt() } catch (e: Exception) {}
        try { thread2?.interrupt() } catch (e: Exception) {}
    }

    // obf: m5726 — start the on-device server (app_process) on a worker thread
    fun startServer(
        serial: String,
        serverAppClassNameWithEncryptedToken: String,
        connectionHistoryCallback: (String) -> Unit,
        onSuccess: () -> Unit,
    ) {
        stop()
        // obf: RunnableC0506 case 0
        val thread = Thread {
            try {
                val serverPath = ServerInstaller.DEVICE_SERVER_PATH
                val serverName = "." + ServerInstaller.SERVER_JAR
                val stream = openServerShell()
                currentStream = stream
                // (original null-checks stdIn here)
                stream.stdIn.let { stdIn ->
                    val command = "adb -s " + serial + " shell 'export CLASSPATH=" + serverPath +
                        ";app_process " + serverName + ' ' + serverAppClassNameWithEncryptedToken + '\''
                    // obf: AbstractC2860.f10440 / C0656.m2454("TOKEN: …") — app debug tree logger (no-op, unregistered)
                    writeLine(stdIn, command)
                }
                var serverStarted = false
                // stdout watcher: report "ADBServer started" once
                thread1 = Thread {
                    val reader = currentStream?.stdOut?.let { InputStreamReader(it, Charsets.UTF_8) }
                    val buffer = CharArray(8192)
                    try {
                        var count = reader?.read(buffer) ?: 0
                        while (count >= 0) {
                            val output = StringBuilder().append(buffer, 0, count)
                            if (!serverStarted && output.contains("ADBServer started", true)) {
                                serverStarted = true
                                connectionHistoryCallback("ADB server started.")
                                onSuccess()
                                break
                            }
                            count = reader?.read(buffer) ?: 0
                        }
                    } catch (e: Exception) {
                    }
                }.also { it.start() }
                // obf: RunnableC0506 case 1 — stderr watcher: restart when the server got killed
                thread2 = Thread {
                    val reader = currentStream?.stdError?.let { InputStreamReader(it, Charsets.UTF_8) }
                    val buffer = CharArray(8192)
                    try {
                        var count = reader?.read(buffer) ?: 0
                        while (count >= 0) {
                            val output = StringBuilder().append(buffer, 0, count)
                            if (output.contains("Killed", true)) {
                                connectionHistoryCallback("ADB server killed. restarting server ...")
                                stop()
                                startServer(serial, serverAppClassNameWithEncryptedToken, connectionHistoryCallback, onSuccess)
                                break
                            }
                            connectionHistoryCallback("ADB server interrupted. $output")
                            count = reader?.read(buffer) ?: 0
                        }
                    } catch (e: Exception) {
                    }
                }.also { it.start() }
            } catch (e: Exception) {
            }
        }
        serverThread = thread
        thread.start()
    }

    // obf: m5727 — open `adb -s <serial> shell`
    @Throws(IOException::class)
    fun openShell(serial: String): ProcessStream {
        val builder = ProcessBuilder(adbBinaryPath, "-s", serial, "shell")
        builder.directory(File(cacheDirPath))
        builder.environment().apply {
            put("HOME", filesDirPath)
            put("TMPDIR", cacheDirPath)
        }
        val process = builder.start()
        val stdIn = DataOutputStream(process.outputStream)
        return ProcessStream(process, stdIn, process.inputStream, process.errorStream)
    }

    // obf: m5728 — open an `sh` with an `alias adb="<binary>"` and ADB server port 5037
    @Throws(IOException::class)
    fun openServerShell(): ProcessStream {
        File(filesDirPath).takeIf { !it.exists() }?.mkdirs()
        val cacheDir = File(cacheDirPath)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val builder = ProcessBuilder("sh")
        builder.environment().apply {
            put("HOME", filesDirPath)
            put("TMPDIR", cacheDirPath)
            put("ANDROID_ADB_SERVER_PORT", "5037")
        }
        builder.directory(cacheDir)
        val process = builder.start()
        val stdIn = DataOutputStream(process.outputStream)
        writeLine(stdIn, "alias adb=\"$adbBinaryPath\"")
        return ProcessStream(process, stdIn, process.inputStream, process.errorStream)
    }

    // obf: m5729 — run a scripted shell session with a timeout, delivering the result on Main
    fun runCommands(
        commands: List<AdbBinaryCommand>,
        timeoutMillis: Long,
        callback: (CommandResult) -> Unit,
    ): Job = scope.launch {
        // obf: C1856
        try {
            // obf: C4049 — make sure filesDir/.android/adbkey exists
            async {
                if (androidDir.exists() && !androidDir.isDirectory) androidDir.delete()
                if (!androidDir.exists()) androidDir.mkdirs()
                if (!adbKeyFile.exists()) {
                    val keygen = openServerShell()
                    writeLine(keygen.stdIn, "adb keygen " + adbKeyFile.path)
                    keygen.stdIn.close()
                    keygen.stdOut.close()
                    keygen.stdError.close()
                    keygen.process.destroy()
                }
            }.await()
            val stream = openServerShell()
            val stdIn = stream.stdIn
            for (command in commands) {
                when (command) {
                    is AdbBinaryCommand.Expect -> {
                        val reader = InputStreamReader(stream.stdOut, Charsets.UTF_8)
                        val buffer = CharArray(8192)
                        try {
                            var count = reader.read(buffer)
                            while (count >= 0) {
                                val output = StringBuilder().append(buffer, 0, count)
                                if (output.contains(command.output, true)) break
                                count = reader.read(buffer)
                            }
                        } catch (e: Exception) {
                        }
                    }
                    is AdbBinaryCommand.Command -> writeLine(stdIn, command.command)
                }
            }
            stdIn.close()
            // obf: C2348 / C0439
            val output = async { readStream(stream.stdOut) }
            val error = async { readStream(stream.stdError) }
            // obf: C4002
            val result = withTimeoutOrNull(timeoutMillis) {
                CommandResult(CommandStatus.OK, 1, output.await(), error.await())
            } ?: CommandResult(CommandStatus.TIMEOUT)
            stream.stdOut.close()
            stream.stdError.close()
            stream.process.destroy()
            // obf: C2685
            withContext(Dispatchers.Main) { callback(result) }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                // obf: C3567
                withContext(Dispatchers.Main) { callback(CommandResult(CommandStatus.ERROR, error = e.message)) }
            }
        }
    }
}

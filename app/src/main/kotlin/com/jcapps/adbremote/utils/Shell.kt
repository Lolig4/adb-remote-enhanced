package com.jcapps.adbremote.utils

import com.jcapps.adbremote.model.ShellResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Thrown into [ShellResult.e] when [Shell.exec] timed out. RECON-UNSURE name. */
// obf: C1864 (no-arg ctor variant of the merged exception class)
class ShellTimeoutException : Exception()

/** Runs local `sh -c` commands of the app process. */
object Shell {
    // obf: C3025 (singleton f10918)

    // obf: m5430 (body = suspend lambda C2361)
    suspend fun exec(command: String, timeoutMillis: Long): ShellResult {
        return withTimeoutOrNull(timeoutMillis) {
            try {
                val process = ProcessBuilder("sh", "-c", command).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val error = process.errorStream.bufferedReader().use { it.readText() }
                ShellResult(process.waitFor(), output, error)
            } catch (e: Exception) {
                ShellResult(-1, e = e)
            }
        } ?: ShellResult(-1, e = ShellTimeoutException())
    }
}

/**
 * Kills a running local `adb` server process (`ps | grep -iw adb` -> `kill <pid>`), then invokes
 * [callback] on the main thread. R8-inlined at its call sites (MainActivity.startConnection,
 * MainViewModel.disconnectAll, ServerService.onDestroy). RECON-UNSURE name.
 */
// obf: C3583
fun killAdbServer(callback: () -> Unit): Job =
    CoroutineScope(Dispatchers.IO).launch {
        val output = Shell.exec("ps | grep -iw adb", 2000L).output
        val pid = output?.split("\\s+".toRegex())?.getOrNull(1)
        if (pid != null) {
            Shell.exec("kill $pid", 2000L)
        }
        withContext(Dispatchers.Main) {
            // obf: C1278
            callback()
        }
    }

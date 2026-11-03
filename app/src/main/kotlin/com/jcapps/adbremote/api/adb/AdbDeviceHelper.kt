package com.jcapps.adbremote.api.adb

import com.jcapps.adbremote.api.adb.commands.BinaryCommand
import com.jcapps.adbremote.model.ProcessStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Drives an `adb -s <serial> shell` process via [AdbBinary] (ADB-binary control mode): queued
 * [BinaryCommand]s are written as `input keyevent` / `input text` lines.
 *
 * NOTE: in the R8 artifact this is tag 1 (`f13433 == 1`) of a 4-way merged class `C3817`
 * (the other tags are appcompat `AppCompatDrawableManager`, okhttp `Dispatcher` and okhttp
 * `Request`/`Request.Builder` — all library, not ported). Only the app members are reconstructed.
 */
// obf: C3817 (tag 1)
class AdbDeviceHelper(
    // obf: f13432 ("adb")
    private val adb: AdbBinary,
    // obf: f13436 ("getDeviceSerial") — C2247 case 13: `{ device.value?.serial }`
    private val getDeviceSerial: () -> String?,
) {
    // obf: f13435 — IO coroutine scope (Dispatchers.IO + SupervisorJob)
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // obf: f13438 — unbounded Channel used to feed the write loop
    private val commands: Channel<BinaryCommand> = Channel(Channel.UNLIMITED)

    // obf: f13437 — the running shell ProcessStream
    private var processStream: ProcessStream? = null

    // obf: f13434 — the running write coroutine
    private var job: Job? = null

    // obf: m6401 — write a line to the shell stream (append '\n' when missing)
    private fun writeLine(line: String) {
        val out = processStream?.stdIn ?: return
        val withNewline = line.takeIf { it.endsWith("\n") } ?: line.plus("\n")
        out.writeBytes(withNewline)
        out.flush()
    }

    /** Quotes [text] for the device shell (inlined into the C2045 loop). */
    private fun quote(text: String): String = "'" + text.replace("'", "'\"'\"'") + '\''

    /** Queues [command] for the shell loop. */
    // (inlined at MainActivity C1204.m829: `((BufferedChannel) c3817.f13438).trySend(command)`)
    fun send(command: BinaryCommand) {
        commands.trySend(command)
    }

    /** (Re)starts the shell loop. */
    // (inlined at MainActivity C1204.m831 / C2497: `c3817.m6405(); c3817.f13434 = launch(scope) { C2045 }`)
    fun start() {
        stop()
        job = scope.launch {
            // obf: C2045
            processStream = runCatching { adb.openShell(getDeviceSerial() as String) }.getOrNull()
            if (processStream != null) {
                for (command in commands) {
                    when (command) {
                        is BinaryCommand.KeyEvent -> writeLine("input keyevent " + command.keyCode)
                        is BinaryCommand.Text -> writeLine("input text " + quote(command.text))
                    }
                }
            }
        }
    }

    // obf: m6405 — close the shell stream and cancel the read coroutine
    fun stop() {
        runCatching {
            processStream?.apply {
                try { stdIn.close() } catch (e: Exception) {}
                try { stdOut.close() } catch (e: Exception) {}
                try { stdError.close() } catch (e: Exception) {}
            }
        }
        job?.cancel()
    }
}

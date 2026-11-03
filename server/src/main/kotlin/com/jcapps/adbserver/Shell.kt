package com.jcapps.adbserver

import java.io.DataOutputStream

/** Runs shell commands (the server runs as the `shell` user via `app_process`). */
object Shell {

    /**
     * Starts `sh`, writes [command] followed by `"\n exit \n"` to its stdin and waits for it.
     * With [readOutput], stdout and stderr are read on two background threads.
     * Any exception yields `CommandResult(-1, "", "")`.
     */
    // obf: C0335.m329
    fun run(command: String, readOutput: Boolean): CommandResult {
        var process: Process? = null // obf: c0321 (Ref.ObjectRef)
        try {
            val proc = Runtime.getRuntime().exec("sh")
            process = proc
            DataOutputStream(proc.outputStream).use { stdin ->
                stdin.write(command.toByteArray(Charsets.UTF_8))
                stdin.writeBytes("\n exit \n")
                stdin.close()
            }
            var output = "" // obf: c0322
            var error = ""  // obf: c0323
            var outputThread: Thread? = null // obf: c0324
            var errorThread: Thread? = null  // obf: c0325
            if (readOutput) {
                // obf: lambda ဘဒဝသဗငက case 0 -> C0287 mode 1
                outputThread = Thread {
                    MiscUtils.ignoreExceptions {
                        output = proc.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                }.also { it.start() }
                // obf: lambda ဘဒဝသဗငက case 1 -> C0287 mode 2
                errorThread = Thread {
                    MiscUtils.ignoreExceptions {
                        error = proc.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                }.also { it.start() }
            }
            // BUG(original): with readOutput == false nobody drains stdout/stderr, so a command that
            // writes more than the pipe buffer blocks forever and waitFor() never returns.
            val exitCode = proc.waitFor()
            if (readOutput) {
                // BUG(original): the reader threads are interrupted, not joined. interrupt() does
                // not stop a blocking read, and nothing waits for them, so output/error are read
                // racily: if a reader has not finished yet, the result below carries "" (or a stale
                // value) for that stream. Also the writes from the reader threads are not volatile.
                MiscUtils.ignoreExceptions { outputThread?.interrupt() } // obf: C0110 mode 2
                MiscUtils.ignoreExceptions { errorThread?.interrupt() }  // obf: C0110 mode 3
            }
            // BUG(original): destroy() also closes the pipes, which can cut off a reader that is
            // still running (see above).
            MiscUtils.ignoreExceptions { proc.destroy() } // obf: C0110 mode 4
            output = output.trim()
            error = error.trim()
            if (output == "null") {
                output = ""
            }
            return CommandResult(exitCode, output, error)
        } catch (e: Exception) {
            MiscUtils.ignoreExceptions { process?.destroy() } // obf: C0110 mode 5
            return CommandResult(-1, "", "")
        }
    }
}

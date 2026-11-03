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
            // Fixed (rebuild): stdout/stderr are always drained (a chatty command used to block
            // forever when readOutput was false) and the readers are joined before the result is
            // built (the original interrupted them, so output/error came back empty or cut off).
            // readOutput only decides whether the text is returned.
            var output = "" // obf: c0322
            var error = ""  // obf: c0323
            // obf: lambda ဘဒဝသဗငက case 0 -> C0287 mode 1
            val outputThread = Thread {
                MiscUtils.ignoreExceptions { output = proc.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
            }.also { it.start() }
            // obf: lambda ဘဒဝသဗငက case 1 -> C0287 mode 2
            val errorThread = Thread {
                MiscUtils.ignoreExceptions { error = proc.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
            }.also { it.start() }
            val exitCode = proc.waitFor()
            outputThread.join()
            errorThread.join()
            MiscUtils.ignoreExceptions { proc.destroy() } // obf: C0110 mode 4
            if (!readOutput) {
                output = ""
                error = ""
            }
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

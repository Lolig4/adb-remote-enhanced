package com.jcapps.adbremote.api.remote

import com.jcapps.adbremote.api.adb.commands.ApiCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Serial executor of [ApiCommand]s against the on-device ADB-server API (the API-mode
 * counterpart of the ADB command channel in MainViewModel). Owned by AppViewModel, driven by
 * MainActivity (`send` on user input, `start` when the API connection mode is selected).
 *
 * NOTE: in the R8 artifact this is one constructor variant (no-arg `C2916()`) of the heavily
 * merged class `C2916`; only the app members are reconstructed. RECON-UNSURE: class/member names.
 */
class ApiCommandExecutor(
    // obf: f10595 ("api") — provider of the current ApiService (AppViewModel.getApi)
    private val api: () -> ApiService,
) {
    // obf: C2916 (no-arg ctor variant)

    // obf: f10598
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // obf: f10597
    private val commands = Channel<ApiCommand>(Channel.CONFLATED)

    // obf: f10596
    var job: Job? = null

    /** Queues [command] (conflated: only the latest pending command is kept). */
    // (inlined at MainActivity C1204: `((BufferedChannel) c2916.f10597).trySend(command)`)
    fun send(command: ApiCommand) {
        commands.trySend(command)
    }

    /** Starts the consumer loop. */
    // (inlined at MainActivity C1204.m831: `c2916.f10596 = launch(scope) { C3593 }`)
    fun start() {
        job = scope.launch {
            // obf: C3593 (jadx failed; reconstructed from smali-dumps/C3593)
            for (command in commands) {
                when (command) {
                    is ApiCommand.KeyEvent -> run("input keyevent " + command.keyCode)
                    is ApiCommand.Text -> run("input text " + quote(command.text))
                }
            }
        }
    }

    // (inlined in C3593: `"'" + text.replace("'", "'\"'\"'") + '\''`)
    // RECON-UNSURE: may have been a private helper of the executor; R8 inlined it.
    private fun quote(text: String): String = "'" + text.replace("'", "'\"'\"'") + '\''

    /** Runs [command] through the server's `run` endpoint; failures are swallowed. */
    // obf: m5307
    private suspend fun run(command: String) {
        runCatching { api().run(command) }
    }
}

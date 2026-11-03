package com.jcapps.adbremote.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-app log shown by LogsActivity: list of (index, "[h:mm a] message"), capped at 100000. */
object Logger {
    // obf: AbstractC1309

    // obf: f6231 (not private: LogsActivity "clear" assigns it directly)
    val _logs = MutableStateFlow<List<Pair<Int, String>>>(emptyList())

    // obf: f6234
    val logs: StateFlow<List<Pair<Int, String>>> = _logs.asStateFlow()

    // obf: f6232
    var isEnabled: Boolean = true

    // obf: f6233
    private val dateFormat = SimpleDateFormat("h:mm a", Locale.ENGLISH)

    // obf: m3254
    fun log(log: String) {
        if (isEnabled) {
            _logs.update { logs ->
                val size = logs.size
                if (size <= 100000) {
                    logs + Pair(size, "[" + dateFormat.format(Date()) + "] " + log)
                } else {
                    logs
                }
            }
        }
    }
}

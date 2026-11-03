package com.jcapps.adbremote.api.adb.hid

import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.api.adb.stream.AdbStream

/**
 * Writes virtual-HID keyboard reports over an [AdbStream] (the `hid -` shell stream).
 *
 * The report JSON template is [NativeConfig.hidReportKeyboard] (originally decrypted from
 * `libapp.so` via `C0104.m807()` and base64-decoded).
 */
// obf: u4 (claimed core-adb)
class HidKeyboard(
    // obf: f2358
    private val stream: AdbStream,
) : AutoCloseable {

    // obf: f2359 — was C0450.m2180(C0104.m807()).string(UTF_8)
    private val reportTemplate: String? = NativeConfig.hidReportKeyboard

    override fun close() {
        stream.close()
    }

    // obf: m1486
    fun formatReport(first: Int, second: Int): String {
        val template = reportTemplate ?: return ""
        return String.format(template, first.toString(), second.toString())
    }

    // obf: m1487
    fun sendKey(keyCode: Int, modifier: Int?) {
        val sb = StringBuilder()
        if (modifier != null) {
            sb.append(formatReport(modifier, 0))
            sb.append(formatReport(modifier, keyCode))
            sb.append(formatReport(modifier, 0))
            sb.append(formatReport(0, 0))
        } else {
            sb.append(formatReport(keyCode, 0))
            sb.append(formatReport(0, 0))
        }
        stream.write(sb.toString())
    }
}

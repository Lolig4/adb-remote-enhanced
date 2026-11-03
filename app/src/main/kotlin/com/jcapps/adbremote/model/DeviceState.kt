package com.jcapps.adbremote.model

/**
 * State of a single ADB device as reported by `adb devices`.
 *
 * Original class name is `DeviceInfo`; renamed to [DeviceState] to avoid clashing with the
 * telemetry [DeviceInfo]. [toString] keeps the original runtime text `DeviceInfo(...)`.
 */
// obf: C2848
data class DeviceState(
    // obf: f10409
    val serial: String,
    // obf: f10408
    val state: DeviceStatus,
    // obf: f10410
    val model: String?,
) {
    override fun toString(): String = "DeviceInfo(serial=$serial, state=$state, model=$model)"
}

package com.jcapps.adbremote.ui.main

import com.jcapps.adbremote.api.adb.ConnectionMethod
import com.jcapps.adbremote.api.adb.commands.ApiCommand
import com.jcapps.adbremote.api.adb.commands.BinaryCommand
import com.jcapps.adbremote.dialogs.home.DialogExtraButtons
import com.jcapps.adbremote.dialogs.home.DialogKeyboard
import com.jcapps.adbremote.dialogs.home.DialogNumpad
import com.jcapps.adbremote.dialogs.home.DialogTrackpad
import com.jcapps.adbremote.model.PhysicalKey
import com.jcapps.adbremote.model.VirtualButton
import com.jcapps.adbremote.model.VirtualKey
import com.jcapps.adbremote.services.AdbService
import com.jcapps.adbremote.ui.MainActivity
import com.jcapps.adbremote.utils.requestAppReviewIfRecommended

/**
 * MainActivity's handler for virtual/physical button actions, set as [ButtonManager.listener].
 *
 * RECON-UNSURE: R8 merged this app class with androidx SplashScreen.Impl (C1880; its `install`
 * mo4055 / `setPostSplashScreenTheme` m4057 are library code and NOT ported — MainActivity uses
 * `installSplashScreen()`) and inlined its click method into ButtonManager.m3233. Original
 * name/shape unknown; "BackPressHandler" is the registry name (OPEN_ITEMS proposes MainButtonListener).
 */
class BackPressHandler(
    // obf: f7745
    val activity: MainActivity,
) {
    // obf: C1880 (app part)

    /** Picks the command for the current control mode (SOCKET -> HID [socket], others -> Android keyCode). */
    private fun command(socket: Any, keyCode: Int): Any = when (activity.connectionMethod) {
        ConnectionMethod.SOCKET -> socket
        ConnectionMethod.BINARY -> BinaryCommand.KeyEvent(keyCode)
        ConnectionMethod.SERVER -> ApiCommand.KeyEvent(keyCode)
    }

    /** Sends BACK in the current control mode. */
    // obf: m4056
    fun onBackPressed() {
        activity.sendCommand(command(PhysicalKey(4), 4))
    }

    /** Body inlined into ButtonManager.m3233 (switch over VirtualButton.ordinal). */
    // obf: ButtonManager.m3233 (inlined)
    fun onButtonClicked(virtualButton: VirtualButton) {
        when (virtualButton) {
            VirtualButton.MUTE -> activity.sendCommand(command(VirtualKey(127, null), 164))
            VirtualButton.POWER -> activity.sendCommand(command(VirtualKey(102, null), 26))
            VirtualButton.KEYBOARD -> {
                val dialog = DialogKeyboard()
                // obf: C1433 case 11
                dialog.onKeyEvent = { activity.sendCommand(it) }
                // obf: C1433 case 12
                dialog.onAdbKeyEvent = { keyCode -> activity.mainViewModel.sendKeyEvent(keyCode) }
                // obf: C0808 case 11
                dialog.onDismiss = {
                    if (AdbService.isRunning) {
                        requestAppReviewIfRecommended(activity)
                    }
                }
                dialog.show(activity.supportFragmentManager, DialogKeyboard::class.java.name)
            }
            VirtualButton.TRACKPAD -> {
                val sensitivity = activity.prefs.getFloat("trackpad_sensitivity", 0.0f)
                val mouse = activity.mainViewModel.adbClient?.mouse
                if (mouse != null) {
                    mouse.sensitivity = Math.pow(2.0, (sensitivity.toInt() / 40.0f).toDouble()).toFloat()
                }
                val dialog = DialogTrackpad()
                // obf: C1433 case 10
                dialog.onEvent = { activity.mainViewModel.enqueue(it) }
                // obf: C0808 case 10
                dialog.onDismiss = {
                    if (AdbService.isRunning) {
                        requestAppReviewIfRecommended(activity)
                    }
                }
                dialog.show(activity.supportFragmentManager, DialogTrackpad::class.java.name)
            }
            VirtualButton.NUMPAD -> {
                val dialog = DialogNumpad()
                // obf: C1433 case 13
                dialog.onKeyEvent = { activity.sendCommand(it) }
                dialog.show(activity.supportFragmentManager, DialogNumpad::class.java.name)
            }
            VirtualButton.BACK -> onBackPressed()
            VirtualButton.HOME -> activity.sendCommand(command(PhysicalKey(2), 3))
            VirtualButton.VOLUME_DOWN -> activity.sendCommand(command(PhysicalKey(16), 25))
            VirtualButton.VOLUME_UP -> activity.sendCommand(command(PhysicalKey(8), 24))
            VirtualButton.MENU -> activity.sendCommand(command(VirtualKey(101, null), 82))
            VirtualButton.EXTRA_BUTTONS -> {
                val dialog = DialogExtraButtons(activity.buttonManager, activity.vibrator)
                // obf: C1433 case 9
                dialog.onKeyEvent = { activity.sendCommand(it) }
                dialog.show(activity.supportFragmentManager, DialogExtraButtons::class.java.name)
            }
            VirtualButton.PREVIOUS -> activity.sendCommand(command(VirtualKey(234, null), 88))
            VirtualButton.NEXT -> activity.sendCommand(command(VirtualKey(235, null), 87))
            VirtualButton.TAB -> activity.sendCommand(command(VirtualKey(43, null), 61))
            VirtualButton.PLAY_PAUSE -> activity.sendCommand(command(VirtualKey(232, null), 85))
            VirtualButton.STOP -> activity.sendCommand(command(VirtualKey(233, null), 86))
            VirtualButton.SCREENSHOT -> activity.sendCommand(command(VirtualKey(70, null), 120))
            VirtualButton.F1 -> activity.sendCommand(command(VirtualKey(58, null), 131))
            VirtualButton.F2 -> activity.sendCommand(command(VirtualKey(59, null), 132))
            VirtualButton.F3 -> activity.sendCommand(command(VirtualKey(60, null), 133))
            VirtualButton.F4 -> activity.sendCommand(command(VirtualKey(61, null), 134))
            VirtualButton.F5 -> activity.sendCommand(command(VirtualKey(62, null), 135))
            VirtualButton.F6 -> activity.sendCommand(command(VirtualKey(63, null), 136))
            VirtualButton.F7 -> activity.sendCommand(command(VirtualKey(64, null), 137))
            VirtualButton.F8 -> activity.sendCommand(command(VirtualKey(65, null), 138))
            VirtualButton.F9 -> activity.sendCommand(command(VirtualKey(66, null), 139))
            VirtualButton.F10 -> activity.sendCommand(command(VirtualKey(67, null), 140))
            VirtualButton.F11 -> activity.sendCommand(command(VirtualKey(68, null), 141))
            VirtualButton.F12 -> activity.sendCommand(command(VirtualKey(69, null), 142))
        }
    }
}

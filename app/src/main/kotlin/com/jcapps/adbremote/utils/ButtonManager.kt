package com.jcapps.adbremote.utils

import android.content.Context
import androidx.appcompat.widget.TooltipCompat
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.jcapps.adbremote.model.VirtualButton
import com.jcapps.adbremote.prefs.JsonPrefs
import com.jcapps.adbremote.ui.main.BackPressHandler

/**
 * Binds the 12 configurable home-screen buttons (prefs `btn_rowX_Y`) to [VirtualButton]s and
 * dispatches presses (haptics, continuous press) to [listener] — or, in edit mode, reports the
 * pressed slot key to [onButtonSelected].
 */
class ButtonManager(
    // obf: f6178
    private val context: Context,
    // obf: f6177
    private val vibrator: HapticFeedback?,
    // obf: f6182
    private val continuousPressHandler: ContinuousPressHandler?,
) {
    // obf: C1289
    // obf: f6183
    val buttons: ArrayList<VirtualButton> = ArrayList()

    // obf: f6179 (C2247 case 3) — not private: ControlButtonsActivity resets the slots through it
    val prefs by lazy {
        JsonPrefs(PreferenceManager.getDefaultSharedPreferences(context))
    }

    // obf: f6180 — RECON-UNSURE name: slot-key callback (C2192 lambda) used while customizing buttons
    var onButtonSelected: ((String) -> Unit)? = null

    // obf: f6184 — RECON-UNSURE: MainActivity's listener (R8 merged it with SplashScreen.Impl, C1880)
    var listener: BackPressHandler? = null

    // obf: f6181 — not private: ControlButtonsActivity iterates its keys on reset
    val defaultButtons: Map<String, VirtualButton> = mapOf(
        BTN_ROW1_1 to VirtualButton.MUTE,
        BTN_ROW1_2 to VirtualButton.POWER,
        BTN_ROW2_1 to VirtualButton.KEYBOARD,
        BTN_ROW2_2 to VirtualButton.TRACKPAD,
        BTN_ROW3_1 to VirtualButton.BACK,
        BTN_ROW3_2 to VirtualButton.HOME,
        BTN_ROW3_3 to VirtualButton.VOLUME_DOWN,
        BTN_ROW3_4 to VirtualButton.VOLUME_UP,
        BTN_ROW4_1 to VirtualButton.NUMPAD,
        BTN_ROW4_2 to VirtualButton.EXTRA_BUTTONS,
        BTN_ROW4_3 to VirtualButton.PREVIOUS,
        BTN_ROW4_4 to VirtualButton.NEXT,
    )

    /**
     * Stores [virtualButton] for the slot [buttonKey]. R8-inlined into ControlButtonsActivity
     * (DialogInterfaceOnClickListenerC0167 case 0, leftover checks "buttonKey"/"virtualButton").
     * RECON-UNSURE: name.
     */
    fun setButton(buttonKey: String, virtualButton: VirtualButton) {
        prefs.dataStore.edit().putString(buttonKey, virtualButton.key).apply()
    }

    // obf: m3232
    fun setupButtons(vararg buttons: MaterialButton) {
        this.buttons.clear()
        buttons.forEachIndexed { index, button ->
            val key = when (index) {
                0 -> BTN_ROW1_1
                1 -> BTN_ROW1_2
                2 -> BTN_ROW2_1
                3 -> BTN_ROW2_2
                4 -> BTN_ROW3_1
                5 -> BTN_ROW3_2
                6 -> BTN_ROW3_3
                7 -> BTN_ROW3_4
                8 -> BTN_ROW4_1
                9 -> BTN_ROW4_2
                10 -> BTN_ROW4_3
                11 -> BTN_ROW4_4
                else -> error("Unimplemented button $index")
            }
            // RECON-UNSURE: `dataStore` accessor of JsonPrefs (see HapticFeedback)
            val saved = prefs.dataStore.getString(key, null)
            val virtualButton = VirtualButton.entries.firstOrNull { it.key == saved }
                ?: defaultButtons[key]
                ?: error("Unimplemented default button $key")
            this.buttons.add(virtualButton)
            setupButton(button, virtualButton, key)
        }
    }

    // obf: m3233 — the listener body was inlined here by R8 (see BackPressHandler.onButtonClicked)
    fun onButtonClicked(virtualButton: VirtualButton) {
        val listener = listener ?: return
        listener.onButtonClicked(virtualButton)
    }

    // obf: m3234 (press callback = C0582 case 2, continuous body = C0582 case 3)
    fun setupButton(button: MaterialButton, virtualButton: VirtualButton, key: String?) {
        button.setIconResource(virtualButton.icon)
        val title = virtualButton.title
        button.contentDescription = context.getString(title)
        TooltipCompat.setTooltipText(
            button,
            if (prefs.dataStore.getBoolean("enable_tooltip_on_buttons", false)) context.getString(title) else null,
        )
        button.setOnPressListener { isPressed ->
            var continuousJobCount = 0
            val vibrator = vibrator
            val continuousPressHandler = continuousPressHandler
            val hapticFeedback = virtualButton.hapticFeedback
            val continuousPress = virtualButton.continuousPress
            if (isPressed) {
                val onButtonSelected = onButtonSelected
                if (onButtonSelected != null) {
                    onButtonSelected(key!!)
                } else if (listener != null) {
                    if (!continuousPress) {
                        onButtonClicked(virtualButton)
                        if (hapticFeedback && vibrator != null) {
                            vibrator.perform(button, true)
                        }
                    } else if (continuousPressHandler != null) {
                        continuousPressHandler.start {
                            continuousJobCount++
                            onButtonClicked(virtualButton)
                            if (virtualButton.hapticFeedback) {
                                this.vibrator?.perform(button, true)
                            }
                        }
                    }
                }
            } else if (continuousPress) {
                continuousPressHandler?.stop()
                if (continuousJobCount <= 1) {
                    if (hapticFeedback) {
                        // RECON-UNSURE: original dereferences the nullable vibrator here without a check
                        vibrator!!.perform(button, false)
                    }
                }
            } else if (hapticFeedback && vibrator != null) {
                vibrator.perform(button, false)
            }
        }
    }

    companion object {
        const val BTN_ROW1_1 = "btn_row1_1"
        const val BTN_ROW1_2 = "btn_row1_2"
        const val BTN_ROW2_1 = "btn_row2_1"
        const val BTN_ROW2_2 = "btn_row2_2"
        const val BTN_ROW3_1 = "btn_row3_1"
        const val BTN_ROW3_2 = "btn_row3_2"
        const val BTN_ROW3_3 = "btn_row3_3"
        const val BTN_ROW3_4 = "btn_row3_4"
        const val BTN_ROW4_1 = "btn_row4_1"
        const val BTN_ROW4_2 = "btn_row4_2"
        const val BTN_ROW4_3 = "btn_row4_3"
        const val BTN_ROW4_4 = "btn_row4_4"
    }
}

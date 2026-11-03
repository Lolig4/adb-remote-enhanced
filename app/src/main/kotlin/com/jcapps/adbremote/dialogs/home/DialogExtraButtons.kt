package com.jcapps.adbremote.dialogs.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.VirtualButtonsAdapter
import com.jcapps.adbremote.api.adb.ConnectionMethod
import com.jcapps.adbremote.api.adb.commands.ApiCommand
import com.jcapps.adbremote.api.adb.commands.BinaryCommand
import com.jcapps.adbremote.databinding.DialogExtraButtonsBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.VirtualButton
import com.jcapps.adbremote.model.VirtualKey
import com.jcapps.adbremote.utils.ButtonManager
import com.jcapps.adbremote.utils.SpacingItemDecoration
import com.jcapps.adbremote.utils.getConnectionMethod
import com.jcapps.adbremote.utils.HapticFeedback
import com.jcapps.adbremote.utils.setOnPressListener
import kotlin.math.roundToInt

class DialogExtraButtons(
    // obf: f10137
    val buttonManager: ButtonManager,
    // obf: f10136
    private val vibrator: HapticFeedback,
) : BaseBottomSheet<DialogExtraButtonsBinding>() {
    // obf: C2717

    // obf: f10135 — VirtualKey / BinaryCommand / ApiCommand (MainActivity C1433 case 9)
    var onKeyEvent: ((Any) -> Unit)? = null

    // obf: mo1933 (C0771)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogExtraButtonsBinding
        get() = DialogExtraButtonsBinding::inflate

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return createBottomSheetDialog(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val virtualButtons = VirtualButton.entries.filter { it !in buttonManager.buttons }.toList()
        val recyclerView = binding.list
        recyclerView.addItemDecoration(
            SpacingItemDecoration(recyclerView.resources.getDimension(R.dimen.extra_buttons_spacing).roundToInt()),
        )
        recyclerView.adapter = VirtualButtonsAdapter(this, virtualButtons)
        val buttonControlMode =
            PreferenceManager.getDefaultSharedPreferences(requireContext()).getConnectionMethod()
        // obf: anonymous Function0 cases 0..3 (btnMoveHome / btnMoveEnd / btnPageUp / btnPageDown)
        binding.btnKeyHome.setHapticOnClickListener {
            onKeyEvent?.invoke(
                when (buttonControlMode) {
                    ConnectionMethod.SOCKET -> VirtualKey(74, null)
                    ConnectionMethod.BINARY -> BinaryCommand.KeyEvent(122)
                    ConnectionMethod.SERVER -> ApiCommand.KeyEvent(122)
                },
            )
        }
        binding.btnKeyEnd.setHapticOnClickListener {
            onKeyEvent?.invoke(
                when (buttonControlMode) {
                    ConnectionMethod.SOCKET -> VirtualKey(77, null)
                    ConnectionMethod.BINARY -> BinaryCommand.KeyEvent(123)
                    ConnectionMethod.SERVER -> ApiCommand.KeyEvent(123)
                },
            )
        }
        binding.btnKeyPageUp.setHapticOnClickListener {
            onKeyEvent?.invoke(
                when (buttonControlMode) {
                    ConnectionMethod.SOCKET -> VirtualKey(75, null)
                    ConnectionMethod.BINARY -> BinaryCommand.KeyEvent(92)
                    ConnectionMethod.SERVER -> ApiCommand.KeyEvent(92)
                },
            )
        }
        binding.btnKeyPageDown.setHapticOnClickListener {
            onKeyEvent?.invoke(
                when (buttonControlMode) {
                    ConnectionMethod.SOCKET -> VirtualKey(78, null)
                    ConnectionMethod.BINARY -> BinaryCommand.KeyEvent(93)
                    ConnectionMethod.SERVER -> ApiCommand.KeyEvent(93)
                },
            )
        }
    }

    // obf: m5110 (press callback = C0753 case 7)
    private fun MaterialButton.setHapticOnClickListener(onClicked: () -> Unit) {
        setOnPressListener { isPressed ->
            if (isPressed) {
                vibrator.perform(this, true)
                onClicked()
            } else {
                vibrator.perform(this, false)
            }
        }
    }
}

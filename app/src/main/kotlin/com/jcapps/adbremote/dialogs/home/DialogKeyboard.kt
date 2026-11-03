package com.jcapps.adbremote.dialogs.home

import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputFilter
import androidx.transition.TransitionManager
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.KeyboardButtonsAdapter
import com.jcapps.adbremote.api.adb.ConnectionMethod
import com.jcapps.adbremote.api.adb.commands.ApiCommand
import com.jcapps.adbremote.api.adb.commands.BinaryCommand
import com.jcapps.adbremote.databinding.DialogKeyboardBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.VirtualKey
import com.jcapps.adbremote.utils.HidKeyMapper
import com.jcapps.adbremote.utils.SpacingItemDecoration
import com.jcapps.adbremote.utils.UnsupportedCharacterException
import com.jcapps.adbremote.utils.getClipboardText
import com.jcapps.adbremote.utils.toKeyEvents
import com.jcapps.adbremote.utils.toVirtualKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.jcapps.adbremote.utils.getConnectionMethod

/** Keyboard sheet (FQN com.jcapps.adbremote.dialogs.home.DialogKeyboard from string evidence). */
class DialogKeyboard : BaseBottomSheet<DialogKeyboardBinding>() {
    // obf: C0234

    /** Special-key buttons (FQN DialogKeyboard.Button.* from string evidence). */
    sealed class Button {
        // obf: AbstractC3368
        class Icon(
            // obf: f3424
            @DrawableRes val icon: Int,
            // obf: f3423 — HID usage
            val keyCode: Int,
            // obf: f3426 — fixed HID modifier
            val modifierKey: Int? = null,
            // obf: f3425 — use the currently held modifier when [modifierKey] is null
            //  (never passed explicitly; R8 folded its default)
            val useModifier: Boolean = true,
            // obf: f3428
            val isEnabled: Boolean = true,
            // obf: f3427 — Android keyCode for binary/server mode
            val adbKeyCode: Int? = null,
        ) : Button() {
            // obf: C0269 — RECON-UNSURE: property names
        }

        class Text(
            // obf: f13157
            @StringRes val text: Int,
            // obf: f13156 — HID usage
            val keyCode: Int,
            // obf: f13158 — Android keyCode for binary/server mode
            val adbKeyCode: Int?,
        ) : Button() {
            // obf: C3705 — RECON-UNSURE: property names
        }

        /** Sends `input keyevent 23` through adb (DPAD_CENTER). */
        object IconADB : Button() {
            // obf: C4004
            val keyCode: Int = 23
        }
    }

    // obf: f3325
    var onDismiss: (() -> Unit)? = null

    // obf: f3326 — adb shell `input keyevent <code>` (MainActivity C1433 case 12)
    var onAdbKeyEvent: ((Int) -> Unit)? = null

    // obf: f3329 — VirtualKey / List<VirtualKey> / BinaryCommand / ApiCommand
    var onKeyEvent: ((Any) -> Unit)? = null

    // obf: f3330 — currently held HID modifier
    private var modifierKey: Int? = null

    // obf: f3331 — skip the next onDeleteCharacters callback
    private var ignoreNextDelete: Boolean = false

    // obf: f3328 (C0417 case 0)
    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    // obf: f3332 (C0417 case 1)
    private val connectionMethod: ConnectionMethod by lazy {
        prefs.getConnectionMethod()
    }

    // obf: f3327
    private val hidKeyMapper = HidKeyMapper()

    // obf: mo1933 (C3325)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogKeyboardBinding
        get() = DialogKeyboardBinding::inflate

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return createBottomSheetDialog(savedInstanceState)
    }

    override fun onDestroyView() {
        onDismiss?.invoke()
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        binding.inputText.requestFocus()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isSocket = connectionMethod == ConnectionMethod.SOCKET
        // obf: C0417 case 2
        binding.inputText.onTextSelected = {
            binding.inputLayoutPreview.helperText = getString(R.string.text_selection_not_supported_yet)
        }
        // obf: C1920 case 2
        binding.inputText.onCursorChanged = { oldPosition, newPosition ->
            val count = Math.abs(newPosition - oldPosition)
            if (count > 0) {
                val isLeft = oldPosition > newPosition
                when (connectionMethod) {
                    ConnectionMethod.SOCKET -> {
                        val keyCode = if (isLeft) 80 else 79
                        onKeyEvent?.invoke(List(count) { VirtualKey(keyCode, null) })
                    }
                    ConnectionMethod.BINARY -> {
                        val keyCode = if (isLeft) 21 else 22
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            // obf: C2366
                            repeat(count) {
                                onKeyEvent?.invoke(BinaryCommand.KeyEvent(keyCode))
                                delay(50L)
                            }
                        }
                    }
                    ConnectionMethod.SERVER -> {
                        val keyCode = if (isLeft) 21 else 22
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            // obf: C3045
                            repeat(count) {
                                onKeyEvent?.invoke(ApiCommand.KeyEvent(keyCode))
                                delay(50L)
                            }
                        }
                    }
                }
            }
        }
        // obf: anonymous Function1 case 2
        binding.inputText.onDeleteCharacters = { count ->
            if (ignoreNextDelete) {
                ignoreNextDelete = false
            } else {
                when (connectionMethod) {
                    ConnectionMethod.SOCKET -> send(List(count) { VirtualKey(42, null) })
                    ConnectionMethod.BINARY -> repeat(count) { send(BinaryCommand.KeyEvent(67)) }
                    ConnectionMethod.SERVER -> repeat(count) { send(ApiCommand.KeyEvent(67)) }
                }
            }
        }
        // obf: anonymous Function1 case 3
        binding.inputText.onSendKeyEvent = { event ->
            when (connectionMethod) {
                ConnectionMethod.SOCKET -> hidKeyMapper.map(event)?.let { send(it) }
                ConnectionMethod.BINARY -> {
                    val keyCode = if (event.action == KeyEvent.ACTION_DOWN) event.keyCode else null
                    if (keyCode != null) send(BinaryCommand.KeyEvent(keyCode))
                }
                ConnectionMethod.SERVER -> {
                    val keyCode = if (event.action == KeyEvent.ACTION_DOWN) event.keyCode else null
                    if (keyCode != null) send(ApiCommand.KeyEvent(keyCode))
                }
            }
        }
        // obf: ViewOnClickListener case 1 — clear
        binding.inputLayoutPreview.setEndIconOnClickListener {
            ignoreNextDelete = true
            binding.inputText.setText(String())
        }
        // obf: C1135 case 0
        val filter = InputFilter { source, _, _, _, _, _ ->
            if (source == null || source.length == 0) return@InputFilter null
            try {
                val text = source.toString()
                when (connectionMethod) {
                    ConnectionMethod.SOCKET -> send(text.toVirtualKeys())
                    ConnectionMethod.BINARY -> for (event in toKeyEvents(text)) {
                        send(if (event is Int) BinaryCommand.KeyEvent(event) else BinaryCommand.Text(event as String))
                    }
                    ConnectionMethod.SERVER -> for (event in toKeyEvents(text)) {
                        send(if (event is Int) ApiCommand.KeyEvent(event) else ApiCommand.Text(event as String))
                    }
                }
                binding.inputLayoutPreview.helperText = getString(R.string.preview_may_differ_actual_text)
                null
            } catch (e: UnsupportedCharacterException) {
                TransitionManager.beginDelayedTransition(requireView().parent as ViewGroup, null)
                binding.inputLayoutPreview.helperText = getString(R.string.character_not_supported_yet, e.char)
                e.passed
            }
        }
        binding.inputText.filters = arrayOf(filter)
        // obf: ViewOnClickListenerC0827 case 0 — paste
        binding.inputLayoutPreview.setStartIconOnClickListener {
            val clipboardText = requireContext().getClipboardText()
            if (clipboardText.length > 0) {
                if (!isSocket) {
                    send(
                        if (connectionMethod == ConnectionMethod.BINARY) {
                            BinaryCommand.Text(clipboardText)
                        } else {
                            ApiCommand.Text(clipboardText)
                        },
                    )
                    val editText = binding.inputText
                    editText.filters = arrayOf()
                    editText.text?.insert(editText.selectionEnd, clipboardText)
                    editText.filters = arrayOf(filter)
                } else {
                    binding.inputText.text?.insert(binding.inputText.selectionEnd, clipboardText)
                }
            }
        }
        val navigationButtons = listOf(
            Button.Icon(R.drawable.ic_arrow_left, 80, adbKeyCode = 21),
            Button.Icon(R.drawable.ic_arrow_right, 79, adbKeyCode = 22),
            Button.Icon(R.drawable.ic_arrow_up, 82, adbKeyCode = 19),
            Button.Icon(R.drawable.ic_arrow_down, 81, adbKeyCode = 20),
            Button.IconADB,
            Button.Icon(R.drawable.ic_back, 241, null, false, adbKeyCode = 4),
            Button.Text(R.string.home, 74, 122),
            Button.Text(R.string.end, 77, 123),
            Button.Text(R.string.pgup, 75, 92),
            Button.Text(R.string.pgdn, 78, 93),
        )
        val functionButtons = listOf(
            Button.Icon(R.drawable.ic_wakeup, 4, 224, isEnabled = isSocket),
            Button.Icon(R.drawable.ic_copy, 124, null, false, adbKeyCode = 278),
            Button.Icon(R.drawable.ic_paste, 125, null, false, adbKeyCode = 279),
            Button.Icon(R.drawable.ic_cut, 123, null, false, adbKeyCode = 277),
            Button.Text(R.string.tab_2, 43, 61),
            Button.Text(R.string.esc, 41, 111),
            Button.Text(R.string.insert, 73, 124),
            Button.Icon(R.drawable.ic_f1, 58, adbKeyCode = 131),
            Button.Icon(R.drawable.ic_f2, 59, adbKeyCode = 132),
            Button.Icon(R.drawable.ic_f3, 60, adbKeyCode = 133),
            Button.Icon(R.drawable.ic_f4, 61, adbKeyCode = 134),
            Button.Icon(R.drawable.ic_f5, 62, adbKeyCode = 135),
            Button.Icon(R.drawable.ic_f6, 63, adbKeyCode = 136),
            Button.Icon(R.drawable.ic_f7, 64, adbKeyCode = 137),
            Button.Icon(R.drawable.ic_f8, 65, adbKeyCode = 138),
            Button.Icon(R.drawable.ic_f9, 66, adbKeyCode = 139),
            Button.Icon(R.drawable.ic_f10, 67, adbKeyCode = 140),
            Button.Icon(R.drawable.ic_f11, 68, adbKeyCode = 141),
            Button.Icon(R.drawable.ic_f12, 69, adbKeyCode = 142),
        )
        val recyclerView = binding.listKeyboardKeys
        recyclerView.addItemDecoration(
            SpacingItemDecoration(recyclerView.resources.getDimension(R.dimen.keyboard_keys_spacing).roundToInt()),
        )
        // obf: anonymous Function1 case 0
        recyclerView.adapter = KeyboardButtonsAdapter(requireContext(), navigationButtons) { onButtonClicked(it) }
        val recyclerView2 = binding.listKeyboardFunctionKeys
        recyclerView2.addItemDecoration(
            SpacingItemDecoration(recyclerView2.resources.getDimension(R.dimen.keyboard_keys_spacing).roundToInt()),
        )
        // obf: anonymous Function1 case 1
        recyclerView2.adapter = KeyboardButtonsAdapter(requireContext(), functionButtons) { onButtonClicked(it) }
        val keysToIds = mapOf(225 to R.id.btn_shift, 224 to R.id.btn_ctrl, 226 to R.id.btn_alt)
        val idsToKeys = keysToIds.entries.associate { (key, id) -> id to key }
        if (connectionMethod != ConnectionMethod.SOCKET) {
            binding.toggleKeyboardMode.isEnabled = false
        }
        binding.toggleKeyboardMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                modifierKey = idsToKeys[checkedId]
                return@addOnButtonCheckedListener
            }
            val current = modifierKey ?: return@addOnButtonCheckedListener
            if (keysToIds[current] != checkedId) return@addOnButtonCheckedListener
            modifierKey = null
        }
        // obf: ViewOnClickListener case 0 — info
        binding.btnInfo.setOnClickListener {
            val message = StringBuilder()
            if (connectionMethod != ConnectionMethod.SOCKET) {
                message.append(getString(R.string.modifier_keys_key_combinations_only))
                message.append("\n\n")
            }
            message.append(getString(R.string.app_send_key_combinations_using))
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.keyboard)
                .setMessage(message.toString())
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        binding.inputText.requestFocus()
    }

    // obf: m1932
    private fun onButtonClicked(button: Button) {
        when (button) {
            is Button.Icon -> sendKey(button.keyCode, button.modifierKey, button.useModifier, button.adbKeyCode)
            is Button.Text -> sendKey(button.keyCode, null, true, button.adbKeyCode)
            is Button.IconADB -> onAdbKeyEvent?.invoke(button.keyCode)
        }
    }

    // obf: m1934
    private fun sendKey(keyCode: Int, modifierKey: Int?, useModifier: Boolean, adbKeyCode: Int?) {
        when (connectionMethod) {
            ConnectionMethod.SOCKET -> {
                val modifier = modifierKey ?: if (useModifier) this.modifierKey else null
                send(VirtualKey(keyCode, modifier))
            }
            ConnectionMethod.BINARY -> if (adbKeyCode != null) send(BinaryCommand.KeyEvent(adbKeyCode))
            ConnectionMethod.SERVER -> if (adbKeyCode != null) send(ApiCommand.KeyEvent(adbKeyCode))
        }
    }

    // obf: m1935
    private fun send(command: Any) {
        onKeyEvent?.invoke(command)
    }
}

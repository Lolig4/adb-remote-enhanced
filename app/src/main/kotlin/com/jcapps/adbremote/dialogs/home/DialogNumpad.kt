package com.jcapps.adbremote.dialogs.home

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputFilter
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.ControlButtonsAdapter
import com.jcapps.adbremote.api.adb.ConnectionMethod
import com.jcapps.adbremote.api.adb.commands.ApiCommand
import com.jcapps.adbremote.api.adb.commands.BinaryCommand
import com.jcapps.adbremote.databinding.DialogNumpadBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.VirtualKey
import com.jcapps.adbremote.utils.ContinuousPressHandler
import com.jcapps.adbremote.utils.HapticFeedback
import com.jcapps.adbremote.utils.SpacingItemDecoration
import com.jcapps.adbremote.utils.UnsupportedCharacterException
import com.jcapps.adbremote.utils.copyToClipboard
import com.jcapps.adbremote.utils.getClipboardText
import com.jcapps.adbremote.utils.getConnectionMethod
import com.jcapps.adbremote.utils.toKeyEvents
import com.jcapps.adbremote.utils.toVirtualKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Numpad sheet (FQN com.jcapps.adbremote.dialogs.home.DialogNumpad from string evidence). */
class DialogNumpad : BaseBottomSheet<DialogNumpadBinding>() {
    // obf: C0958

    /** Grid entry (FQN DialogNumpad.NumpadData.* from string evidence). */
    sealed class NumpadData(
        // obf: f11570 — Android keyCode
        val keyCode: Int,
    ) {
        // obf: AbstractC3222
        class Number(
            keyCode: Int,
            // obf: f6983
            val value: String,
        ) : NumpadData(keyCode) {
            // obf: C1588
        }

        class Icon(
            keyCode: Int,
            // obf: f13329
            @DrawableRes val icon: Int,
        ) : NumpadData(keyCode) {
            // obf: C3773
        }
    }

    // obf: f5261 — VirtualKey / BinaryCommand / ApiCommand (MainActivity C1433 case 13)
    var onKeyEvent: ((Any) -> Unit)? = null

    // obf: f5259 (C2896 case 0)
    private val vibrator: HapticFeedback by lazy { HapticFeedback(requireContext()) }

    // obf: f5258 (C2896 case 1)
    private val continuousPressHandler: ContinuousPressHandler by lazy {
        ContinuousPressHandler(requireContext(), lifecycleScope)
    }

    // obf: f5260 (C2896 case 2)
    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    // obf: f5262 (C2896 case 3)
    private val connectionMethod: ConnectionMethod by lazy { prefs.getConnectionMethod() }

    // obf: mo1933 (C2582)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogNumpadBinding
        get() = DialogNumpadBinding::inflate

    override fun onDestroyView() {
        super.onDestroyView()
        continuousPressHandler.stop()
    }

    // lambdas: C0613 case 2 (onButtonTouched), C2896 case 4, C1920 case 3, C1135 case 1 (InputFilter),
    //  anonymous click listener (case 0 paste from clipboard, case 1 copy to clipboard)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val buttons = ArrayList<NumpadData>()
        for (i in 1 until 10) {
            buttons.add(NumpadData.Number(KeyEvent.keyCodeFromString((i + 7).toString()), i.toString()))
        }
        buttons.add(NumpadData.Icon(67, R.drawable.ic_backspace))
        buttons.add(NumpadData.Number(7, "0"))
        buttons.add(NumpadData.Icon(66, R.drawable.ic_check))
        // obf: C0613 case 2
        val adapter = ControlButtonsAdapter(requireContext(), buttons) { btn, data, isPressed ->
            if (!isPressed) {
                continuousPressHandler.stop()
            } else if (data.keyCode == 66) {
                onKeyEvent?.invoke(
                    when (connectionMethod) {
                        ConnectionMethod.SOCKET -> VirtualKey(40, null)
                        ConnectionMethod.BINARY -> BinaryCommand.KeyEvent(66)
                        ConnectionMethod.SERVER -> ApiCommand.KeyEvent(66)
                    },
                )
                vibrator.perform(btn, true)
                dismiss()
            } else {
                continuousPressHandler.start {
                    // obf: C0753 case 8
                    vibrator.perform(btn, true)
                    if (data is NumpadData.Number) {
                        binding.inputText.sendKey(data.keyCode)
                    } else {
                        onKeyEvent?.invoke(
                            when (connectionMethod) {
                                ConnectionMethod.SOCKET -> VirtualKey(42, null)
                                ConnectionMethod.BINARY -> BinaryCommand.KeyEvent(67)
                                ConnectionMethod.SERVER -> ApiCommand.KeyEvent(67)
                            },
                        )
                        binding.inputText.sendKey(67)
                    }
                }
            }
        }
        val recyclerView = binding.list
        recyclerView.addItemDecoration(
            SpacingItemDecoration(recyclerView.resources.getDimension(R.dimen.list_spacing).roundToInt()),
        )
        recyclerView.adapter = adapter
        binding.inputText.showSoftInputOnFocus = false
        binding.inputText.maxLines = 1
        // obf: C2896 case 4
        binding.inputText.onTextSelected = {
            binding.inputLayoutPreview.helperText = getString(R.string.text_selection_not_supported_yet)
        }
        // obf: C1920 case 3
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
                            // obf: C1155
                            repeat(count) {
                                onKeyEvent?.invoke(BinaryCommand.KeyEvent(keyCode))
                                delay(50L)
                            }
                        }
                    }
                    ConnectionMethod.SERVER -> {
                        val keyCode = if (isLeft) 21 else 22
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            // obf: C3071
                            repeat(count) {
                                onKeyEvent?.invoke(ApiCommand.KeyEvent(keyCode))
                                delay(50L)
                            }
                        }
                    }
                }
            }
        }
        // obf: C1135 case 1
        binding.inputText.filters = arrayOf(
            InputFilter { source, _, _, _, _, _ ->
                if (source == null || source.length == 0) return@InputFilter null
                try {
                    val text = source.toString()
                    when (connectionMethod) {
                        ConnectionMethod.SOCKET -> onKeyEvent?.invoke(text.toVirtualKeys())
                        ConnectionMethod.BINARY -> for (event in toKeyEvents(text)) {
                            onKeyEvent?.invoke(
                                if (event is Int) BinaryCommand.KeyEvent(event) else BinaryCommand.Text(event as String),
                            )
                        }
                        ConnectionMethod.SERVER -> for (event in toKeyEvents(text)) {
                            onKeyEvent?.invoke(
                                if (event is Int) ApiCommand.KeyEvent(event) else ApiCommand.Text(event as String),
                            )
                        }
                    }
                    binding.inputLayoutPreview.helperText = getString(R.string.preview_may_differ_actual_text)
                    null
                } catch (e: UnsupportedCharacterException) {
                    binding.inputLayoutPreview.helperText = getString(R.string.character_not_supported_yet, e.char)
                    ""
                }
            },
        )
        // obf: anonymous click listener case 0 — paste a number from the clipboard
        binding.inputLayoutPreview.setEndIconOnClickListener {
            val number = requireContext().getClipboardText().toIntOrNull()
            if (number != null) {
                binding.inputText.text?.insert(binding.inputText.selectionEnd, number.toString())
            }
        }
        // obf: anonymous click listener case 1 — copy to clipboard
        binding.inputLayoutPreview.setStartIconOnClickListener {
            val text = binding.inputText.text.toString()
            requireContext().copyToClipboard(text)
            binding.inputLayoutPreview.helperText = getString(R.string.copied_clipboard)
        }
    }
}

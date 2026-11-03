package com.jcapps.adbremote.dialogs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.jcapps.adbremote.databinding.DialogInputBinding

/** Single text-input bottom sheet with [title], prefilled [text]; OK reports the entered text. */
class DialogInput : BaseBottomSheet<DialogInputBinding>() {
    // obf: C1626

    // obf: f7104 — RECON-UNSURE name
    var onInput: ((String) -> Unit)? = null

    // obf: f7105
    var text: String? = null

    // obf: f7106
    var title: String? = null

    // obf: mo1933 (C2577)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogInputBinding
        get() = DialogInputBinding::inflate

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.title.text = title
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        binding.btnOk.isEnabled = false
        val text = text
        val input = binding.inputEnterTextHere
        if (text != null) {
            input.text?.insert(0, text)
        }
        // obf: C3076 case 3
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                val string = s?.toString()
                binding.btnOk.isEnabled = !string.isNullOrEmpty()
            }
        })
        binding.btnOk.setOnClickListener {
            onInput?.invoke(binding.inputEnterTextHere.text.toString())
            dismiss()
        }
    }
}

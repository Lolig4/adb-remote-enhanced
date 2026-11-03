package com.jcapps.adbremote.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.jcapps.adbremote.adapters.OptionsAdapter
import com.jcapps.adbremote.databinding.DialogOptionBinding
import com.jcapps.adbremote.model.Option

/** Bottom sheet listing [options] (optionally with a [title]); reports the picked one. */
class DialogOption : BaseBottomSheet<DialogOptionBinding>() {
    // obf: C1054

    // obf: f5533
    var onOptionSelected: ((Option) -> Unit)? = null

    // obf: f5534
    var options: List<Option>? = null

    // obf: f5535
    var title: String? = null

    // obf: mo1933 (C3539)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogOptionBinding
        get() = DialogOptionBinding::inflate

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return createBottomSheetDialog(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (options == null) return
        if (title == null) {
            binding.title.visibility = View.GONE
        } else {
            binding.title.text = title
        }
        // obf: C2192 case 14 — RECON-UNSURE: body not decompiled by jadx (presumed invoke + dismiss)
        binding.list.adapter = OptionsAdapter(options!!) {
            onOptionSelected?.invoke(it)
            dismiss()
        }
    }
}

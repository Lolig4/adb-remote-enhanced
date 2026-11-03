package com.jcapps.adbremote.settings

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.preference.ListPreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * [ListPreferenceDialogFragmentCompat] rendered with a [MaterialAlertDialogBuilder].
 *
 * Tracks the clicked button itself because the base class's `mWhichButtonClicked` is private;
 * when the dialog is dismissed, [onDialogClosed] reports "positive" only if the positive button
 * was clicked. RECON-UNSURE: class/member names (unregistered alias C1847).
 */
class MaterialListPreferenceDialog : ListPreferenceDialogFragmentCompat() {
    // obf: C1847

    // obf: f7682
    private var onDialogClosedWasCalledFromOnDismiss = false

    // obf: f7683
    private var whichButtonClicked = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        whichButtonClicked = DialogInterface.BUTTON_NEGATIVE
        val builder = MaterialAlertDialogBuilder(requireActivity())
            .setTitle(preference.dialogTitle)
            .setIcon(preference.dialogIcon)
            .setPositiveButton(preference.positiveButtonText, this)
            .setNegativeButton(preference.negativeButtonText, this)
        val contentView = context?.let { onCreateDialogView(it) }
        if (contentView != null) {
            onBindDialogView(contentView)
            builder.setView(contentView)
        } else {
            builder.setMessage(preference.dialogMessage)
        }
        onPrepareDialogBuilder(builder)
        return builder.create()
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        whichButtonClicked = which
    }

    override fun onDismiss(dialog: DialogInterface) {
        onDialogClosedWasCalledFromOnDismiss = true
        super.onDismiss(dialog)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (onDialogClosedWasCalledFromOnDismiss) {
            onDialogClosedWasCalledFromOnDismiss = false
            super.onDialogClosed(whichButtonClicked == DialogInterface.BUTTON_POSITIVE)
        } else {
            super.onDialogClosed(positiveResult)
        }
    }
}

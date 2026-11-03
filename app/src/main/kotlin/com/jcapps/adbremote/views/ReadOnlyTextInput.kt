package com.jcapps.adbremote.views

import android.content.Context
import android.os.Build
import android.text.method.MovementMethod
import android.util.AttributeSet
import android.view.View
import com.google.android.material.textfield.TextInputEditText

/**
 * A [TextInputEditText] that shows selectable but non-editable text (used for read-only fields
 * such as the device IP). Original FQN kept.
 */
class ReadOnlyTextInput(context: Context, attrs: AttributeSet?) : TextInputEditText(context, attrs) {
    // obf: ReadOnlyTextInput

    init {
        setTextIsSelectable(true)
        if (Build.VERSION.SDK_INT >= 26) {
            setFocusable(View.FOCUSABLE_AUTO)
        }
    }

    override fun getDefaultEditable(): Boolean = false

    override fun getFreezesText(): Boolean = false

    override fun getDefaultMovementMethod(): MovementMethod? = null
}

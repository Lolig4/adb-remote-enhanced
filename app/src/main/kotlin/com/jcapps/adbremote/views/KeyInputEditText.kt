package com.jcapps.adbremote.views

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.ActionMode
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import com.google.android.material.textfield.TextInputEditText

/**
 * Edit text used as a key sink: forwards deletions, IME key events, cursor moves and blocks
 * selection / context menus. Referenced from layouts dialog_keyboard / dialog_numpad.
 */
class KeyInputEditText(context: Context, attrs: AttributeSet) : TextInputEditText(context, attrs) {
    // obf: C1368

    // obf: f1578
    var onTextSelected: (() -> Unit)? = null

    // obf: f1582 — (oldPosition, newPosition)
    var onCursorChanged: ((Int, Int) -> Unit)? = null

    // obf: f1583
    var onSendKeyEvent: ((KeyEvent) -> Unit)? = null

    // obf: f1584 — number of deleted characters
    var onDeleteCharacters: ((Int) -> Unit)? = null

    // obf: f1579
    private var cursorPosition: Int = 0

    // obf: f1580
    private var previousText: String = ""

    // obf: f1581
    private var textChanged: Boolean = false

    init {
        // obf: C3076 case 1
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                textChanged = true
            }

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                val deleted = previousText.length - text.length
                if (deleted > 0) {
                    onDeleteCharacters?.invoke(deleted)
                }
                previousText = text
            }
        })
    }

    override fun onCreateContextMenu(menu: ContextMenu?) {
        menu?.clear()
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        return KeyInputConnection(super.onCreateInputConnection(outAttrs))
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        if (selStart != selEnd) {
            setSelection(cursorPosition)
            onTextSelected?.invoke()
        } else {
            if (!textChanged) {
                onCursorChanged?.invoke(cursorPosition, selEnd)
            }
            textChanged = false
            cursorPosition = selEnd
        }
        super.onSelectionChanged(selStart, selEnd)
    }

    override fun onTextContextMenuItem(id: Int): Boolean = false

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? = null

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? = null

    // obf: m837
    fun sendKey(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, 0))
        dispatchKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_UP, keyCode, 0))
    }

    /** RECON-UNSURE: name. R8 merged it with androidx InputConnectionCompat's wrapper (case 0). */
    private inner class KeyInputConnection(target: InputConnection?) : InputConnectionWrapper(target, true) {
        // obf: InputConnectionCompat (case 0)
        override fun sendKeyEvent(event: KeyEvent?): Boolean {
            if (event != null) {
                onSendKeyEvent?.invoke(event)
            }
            return super.sendKeyEvent(event)
        }
    }
}

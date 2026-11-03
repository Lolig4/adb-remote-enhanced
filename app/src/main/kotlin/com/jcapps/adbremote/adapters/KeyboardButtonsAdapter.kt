package com.jcapps.adbremote.adapters

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.jcapps.adbremote.R
import com.jcapps.adbremote.dialogs.home.DialogKeyboard

/** Buttons row of DialogKeyboard. (R8 merged this with ControlButtonsAdapter into C2919, case 0.) */
class KeyboardButtonsAdapter(
    // obf: f10601
    private val context: Context,
    // obf: f10604
    private val buttons: List<DialogKeyboard.Button>,
    // obf: f10603
    private val onButtonClicked: (DialogKeyboard.Button) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    // obf: C2919#0

    inner class IconViewHolder(
        // obf: f5412
        val btn: MaterialButton,
    ) : RecyclerView.ViewHolder(btn) {
        // obf: C1015
        init {
            // obf: ViewOnClickListenerC2376 case 9
            btn.setOnClickListener {
                onButtonClicked(buttons[absoluteAdapterPosition] as DialogKeyboard.Button.Icon)
            }
        }
    }

    inner class TextViewHolder(
        // obf: f10536
        val btn: MaterialButton,
    ) : RecyclerView.ViewHolder(btn) {
        // obf: C2897
        init {
            // obf: ViewOnClickListenerC2376 case 10
            btn.setOnClickListener {
                onButtonClicked(buttons[absoluteAdapterPosition] as DialogKeyboard.Button.Text)
            }
        }
    }

    inner class IconADBViewHolder(
        // obf: f3653
        val btn: MaterialButton,
    ) : RecyclerView.ViewHolder(btn) {
        // obf: C0363
        init {
            // obf: ViewOnClickListenerC2376 case 8
            btn.setOnClickListener {
                onButtonClicked(buttons[absoluteAdapterPosition] as DialogKeyboard.Button.IconADB)
            }
        }
    }

    override fun getItemCount(): Int = buttons.size

    override fun getItemViewType(position: Int): Int = when (buttons[position]) {
        is DialogKeyboard.Button.Icon -> 1
        is DialogKeyboard.Button.Text -> 2
        is DialogKeyboard.Button.IconADB -> 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        1 -> IconViewHolder(MaterialButton(context, null, R.attr.materialIconButtonOutlinedStyle))
        2 -> TextViewHolder(MaterialButton(context, null, R.attr.materialButtonOutlinedStyle))
        3 -> IconADBViewHolder(MaterialButton(context, null, R.attr.materialIconButtonOutlinedStyle))
        else -> throw IllegalStateException("Unexpected view type: $viewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is IconViewHolder -> {
                val button = buttons[position] as DialogKeyboard.Button.Icon
                holder.btn.isEnabled = button.isEnabled
                holder.btn.setIconResource(button.icon)
            }
            is TextViewHolder -> {
                holder.btn.isEnabled = true
                holder.btn.setText((buttons[position] as DialogKeyboard.Button.Text).text)
            }
            is IconADBViewHolder -> {
                buttons[position] as DialogKeyboard.Button.IconADB
                holder.btn.isEnabled = true
                holder.btn.setIconResource(R.drawable.ic_dpad_center)
            }
        }
    }
}

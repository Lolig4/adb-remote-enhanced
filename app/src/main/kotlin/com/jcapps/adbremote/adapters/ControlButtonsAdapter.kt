package com.jcapps.adbremote.adapters

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.jcapps.adbremote.R
import com.jcapps.adbremote.dialogs.home.DialogNumpad
import com.jcapps.adbremote.utils.setOnPressListener

/** Numpad buttons grid. (R8 merged this with KeyboardButtonsAdapter into C2919, case 1.) */
class ControlButtonsAdapter(
    // obf: f10601
    private val context: Context,
    // obf: f10604
    private val buttons: ArrayList<DialogNumpad.NumpadData>,
    // obf: f10603 — (button view, data, isPressed)
    private val onButtonTouched: (MaterialButton, DialogNumpad.NumpadData, Boolean) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    // obf: C2919#1

    class NumberViewHolder(
        // obf: f11293
        val btn: MaterialButton,
    ) : RecyclerView.ViewHolder(btn) {
        // obf: C3152
    }

    class IconViewHolder(
        // obf: f3783
        val btn: MaterialButton,
    ) : RecyclerView.ViewHolder(btn) {
        // obf: C0407
    }

    override fun getItemCount(): Int = buttons.size

    override fun getItemViewType(position: Int): Int = when (buttons[position]) {
        is DialogNumpad.NumpadData.Number -> 1
        is DialogNumpad.NumpadData.Icon -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        1 -> NumberViewHolder(MaterialButton(context, null, R.attr.keyTextButtonStyle))
        2 -> IconViewHolder(MaterialButton(context, null, R.attr.keyIconButtonStyle))
        else -> throw IllegalStateException("Unexpected view type: $viewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is NumberViewHolder) {
            val data = buttons[position] as DialogNumpad.NumpadData.Number
            holder.btn.text = data.value
            // obf: C0753 case 9
            holder.btn.setOnPressListener { isPressed ->
                onButtonTouched(holder.btn, data, isPressed)
            }
        } else if (holder is IconViewHolder) {
            val data = buttons[position] as DialogNumpad.NumpadData.Icon
            holder.btn.setIconResource(data.icon)
            // obf: C0753 case 10
            holder.btn.setOnPressListener { isPressed ->
                onButtonTouched(holder.btn, data, isPressed)
            }
        }
    }
}

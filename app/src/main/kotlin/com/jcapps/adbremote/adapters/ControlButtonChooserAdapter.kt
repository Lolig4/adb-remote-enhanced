package com.jcapps.adbremote.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import coil.load
import com.jcapps.adbremote.databinding.ItemControlButtonBinding
import com.jcapps.adbremote.model.VirtualButton

/**
 * List of [VirtualButton]s (icon + title) shown in ControlButtonsActivity's "choose button" dialog.
 * Item layout is `item_control_button` (binding name from string evidence).
 * RECON-UNSURE: class name (unregistered alias C3119).
 */
class ControlButtonChooserAdapter(
    context: Context,
    // obf: f11198
    private val buttons: List<VirtualButton>,
) : BaseAdapter() {
    // obf: C3119

    // obf: f11199
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = buttons.size

    override fun getItem(position: Int): VirtualButton = buttons[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding: ItemControlButtonBinding
        val view: View
        if (convertView == null) {
            binding = ItemControlButtonBinding.inflate(inflater, parent, false)
            view = binding.root
            view.tag = binding
        } else {
            binding = convertView.tag as ItemControlButtonBinding
            view = convertView
        }
        val virtualButton = buttons[position]
        binding.imageButtonIcon.load(virtualButton.icon)
        binding.textAdbRemote.setText(virtualButton.title)
        return view
    }
}

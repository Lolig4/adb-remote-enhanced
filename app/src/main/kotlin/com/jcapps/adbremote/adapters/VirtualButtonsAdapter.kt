package com.jcapps.adbremote.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.databinding.ItemVirtualButtonBinding
import com.jcapps.adbremote.dialogs.home.DialogExtraButtons
import com.jcapps.adbremote.model.VirtualButton

/** Grid of extra [VirtualButton]s in DialogExtraButtons. (R8 merged into C3442, case 3.) */
class VirtualButtonsAdapter(
    // obf: f12294
    private val dialog: DialogExtraButtons,
    // obf: f12296
    private val virtualButtons: List<VirtualButton>,
) : RecyclerView.Adapter<VirtualButtonsAdapter.ViewHolder>() {
    // obf: C3442#3 — RECON-UNSURE: likely an inner class of DialogExtraButtons (outer ref not null-checked)

    class ViewHolder(
        // obf: f11417
        val binding: ItemVirtualButtonBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        // obf: C3199
    }

    override fun getItemCount(): Int = virtualButtons.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVirtualButtonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val virtualButton = virtualButtons[position]
        dialog.buttonManager.setupButton(holder.binding.btnVirtualButton, virtualButton, null)
    }
}

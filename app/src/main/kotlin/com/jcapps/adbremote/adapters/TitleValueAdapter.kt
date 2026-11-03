package com.jcapps.adbremote.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.databinding.ItemTitleValueBinding
import com.jcapps.adbremote.model.TitleValue

/**
 * Static title/value rows (item layout `c9`, proposed `item_title_value`); used by DialogDeviceInfo.
 * Claimed by group main-b. RECON-UNSURE: class names.
 */
class TitleValueAdapter(
    // obf: f13317
    private val dataList: List<TitleValue>,
) : RecyclerView.Adapter<TitleValueAdapter.ViewHolder>() {
    // obf: C3766

    override fun getItemCount(): Int = dataList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTitleValueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = dataList[position]
        val binding = holder.binding
        binding.textTitle.setText(data.title)
        binding.textValue.text = data.value
    }

    class ViewHolder(
        // obf: f7022
        val binding: ItemTitleValueBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        // obf: C1601
    }
}

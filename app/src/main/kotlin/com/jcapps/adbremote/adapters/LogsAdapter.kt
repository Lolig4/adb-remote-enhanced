package com.jcapps.adbremote.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.databinding.ItemTextBinding

/** Log lines of [com.jcapps.adbremote.ui.LogsActivity] (items = [com.jcapps.adbremote.utils.Logger.logs]). */
class LogsAdapter(
    // obf: f12296
    private val onItemClicked: (String) -> Unit,
) : ListAdapter<Pair<Int, String>, LogsAdapter.ViewHolder>(DiffCallback) {
    // obf: C3442 (case 0)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemTextBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.textTitle.text = getItem(position).second
    }

    // obf: C1567
    inner class ViewHolder(
        // obf: f6943
        val binding: ItemTextBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            // obf: ViewOnClickListenerC2376 case 1
            binding.root.setOnClickListener { onItemClicked(getItem(bindingAdapterPosition).second) }
        }
    }

    // obf: C2403 case 1
    private object DiffCallback : DiffUtil.ItemCallback<Pair<Int, String>>() {
        override fun areItemsTheSame(oldItem: Pair<Int, String>, newItem: Pair<Int, String>): Boolean =
            oldItem.first == newItem.first

        override fun areContentsTheSame(oldItem: Pair<Int, String>, newItem: Pair<Int, String>): Boolean =
            oldItem == newItem
    }
}

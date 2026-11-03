package com.jcapps.adbremote.adapters.apps

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.databinding.ItemTextBinding

/**
 * Permission names of AppInfoActivity; a click copies the name.
 * RECON-UNSURE: class name (case 2 of the R8-merged adapter C3442; row layout ci shared with LogsAdapter).
 */
class AppPermissionsAdapter(
    // obf: f12296 (C1026 case 1)
    private val onItemClicked: (String) -> Unit,
) : RecyclerView.Adapter<AppPermissionsAdapter.ViewHolder>() {
    // obf: C3442#2

    // obf: f12294 (C1523 = AsyncListDiffer, diff = C2403 case 3)
    val differ: AsyncListDiffer<String> = AsyncListDiffer(
        this,
        object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        },
    )

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTextBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.root.text = differ.currentList[position]
    }

    inner class ViewHolder(
        // obf: f12587
        val binding: ItemTextBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        // obf: C3537

        init {
            // obf: ViewOnClickListenerC2376 case 3
            binding.root.setOnClickListener {
                val permission = differ.currentList[absoluteAdapterPosition]
                onItemClicked(permission)
            }
        }
    }
}

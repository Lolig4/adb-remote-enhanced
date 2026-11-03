package com.jcapps.adbremote.adapters.apps

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.databinding.ItemAppInfoBinding
import com.jcapps.adbremote.model.Info

/**
 * Title/value rows of AppInfoActivity (package, version, SDKs, paths, ...); a click copies the value.
 * RECON-UNSURE: class name (case 1 of the R8-merged adapter C3442).
 */
class AppInfoAdapter(
    // obf: f12296 (C1026 case 0)
    private val onItemClicked: (Info) -> Unit,
) : RecyclerView.Adapter<AppInfoAdapter.ViewHolder>() {
    // obf: C3442#1

    // obf: f12294 (C1523 = AsyncListDiffer, diff = C2403 case 2)
    val differ: AsyncListDiffer<Info> = AsyncListDiffer(
        this,
        object : DiffUtil.ItemCallback<Info>() {
            override fun areItemsTheSame(oldItem: Info, newItem: Info): Boolean = oldItem.title == newItem.title

            override fun areContentsTheSame(oldItem: Info, newItem: Info): Boolean = oldItem == newItem
        },
    )

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppInfoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val info = differ.currentList[position]
        holder.binding.title.setText(info.title)
        holder.binding.textInfoValue.text = info.value
    }

    inner class ViewHolder(
        // obf: f2997
        val binding: ItemAppInfoBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        // obf: C0162

        init {
            // obf: ViewOnClickListenerC2376 case 2
            binding.root.setOnClickListener {
                val info = differ.currentList[absoluteAdapterPosition]
                onItemClicked(info)
            }
        }
    }
}

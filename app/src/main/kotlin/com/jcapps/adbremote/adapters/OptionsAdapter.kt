package com.jcapps.adbremote.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.jcapps.adbremote.databinding.ItemOptionBinding
import com.jcapps.adbremote.model.Option

/** List of [Option]s in DialogOption. (R8 merged several adapters into C3442; this is the default case.) */
class OptionsAdapter(
    // obf: f12296
    private val options: List<Option>,
    // obf: f12294
    private val onOptionSelected: (Option) -> Unit,
) : RecyclerView.Adapter<OptionsAdapter.ViewHolder>() {
    // obf: C3442#4

    inner class ViewHolder(
        // obf: f13270
        val binding: ItemOptionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        // obf: C3751
        init {
            // obf: ViewOnClickListenerC2376 case 11
            binding.root.setOnClickListener {
                onOptionSelected(options[absoluteAdapterPosition])
            }
        }
    }

    override fun getItemCount(): Int = options.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val option = options[position]
        val binding = holder.binding
        // obf: C3025.m5426 (imageLoader) + C3779 (ImageRequest.Builder) = Coil `load`
        binding.icon.load(option.icon)
        binding.title.text = option.title
    }
}

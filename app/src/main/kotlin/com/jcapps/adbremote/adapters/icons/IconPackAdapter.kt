package com.jcapps.adbremote.adapters.icons

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.elevation.SurfaceColors
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ItemIconPackAddBinding
import com.jcapps.adbremote.databinding.ItemIconLabelBinding
import com.jcapps.adbremote.model.IconPack

/** Icon packs grid of [com.jcapps.adbremote.dialogs.icons.DialogIconPackChooser]; last item (info == null) is "add". */
class IconPackAdapter(
    context: Context,
    // obf: f6025
    private var selectedIconPack: String,
    // obf: f6023
    private val onSelected: (IconPack) -> Unit,
    // obf: f6027
    private val onAddClicked: () -> Unit,
) : ListAdapter<IconPack, RecyclerView.ViewHolder>(DiffCallback) {
    // obf: C1242

    // obf: f6026
    private val strokeWidth: Int = context.resources.getDimension(R.dimen.icon_stroke_width).toInt()

    // obf: f6022
    private val selectedColor: Int = SurfaceColors.SURFACE_5.getColor(context)

    override fun getItemViewType(position: Int): Int = if (getItem(position).info == null) TYPE_ADD else TYPE_ICON_PACK

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        TYPE_ADD -> {
            val binding = ItemIconPackAddBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            AddViewHolder(binding)
        }
        TYPE_ICON_PACK -> IconPackViewHolder(ItemIconLabelBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        else -> throw IllegalStateException("Unexpected view type: $viewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val iconPack = getItem(position)
        if (holder is IconPackViewHolder) {
            holder.bind(iconPack!!)
        }
    }

    // obf: C3565
    inner class AddViewHolder(binding: ItemIconPackAddBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            // obf: ViewOnClickListenerC2219 case 6
            binding.root.setOnClickListener { onAddClicked() }
        }
    }

    // obf: C1547
    inner class IconPackViewHolder(
        // obf: f6859
        private val binding: ItemIconLabelBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // obf: ViewOnClickListenerC2376 case 7
            binding.root.setOnClickListener {
                val iconPack = getItem(bindingAdapterPosition)!!
                select(iconPack)
            }
        }

        private fun select(iconPack: IconPack) {
            val info = iconPack.info!!
            val oldIndex = currentList.indexOfFirst { it.info!!.packageName == selectedIconPack }
            val packageName = info.packageName
            val newIndex = currentList.indexOfFirst { it.info!!.packageName == packageName }
            selectedIconPack = packageName
            if (oldIndex != newIndex) {
                notifyItemChanged(oldIndex, Unit)
            }
            notifyItemChanged(newIndex, Unit)
            onSelected(iconPack)
        }

        fun bind(icon: IconPack) {
            binding.icon.imageTintList = null
            val info = icon.info!!
            binding.icon.setImageDrawable(info.icon)
            binding.title.text = info.name
            if (selectedIconPack == info.packageName) {
                binding.root.setCardBackgroundColor(selectedColor)
                binding.root.strokeWidth = strokeWidth
            } else {
                binding.root.setCardBackgroundColor(0)
                binding.root.strokeWidth = 0
            }
        }
    }

    // obf: C2403 case 5
    private object DiffCallback : DiffUtil.ItemCallback<IconPack>() {
        override fun areItemsTheSame(oldItem: IconPack, newItem: IconPack): Boolean =
            oldItem.info?.packageName == newItem.info?.packageName

        override fun areContentsTheSame(oldItem: IconPack, newItem: IconPack): Boolean = oldItem == newItem
    }

    private companion object {
        const val TYPE_ADD = 1
        const val TYPE_ICON_PACK = 2
    }
}

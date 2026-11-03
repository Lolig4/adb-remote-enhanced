package com.jcapps.adbremote.adapters.icons

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.databinding.ItemIconShapeCustomBinding
import com.jcapps.adbremote.databinding.ItemIconShapeBinding
import com.jcapps.adbremote.model.IconShapeData
import com.jcapps.adbremote.utils.createShapeAppearanceModel

/** Icon shapes list of [com.jcapps.adbremote.dialogs.icons.DialogIconShapeChooser]; item id -1 is the custom shape. */
class IconShapeAdapter(
    // obf: f5957
    var isCustomShapeAvailable: Boolean,
    // obf: f5955
    private val cornerSize: Float,
    // obf: f5959
    var selectedId: Int,
    // obf: f5958
    private val icons: ArrayList<IconShapeData>,
    // obf: f5954
    private val onSelected: (IconShapeData) -> Unit,
    // obf: f5956
    private val onCustomizeClicked: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    // obf: C1205

    override fun getItemCount(): Int = icons.size

    override fun getItemViewType(position: Int): Int = if (icons[position].id == -1) TYPE_CUSTOM else TYPE_SHAPE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        TYPE_CUSTOM -> CustomIconShapeViewHolder(ItemIconShapeCustomBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        TYPE_SHAPE -> IconShapeViewHolder(ItemIconShapeBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        else -> throw IllegalStateException("Unexpected view type: $viewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val icon = icons[position]
        if (holder is IconShapeViewHolder) {
            holder.bind(icon)
        } else if (holder is CustomIconShapeViewHolder) {
            holder.bind(icon)
        }
    }

    // obf: C1639
    inner class IconShapeViewHolder(
        // obf: f7129
        private val binding: ItemIconShapeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // obf: ViewOnClickListenerC2376 case 6
            binding.root.setOnClickListener { onSelected(icons[bindingAdapterPosition]) }
        }

        fun bind(icon: IconShapeData) {
            binding.radio.isChecked = icon.id == selectedId
            binding.textIconShapeTitle.setText(icon.title)
            binding.icon.shapeAppearanceModel = createShapeAppearanceModel(icon.shape, cornerSize)
            binding.icon.imageTintList = null
        }
    }

    // obf: C3550
    inner class CustomIconShapeViewHolder(
        // obf: f12627
        private val binding: ItemIconShapeCustomBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // obf: ViewOnClickListenerC2219 case 4
            binding.btnCustomizeIconShape.setOnClickListener { onCustomizeClicked() }
            // obf: ViewOnClickListenerC2376 case 5
            binding.root.setOnClickListener { onSelected(icons[bindingAdapterPosition]) }
        }

        fun bind(icon: IconShapeData) {
            if (!isCustomShapeAvailable) {
                binding.imagePreview.visibility = View.GONE
                return
            }
            binding.imagePreview.visibility = View.VISIBLE
            binding.radio.isChecked = icon.id == selectedId
            binding.textIconShapeTitle.setText(icon.title)
            binding.icon.shapeAppearanceModel = createShapeAppearanceModel(icon.shape, cornerSize)
            binding.icon.imageTintList = null
        }
    }

    private companion object {
        const val TYPE_CUSTOM = 1
        const val TYPE_SHAPE = 2
    }
}

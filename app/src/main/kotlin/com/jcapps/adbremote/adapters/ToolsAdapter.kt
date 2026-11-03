package com.jcapps.adbremote.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.ShapeAppearanceModel
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ItemToolBinding
import com.jcapps.adbremote.model.Tool
import com.jcapps.adbremote.model.ToolType
import com.jcapps.adbremote.utils.createShapeAppearanceModel
import com.jcapps.adbremote.utils.getIconShape

class ToolsAdapter(
    // obf: f5781
    val context: Context,
    // obf: f5780
    private val tools: List<Tool>,
    // obf: f5783
    private val onClicked: (ToolType) -> Unit,
) : RecyclerView.Adapter<ToolsAdapter.ViewHolder>() {
    // obf: C1137

    // obf: f5782
    val cornerSize: Float = context.resources.getDimension(R.dimen.fav_icon_height)

    // obf: f5779
    var iconShape: ShapeAppearanceModel = createShapeAppearanceModel(getIconShape(context).shape, cornerSize)

    inner class ViewHolder(
        // obf: f4034
        val binding: ItemToolBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        // obf: C0481
        init {
            // obf: ViewOnClickListenerC2376 case 16 (default)
            binding.root.setOnClickListener {
                onClicked(tools[absoluteAdapterPosition].type)
            }
        }
    }

    override fun getItemCount(): Int = tools.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemToolBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tool = tools[position]
        val binding = holder.binding
        binding.title.setText(tool.name)
        val icon = binding.icon
        icon.setImageResource(tool.icon)
        icon.imageTintList = ContextCompat.getColorStateList(icon.context, tool.iconColor)
        icon.shapeAppearanceModel = iconShape
    }
}

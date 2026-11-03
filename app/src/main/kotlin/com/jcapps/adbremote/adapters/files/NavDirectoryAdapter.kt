package com.jcapps.adbremote.adapters.files

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ItemNavDirectoryBinding
import com.jcapps.adbremote.model.NavDirectory
import com.jcapps.adbremote.utils.getColorFromAttr

/**
 * Breadcrumb adapter showing the current [NavDirectory] trail at the top of the Files screen
 * (item layout cc -> ItemNavDirectoryBinding, a single MaterialButton). Clicking an entry selects it and drops
 * every entry after it.
 */
class NavDirectoryAdapter(
    context: Context,
    // obf: f4578
    private val onSelected: (NavDirectory) -> Unit,
) : RecyclerView.Adapter<NavDirectoryAdapter.ViewHolder>() {
    // obf: C0696

    // obf: f4576
    private val selectedColor: Int = context.getColorFromAttr(android.R.attr.colorPrimary)

    // obf: f4580
    private val textColor: Int = context.getColorFromAttr(android.R.attr.textColorPrimary)

    // obf: f4579
    private val padding: Int = context.resources.getDimension(R.dimen.nav_directory_padding).toInt()

    // obf: f4575
    private val firstPadding: Int = context.resources.getDimension(R.dimen.nav_directory_first_padding).toInt()

    // obf: f4577 — the breadcrumb entries
    var directories: MutableList<NavDirectory> = ArrayList()

    override fun getItemCount(): Int = directories.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemNavDirectoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val directory = directories[position]
        val isFirst = position == 0
        val isLast = directories.size == position + 1
        val button = holder.binding.root
        button.text = directory.name
        button.setPadding(if (isFirst) firstPadding else padding, 0, padding, 0)
        button.setTextColor(if (isLast) selectedColor else textColor)
        if (!isLast) {
            button.setIconResource(R.drawable.ic_arrow_right)
        }
        holder.setIsRecyclable(false)
    }

    // obf: C2250
    inner class ViewHolder(
        // obf: f8880
        val binding: ItemNavDirectoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            // obf: ViewOnClickListenerC2376 case 14
            binding.root.setOnClickListener {
                val position = absoluteAdapterPosition
                val count = position + 1
                if (directories.size != count) {
                    onSelected(directories[position])
                    directories = directories.subList(0, count)
                    notifyDataSetChanged()
                }
            }
        }
    }
}

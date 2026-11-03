package com.jcapps.adbremote.adapters.icons

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.ShapeAppearanceModel
import com.jcapps.adbremote.R
import com.jcapps.adbremote.dao.Fav
import com.jcapps.adbremote.databinding.ItemFavAddBinding
import com.jcapps.adbremote.databinding.ItemFavAppBinding
import com.jcapps.adbremote.model.App
import com.jcapps.adbremote.utils.AppIconLoader
import com.jcapps.adbremote.utils.createShapeAppearanceModel
import com.jcapps.adbremote.utils.getIconShape
import com.jcapps.adbremote.utils.loadAppIcon

/**
 * Favourite-apps grid of the main screen (icons drawn with the chosen icon shape / icon pack);
 * the [Fav.Add] item opens the app picker.
 *
 * NOTE: despite the registry name this is MainActivity's favourites adapter (only user: C1204).
 */
class IconShapePreviewAdapter(
    // obf: f6621
    val context: Context,
) : ListAdapter<Fav, RecyclerView.ViewHolder>(DiffCallback) {
    // obf: C1444

    // obf: f6619
    val cornerSize: Float = context.resources.getDimension(R.dimen.fav_icon_height)

    // obf: f6623
    private val density: Int = context.resources.displayMetrics.density.toInt()

    // obf: f6618
    private val strokeWidth: Float = context.resources.getDimension(R.dimen.icon_stroke_width)

    // obf: f6620
    private val iconBackgroundColor: Int = ContextCompat.getColor(context, R.color.white)

    // obf: f6624
    lateinit var iconShape: ShapeAppearanceModel

    // obf: f6613
    val appIconLoader = AppIconLoader(context)

    // obf: f6614 (C2247 case 12)
    private val layoutInflater: LayoutInflater by lazy { LayoutInflater.from(context) }

    // obf: f6615
    var onItemClicked: ((App) -> Unit)? = null

    // obf: f6616
    var onItemLongClicked: ((App) -> Unit)? = null

    // obf: f6622
    var onAddClicked: (() -> Unit)? = null

    init {
        iconShape = createShapeAppearanceModel(getIconShape(context).shape, cornerSize)
    }

    override fun getItemViewType(position: Int): Int = if (getItem(position) is Fav.Add) TYPE_ADD else TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        TYPE_ADD -> AddViewHolder(ItemFavAddBinding.inflate(layoutInflater, parent, false))
        TYPE_APP -> AppViewHolder(ItemFavAppBinding.inflate(layoutInflater, parent, false))
        else -> throw IllegalStateException("Unexpected view type: $viewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AppViewHolder) {
            val data = getItem(position) as Fav.Data
            holder.binding.icon.shapeAppearanceModel = iconShape
            holder.binding.title.text = data.app.name
            holder.binding.icon.loadAppIcon(data.app.packageName, appIconLoader, density, strokeWidth, iconBackgroundColor)
        } else if (holder is AddViewHolder) {
            holder.binding.icon.shapeAppearanceModel = iconShape
        }
    }

    // obf: C2022
    inner class AppViewHolder(
        // obf: f8257
        val binding: ItemFavAppBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            // obf: ViewOnClickListenerC2376 case 13
            binding.root.setOnClickListener {
                val data = getItem(bindingAdapterPosition) as Fav.Data
                onItemClicked?.invoke(data.app)
            }
            binding.root.setOnLongClickListener {
                val data = getItem(bindingAdapterPosition) as Fav.Data
                onItemLongClicked?.invoke(data.app)
                true
            }
        }
    }

    // obf: C3985
    inner class AddViewHolder(
        // obf: f13956
        val binding: ItemFavAddBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            // obf: ViewOnClickListenerC2219 case 10
            binding.root.setOnClickListener { onAddClicked?.invoke() }
        }
    }

    // obf: C2403 case 7
    private object DiffCallback : DiffUtil.ItemCallback<Fav>() {
        override fun areItemsTheSame(oldItem: Fav, newItem: Fav): Boolean {
            if (oldItem is Fav.Add && newItem is Fav.Add) return true
            if (oldItem is Fav.Data && newItem is Fav.Data) return oldItem.app.packageName == newItem.app.packageName
            return false
        }

        override fun areContentsTheSame(oldItem: Fav, newItem: Fav): Boolean {
            if (oldItem is Fav.Add && newItem is Fav.Add) return true
            if (oldItem is Fav.Data && newItem is Fav.Data) return oldItem.app == newItem.app
            return false
        }
    }

    private companion object {
        const val TYPE_ADD = 1
        const val TYPE_APP = 2
    }
}

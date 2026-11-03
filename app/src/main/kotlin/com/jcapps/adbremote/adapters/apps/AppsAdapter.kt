package com.jcapps.adbremote.adapters.apps

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.util.Pair
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.ShapeAppearanceModel
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ItemAppBinding
import com.jcapps.adbremote.model.App
import com.jcapps.adbremote.utils.AppIconLoader
import com.jcapps.adbremote.utils.createShapeAppearanceModel
import com.jcapps.adbremote.utils.getIconShape
import com.jcapps.adbremote.utils.loadAppIcon

/**
 * Installed / running apps list (AppsActivity) and the favourite-apps picker (MainActivity).
 * (The original interleaved native-ad rows; ads were removed from this build.)
 */
class AppsAdapter(
    // obf: f10811
    val context: Context,
    // obf: f10808
    val showFavButton: Boolean,
    /** (add, app, position): add = app is not yet a favourite. */
    // obf: f10814 (C0613 case 0 in AppsActivity; null from MainActivity)
    val onFavClicked: ((Boolean, App, Int) -> Unit)?,
    /** (sharedElements, app, position, isFav). */
    // obf: f10813 (C3704 case 0 AppsActivity / case 2 MainActivity)
    val onClicked: (List<Pair<View, String>>, App, Int, Boolean) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    // obf: C2995

    // obf: f10807
    val iconSize: Float = context.resources.getDimension(R.dimen.fav_icon_height)

    // obf: f10802
    val density: Int = context.resources.displayMetrics.density.toInt()

    // obf: f10804
    val strokeWidth: Float = context.resources.getDimension(R.dimen.icon_stroke_width)

    // obf: f10805
    val backgroundColor: Int = ContextCompat.getColor(context, R.color.white)


    // obf: f10810
    lateinit var iconShape: ShapeAppearanceModel

    /** Package names of the current device's favourite apps. */
    // obf: f10803
    var favApps: List<String> = emptyList()

    // obf: f10806
    val appIconLoader: AppIconLoader = AppIconLoader(context)

    // obf: f10809 (C1523 = AsyncListDiffer, diff = C2403 case 0)
    val differ: AsyncListDiffer<App> = AsyncListDiffer(
        this,
        object : DiffUtil.ItemCallback<App>() {
            override fun areItemsTheSame(oldItem: App, newItem: App): Boolean =
                oldItem.isAd == newItem.isAd &&
                    (oldItem.packageName == newItem.packageName || oldItem.adPosition == newItem.adPosition)

            override fun areContentsTheSame(oldItem: App, newItem: App): Boolean = oldItem == newItem
        },
    )

    init {
        iconShape = createShapeAppearanceModel(getIconShape(context).shape, iconSize)
    }

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0 -> AppInfoViewHolder(
                this,
                ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )
            else -> throw IllegalStateException()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val payload = payloads.firstOrNull()
        if (payload !is Boolean) {
            onBindViewHolder(holder, position)
        } else if (holder is AppInfoViewHolder) {
            holder.binding.btnFavoriteOutline.setIconResource(if (payload) R.drawable.ic_favorite else R.drawable.ic_favorite_outline)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val app = differ.currentList[position]
        if (holder is AppInfoViewHolder) {
            // obf: C0448 bind (inlined)
            with(holder.binding) {
                imageAppIcon.shapeAppearanceModel = iconShape
                title.text = app.name
                textAppVersionName.text = textAppVersionName.context.getString(R.string.text, app.versionName, app.versionCode)
                btnFavoriteOutline.visibility = if (showFavButton) View.VISIBLE else View.GONE
                btnFavoriteOutline.setIconResource(if (favApps.contains(app.packageName)) R.drawable.ic_favorite else R.drawable.ic_favorite_outline)
                imageTv.visibility = if (app.isTvSupported) View.VISIBLE else View.GONE
                imageSettings.visibility = if (app.isSystemApp) View.VISIBLE else View.GONE
                imageDisabled.visibility = if (app.isEnabled) View.GONE else View.VISIBLE
                imageAppIcon.loadAppIcon(app.packageName, appIconLoader, density, strokeWidth, backgroundColor)
                ViewCompat.setTransitionName(title, "title_" + app.packageName)
                ViewCompat.setTransitionName(imageAppIcon, "icon_" + app.packageName)
                ViewCompat.setTransitionName(textAppVersionName, "version_" + app.packageName)
            }
        }
    }
}

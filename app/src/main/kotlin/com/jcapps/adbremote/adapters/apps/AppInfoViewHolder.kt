package com.jcapps.adbremote.adapters.apps

import androidx.core.util.Pair
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.databinding.ItemAppBinding

/**
 * App row of [AppsAdapter] (layout c4).
 *
 * RECON-UNSURE: in the original this was an `inner class` of [AppsAdapter] (synthetic outer
 * reference f3916); the registry makes it top-level, so the adapter is a constructor parameter.
 */
class AppInfoViewHolder(
    // obf: f3916
    val adapter: AppsAdapter,
    // obf: f3915
    val binding: ItemAppBinding,
) : RecyclerView.ViewHolder(binding.root) {
    // obf: C0448

    // m4692 = getAbsoluteAdapterPosition() (same body as the deprecated getAdapterPosition())
    init {
        binding.btnFavoriteOutline.setOnClickListener {
            val app = adapter.differ.currentList[absoluteAdapterPosition]
            val isFav = adapter.favApps.contains(app.packageName)
            adapter.onFavClicked?.invoke(!isFav, app, absoluteAdapterPosition)
        }
        binding.root.setOnClickListener {
            val app = adapter.differ.currentList[absoluteAdapterPosition]
            adapter.onClicked(
                listOf(
                    Pair(binding.title, "title_" + app.packageName),
                    Pair(binding.imageAppIcon, "icon_" + app.packageName),
                    Pair(binding.textAppVersionName, "version_" + app.packageName),
                ),
                app,
                absoluteAdapterPosition,
                adapter.favApps.contains(app.packageName),
            )
        }
    }
}

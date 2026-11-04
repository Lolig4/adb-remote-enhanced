package com.jcapps.adbremote.ui.install

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.dispose
import coil.request.ImageRequest
import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ItemInstallAppBinding
import com.jcapps.adbremote.databinding.ItemInstallHeaderBinding

/** Rebuild addition: rows of the sideload lists. */
sealed class InstallItem {
    data class Header(
        val title: String,
        val hint: String? = null,
        val action: String? = null,
        val onAction: (() -> Unit)? = null,
    ) : InstallItem()

    data class Entry(
        val title: String,
        val subtitle: String,
        val badge: String?,
        val icon: Drawable?,
        val iconUrl: String?,
        val source: ApkSource,
    ) : InstallItem()
}

class InstallAdapter(
    private val imageLoader: ImageLoader,
    private val onClick: (ApkSource) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var items: List<InstallItem> = emptyList()
        set(value) {
            field = value
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = if (items[position] is InstallItem.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderHolder(ItemInstallHeaderBinding.inflate(inflater, parent, false))
        } else {
            EntryHolder(ItemInstallAppBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is InstallItem.Header -> with((holder as HeaderHolder).binding) {
                headerTitle.text = item.title
                headerHint.text = item.hint
                headerHint.visibility = if (item.hint != null) View.VISIBLE else View.GONE
                headerAction.text = item.action
                headerAction.visibility = if (item.action != null) View.VISIBLE else View.GONE
                headerAction.setOnClickListener { item.onAction?.invoke() }
            }
            is InstallItem.Entry -> with((holder as EntryHolder).binding) {
                appTitle.text = item.title
                appSubtitle.text = item.subtitle
                appBadge.text = item.badge
                appBadge.visibility = if (item.badge != null) View.VISIBLE else View.GONE
                if (item.iconUrl != null) {
                    imageLoader.enqueue(
                        ImageRequest.Builder(root.context)
                            .data(item.iconUrl)
                            .addHeader("token", NativeConfig.apiInterceptorToken)
                            .placeholder(R.drawable.ic_app_placeholder)
                            .error(R.drawable.ic_app_placeholder)
                            .target(appIcon)
                            .build(),
                    )
                } else {
                    // Cancel a still-running TV icon load of the recycled row, else it overwrites this icon.
                    appIcon.dispose()
                    appIcon.setImageDrawable(item.icon ?: root.context.getDrawable(R.drawable.ic_app_placeholder))
                }
                root.setOnClickListener { onClick(item.source) }
            }
        }
    }

    class HeaderHolder(val binding: ItemInstallHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class EntryHolder(val binding: ItemInstallAppBinding) : RecyclerView.ViewHolder(binding.root)
}

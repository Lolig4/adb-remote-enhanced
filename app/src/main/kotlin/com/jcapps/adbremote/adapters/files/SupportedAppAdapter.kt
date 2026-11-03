package com.jcapps.adbremote.adapters.files

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ItemIconLabelBinding
import com.jcapps.adbremote.model.SupportedApp
import com.jcapps.adbremote.utils.AppIconLoader
import com.jcapps.adbremote.utils.loadAppIcon

/**
 * Adapter listing the device's [SupportedApp]s to pick for "open remotely with"
 * ([com.jcapps.adbremote.dialogs.files.DialogOpenRemotelyWith]); item layout ce -> ItemIconLabelBinding.
 *
 * RECON-UNSURE: in the original this was an inner class of DialogOpenRemotelyWith (C1481) reading
 * the dialog's AppIconLoader (f6696) and invoking its selection callback (f6695) + dismiss() on
 * click. Here those are passed in as [appIconLoader] and [onClicked].
 */
class SupportedAppAdapter(
    context: Context,
    private val appIconLoader: AppIconLoader,
    // obf: click body = f6695?.invoke(app); dismiss()
    private val onClicked: (SupportedApp) -> Unit,
) : RecyclerView.Adapter<SupportedAppAdapter.ViewHolder>() {
    // obf: C0988

    // obf: f5338
    private val density: Int = context.resources.displayMetrics.density.toInt()

    // obf: f5337
    private val strokeWidth: Float = context.resources.getDimension(R.dimen.icon_stroke_width)

    // obf: f5340
    private val backgroundColor: Int = ContextCompat.getColor(context, R.color.white)

    // obf: f5339 (diff callback = C2403 case 6)
    val differ: AsyncListDiffer<SupportedApp> = AsyncListDiffer(
        this,
        object : DiffUtil.ItemCallback<SupportedApp>() {
            override fun areItemsTheSame(oldItem: SupportedApp, newItem: SupportedApp): Boolean =
                oldItem.packageName == newItem.packageName && oldItem.componentName == newItem.componentName

            override fun areContentsTheSame(oldItem: SupportedApp, newItem: SupportedApp): Boolean =
                oldItem == newItem
        },
    )

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemIconLabelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = differ.currentList[position]
        val binding = holder.binding
        binding.icon.imageTintList = null
        binding.icon.loadAppIcon(app.packageName, appIconLoader, density, strokeWidth, backgroundColor)
        binding.title.text = app.name
    }

    // obf: C3008
    inner class ViewHolder(
        // obf: f10841
        val binding: ItemIconLabelBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            val card = binding.root
            card.setCardBackgroundColor(0)
            card.strokeWidth = 0
            card.setOnClickListener {
                val app = differ.currentList[absoluteAdapterPosition]
                onClicked(app)
            }
        }
    }
}

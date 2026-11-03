package com.jcapps.adbremote.adapters.files

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AdapterListUpdateCallback
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ItemProgressFileBinding
import com.jcapps.adbremote.model.Progress
import com.jcapps.adbremote.model.ProgressState
import com.jcapps.adbremote.model.UiProgressFile
import com.jcapps.adbremote.utils.formatFileSize
import java.util.Locale

/**
 * Adapter for the transfer progress sheet; items are [UiProgressFile] diffed via an
 * AsyncListDiffer (background executor). Partial updates arrive as [Progress] / [Error] payloads.
 * Holder = [ProgressFileViewHolder] (item layout cd).
 */
class ProgressFilesAdapter(context: Context) : RecyclerView.Adapter<ProgressFileViewHolder>() {
    // obf: C0528

    /** Change payload for an error-message update. RECON-UNSURE: placement (toString proves name). */
    // obf: C4011
    data class Error(
        // obf: f14018
        val errorMessage: String?,
        // obf: f14017
        val state: ProgressState,
    )

    // obf: f4108 (C3079 case 8)
    val locale: Lazy<Locale> = lazy { Locale.getDefault() }

    // obf: f4109 (diff callback = C2403 case 9)
    val differ: AsyncListDiffer<UiProgressFile> = AsyncListDiffer(
        AdapterListUpdateCallback(this),
        AsyncDifferConfig.Builder(
            object : DiffUtil.ItemCallback<UiProgressFile>() {
                override fun areItemsTheSame(oldItem: UiProgressFile, newItem: UiProgressFile): Boolean =
                    oldItem.file.id == newItem.file.id

                override fun areContentsTheSame(oldItem: UiProgressFile, newItem: UiProgressFile): Boolean =
                    oldItem == newItem

                override fun getChangePayload(oldItem: UiProgressFile, newItem: UiProgressFile): Any? {
                    if (oldItem.processedSize != newItem.processedSize || oldItem.percent != newItem.percent) {
                        return Progress(newItem.processedSize, newItem.percent, newItem.state)
                    }
                    if (oldItem.error == newItem.error) {
                        return null
                    }
                    return Error(newItem.error, newItem.state)
                }
            },
        ).build(),
    ).apply {
        // obf: C2945 (empty list listener)
        addListListener { _, _ -> }
    }

    // obf: f4110 — pending/pause icon tint (theme attr gq)
    val tintColor: Int = TypedValue().let { typedValue ->
        context.theme.resolveAttribute(R.attr.colorControlNormal, typedValue, true)
        val resourceId = typedValue.resourceId
        if (resourceId == 0) typedValue.data else resourceId
    }

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgressFileViewHolder =
        ProgressFileViewHolder(this, ItemProgressFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ProgressFileViewHolder, position: Int) {
        val item = differ.currentList[position]
        holder.setProgress(item.percent, item.processedSize)
        holder.setState(item.state)
        holder.setError(item.error)
        val file = item.file
        holder.binding.textAdbRemote.text = file.name
        holder.binding.textFileSize.text = formatFileSize(file.size)
    }

    override fun onBindViewHolder(holder: ProgressFileViewHolder, position: Int, payloads: MutableList<Any>) {
        when (val payload = payloads.lastOrNull()) {
            is Progress -> {
                holder.setProgress(payload.percent, payload.processedSize)
                holder.setState(payload.state)
                holder.setError(null)
            }
            is Error -> {
                holder.setError(payload.errorMessage)
                holder.setState(payload.state)
            }
            else -> onBindViewHolder(holder, position)
        }
    }
}

package com.jcapps.adbremote.adapters.files

import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ItemProgressFileBinding
import com.jcapps.adbremote.model.ProgressState

/**
 * Transfer progress item holder (item layout cd -> ItemProgressFileBinding): progress indicator `pb` + percent
 * `v0`, processed-size label `uo`, optional error line `un`, and a state icon `sa`.
 */
class ProgressFileViewHolder(
    // obf: f7573 — owning progress adapter
    private val adapter: ProgressFilesAdapter,
    // obf: f7572
    val binding: ItemProgressFileBinding,
) : RecyclerView.ViewHolder(binding.root) {
    // obf: C1807

    // obf: m3991
    fun setProgress(percent: Int, processedSize: String) {
        binding.progress.setProgressCompat(percent, true)
        binding.textPercent.text = String.format(adapter.locale.value, "%d%%", percent)
        binding.textProcessedSize.text = processedSize
    }

    // obf: m3992
    fun setError(error: String?) {
        val errorView = binding.textError
        errorView.visibility = if (error == null) View.GONE else View.VISIBLE
        errorView.text = error
    }

    // obf: m3993
    fun setState(state: ProgressState) {
        val icon = binding.imageState
        when (state) {
            ProgressState.PENDING -> {
                icon.imageTintList = ContextCompat.getColorStateList(icon.context, adapter.tintColor)
                icon.setImageResource(R.drawable.ic_pending)
            }
            ProgressState.PAUSE -> {
                icon.imageTintList = ContextCompat.getColorStateList(icon.context, adapter.tintColor)
                icon.setImageResource(R.drawable.ic_pause)
            }
            ProgressState.COMPLETED -> {
                icon.imageTintList = ContextCompat.getColorStateList(icon.context, R.color.success)
                icon.setImageResource(R.drawable.ic_check_circle)
            }
            ProgressState.ERROR -> {
                icon.imageTintList = ContextCompat.getColorStateList(icon.context, R.color.error)
                icon.setImageResource(R.drawable.ic_error)
            }
        }
    }
}

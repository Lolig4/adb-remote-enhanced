package com.jcapps.adbremote.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ItemCpuBinding
import com.jcapps.adbremote.model.Performance

/**
 * Per-core CPU usage cards of DialogPerformance (item layout `ch`, proposed `item_cpu`).
 * Claimed by group main-b. RECON-UNSURE: class names.
 */
class CpuAdapter(
    // obf: f3785
    private val context: Context,
) : RecyclerView.Adapter<CpuAdapter.ViewHolder>() {
    // obf: C0408

    // obf: f3784 (item callback = C3174)
    val differ = AsyncListDiffer(
        this,
        object : DiffUtil.ItemCallback<Performance.CPU>() {
            override fun areItemsTheSame(oldItem: Performance.CPU, newItem: Performance.CPU): Boolean =
                oldItem.name == newItem.name

            override fun areContentsTheSame(oldItem: Performance.CPU, newItem: Performance.CPU): Boolean =
                oldItem == newItem
        },
    )

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCpuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cpu = differ.currentList[position]
        val binding = holder.binding
        binding.textCpuName.text = if (position == 0) context.getString(R.string.cpu_usage) else context.getString(R.string.core, position)
        binding.progress.setProgressCompat(cpu.percent, true)
        binding.textCpuPercent.text = context.getString(R.string.text_2, cpu.percent)
    }

    class ViewHolder(
        // obf: f5394
        val binding: ItemCpuBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        // obf: C1005
    }
}

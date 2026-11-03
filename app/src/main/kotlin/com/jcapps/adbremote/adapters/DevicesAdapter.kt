package com.jcapps.adbremote.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.ItemDeviceBinding
import com.jcapps.adbremote.model.AdbDevice

/**
 * Saved ADB devices list of DialogDevices (item layout `c8`, proposed `item_device`).
 *
 * Claimed by group main-b. NOTE: group `files` claimed the same alias C0779 as
 * `FilesGridAdapter` (and the registry maps holder C1776 to `files.FileGridViewHolder`); the
 * decompiled code is unambiguously this AdbDevice adapter (param names onSelected/onEdit/onDelete,
 * only user C0881 = DialogDevices).
 */
class DevicesAdapter(
    // obf: f4797
    val onSelected: (Int, AdbDevice) -> Unit,
    // obf: f4796
    val onEdit: (Int, AdbDevice) -> Unit,
    // obf: f4799
    val onDelete: (Int, AdbDevice) -> Unit,
) : RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {
    // obf: C0779

    // obf: f4798 (item callback = C2403 case 4)
    val differ = AsyncListDiffer(
        this,
        object : DiffUtil.ItemCallback<AdbDevice>() {
            override fun areItemsTheSame(oldItem: AdbDevice, newItem: AdbDevice): Boolean =
                oldItem.ipAddress == newItem.ipAddress

            override fun areContentsTheSame(oldItem: AdbDevice, newItem: AdbDevice): Boolean =
                oldItem == newItem
        },
    )

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = differ.currentList[position]
        val binding = holder.binding
        binding.textAdbRemote.text = device.name
        binding.textDeviceAddress.text = device.ipAddress + ':' + device.port
        val icon = binding.imageCheckCircle
        icon.setImageResource(if (device.isDefault) R.drawable.ic_check_circle else R.drawable.ic_radio_unchecked)
        val tint = if (device.isDefault) R.color.status_connected else R.color.status_disconnected
        icon.imageTintList = ContextCompat.getColorStateList(icon.context, tint)
    }

    inner class DeviceViewHolder(
        // obf: f7496
        val binding: ItemDeviceBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        // obf: C1776

        init {
            binding.root.setOnClickListener {
                val position = absoluteAdapterPosition
                onSelected(position, differ.currentList[position])
            }
            binding.btnMore.setOnClickListener {
                val position = absoluteAdapterPosition
                val device = differ.currentList[position]
                val popupMenu = PopupMenu(binding.btnMore.context, binding.btnMore)
                popupMenu.menuInflater.inflate(R.menu.device_item, popupMenu.menu)
                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_edit -> onEdit(position, device!!)
                        R.id.action_delete -> onDelete(position, device!!)
                    }
                    true // RECON-UNSURE: listener return value was optimized away (void in dex)
                }
                popupMenu.show()
            }
        }
    }
}

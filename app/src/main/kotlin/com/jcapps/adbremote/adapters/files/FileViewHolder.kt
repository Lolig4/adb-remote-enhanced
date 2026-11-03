package com.jcapps.adbremote.adapters.files

import androidx.recyclerview.widget.RecyclerView
import com.jcapps.adbremote.databinding.ItemFileBinding

/**
 * List item holder (item layout cb -> ItemFileBinding). The card click opens the file / navigates into a
 * directory; the overflow button `dn` shows the per-file action menu (R.menu.files_item). Both bodies are
 * FilesActivity methods (R8 inlined them into the click lambdas).
 */
class FileViewHolder(
    // obf: f6483 — owning adapter
    private val adapter: FilesAdapter,
    // obf: f6482
    val binding: ItemFileBinding,
) : RecyclerView.ViewHolder(binding.root) {
    // obf: C1399

    init {
        // obf: card onClick (lambda case 0)
        binding.root.setOnClickListener {
            val file = adapter.differ.currentList[absoluteAdapterPosition]
            adapter.listener.onFileClicked(file)
        }
        // obf: btnMenu onClick (lambda case 1)
        binding.btnMoreVert.setOnClickListener {
            val btnMenu = binding.btnMoreVert
            val file = adapter.differ.currentList[absoluteAdapterPosition]
            adapter.listener.onFileMenuClicked(btnMenu, file)
        }
    }
}

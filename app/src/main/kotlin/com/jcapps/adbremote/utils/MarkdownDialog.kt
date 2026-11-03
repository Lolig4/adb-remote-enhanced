package com.jcapps.adbremote.utils

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.DialogMarkdownBinding
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads the asset [fileName] on IO and shows it rendered as markdown in a dialog. */
// obf: C0431 (IO part) + C1855 (Main part)
fun showMarkdownDialog(scope: CoroutineScope, context: Context, fileName: String) {
    scope.launch(Dispatchers.IO) {
        val markdown = runCatching {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        withContext(Dispatchers.Main) {
            val binding = DialogMarkdownBinding.inflate(LayoutInflater.from(context))
            // obf: C0554 case 1 (app plugin) + case 0 (RECON-UNSURE: library plugin requiring CorePlugin -> LinkifyPlugin)
            val markwon = Markwon.builder(context)
                .usePlugin(object : AbstractMarkwonPlugin() {
                    override fun configureTheme(builder: MarkwonTheme.Builder) {
                        builder.bulletWidth(16)
                    }
                })
                .usePlugin(LinkifyPlugin.create())
                .build()
            markwon.setMarkdown(binding.textContent, markdown)
            MaterialAlertDialogBuilder(context)
                .setView(binding.root)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }
}

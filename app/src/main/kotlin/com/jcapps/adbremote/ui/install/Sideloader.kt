package com.jcapps.adbremote.ui.install

import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textview.MaterialTextView
import com.jcapps.adbremote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Rebuild addition: the install flow of the sideload screen —
 * confirm → (downgrade?) → transfer to the TV → install → result.
 */
class Sideloader(private val activity: InstallActivity) {

    /** Called after every successful install (to refresh lists). */
    var onInstalled: (() -> Unit)? = null

    private fun api(): SideloadApi? =
        PreferenceManager.getDefaultSharedPreferences(activity).getString("current_device_ip", null)?.let { SideloadApi(it) }

    fun start(source: ApkSource) {
        val version = source.versionName ?: activity.getString(R.string.install_unknown_version)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.install_confirm_title)
            .setMessage(activity.getString(R.string.install_confirm_message, source.label, version))
            .setNegativeButton(R.string.cancel) { _, _ -> discard(source) }
            .setOnCancelListener { discard(source) }
            .setPositiveButton(R.string.install_confirm_button) { _, _ -> checkDowngrade(source) }
            .show()
    }

    private fun checkDowngrade(source: ApkSource) {
        val api = api() ?: return fail(source, activity.getString(R.string.install_tv_unreachable))
        val packageName = source.packageName
        if (packageName == null || source.versionCode < 0) {
            transfer(source, api, allowDowngrade = false)
            return
        }
        activity.lifecycleScope.launch {
            val installed = withContext(Dispatchers.IO) { runCatching { api.installedVersion(packageName) }.getOrNull() }
            if (installed != null && installed.first > source.versionCode) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.install_downgrade_title)
                    .setMessage(
                        activity.getString(
                            R.string.install_downgrade_message,
                            source.label,
                            installed.second ?: installed.first.toString(),
                            source.versionName ?: source.versionCode.toString(),
                        ),
                    )
                    .setNegativeButton(R.string.cancel) { _, _ -> discard(source) }
                    .setOnCancelListener { discard(source) }
                    .setPositiveButton(R.string.install_downgrade_button) { _, _ -> transfer(source, api, allowDowngrade = true) }
                    .show()
            } else {
                transfer(source, api, allowDowngrade = false)
            }
        }
    }

    private fun transfer(source: ApkSource, api: SideloadApi, allowDowngrade: Boolean) {
        val progress = ProgressDialog()
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (source) {
                        is ApkSource.TvFile -> {
                            progress.installing()
                            api.install(listOf(source.path), allowDowngrade, null)
                        }
                        else -> {
                            val files: List<Pair<String, File>> = when (source) {
                                is ApkSource.PhoneApp -> source.files.map { it.name to it }
                                is ApkSource.PhoneFile -> listOf(sanitize(source.file.name) to source.file)
                                else -> emptyList()
                            }
                            val session = UUID.randomUUID().toString().take(12)
                            val total = files.sumOf { it.second.length() }.coerceAtLeast(1)
                            var done = 0L
                            val paths = files.map { (name, file) ->
                                val before = done
                                val path = api.upload(session, name, file) { sent -> progress.upload(((before + sent) * 100 / total).toInt()) }
                                done += file.length()
                                path
                            }
                            progress.installing()
                            api.install(paths, allowDowngrade, session)
                        }
                    }
                }
            }
            progress.dismiss()
            discard(source)
            result.onSuccess {
                MaterialAlertDialogBuilder(activity)
                    .setMessage(activity.getString(R.string.install_success, source.label))
                    .setPositiveButton(R.string.ok, null)
                    .show()
                onInstalled?.invoke()
            }.onFailure { e ->
                fail(source, e.message ?: e.toString())
            }
        }
    }

    private fun fail(source: ApkSource, message: String) {
        discard(source)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.install_failed_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /** Picked files are temporary copies in the cache; they are never listed and removed after use. */
    private fun discard(source: ApkSource) {
        if (source is ApkSource.PhoneFile && source.deleteAfter) source.file.delete()
    }

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** Non-cancelable progress dialog, updatable from any thread. */
    private inner class ProgressDialog {
        private val text = MaterialTextView(activity)
        private val bar = LinearProgressIndicator(activity).apply { max = 100 }
        private val dialog: AlertDialog

        init {
            val pad = (activity.resources.displayMetrics.density * 24).toInt()
            val layout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(pad, pad, pad, pad / 2)
                addView(text)
                addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = pad / 2 })
            }
            text.text = activity.getString(R.string.install_progress_upload, 0)
            dialog = MaterialAlertDialogBuilder(activity).setView(layout).setCancelable(false).show()
        }

        fun upload(percent: Int) = activity.runOnUiThread {
            text.text = activity.getString(R.string.install_progress_upload, percent)
            bar.isIndeterminate = false
            bar.setProgressCompat(percent, true)
        }

        fun installing() = activity.runOnUiThread {
            text.text = activity.getString(R.string.install_progress_install)
            bar.isIndeterminate = true
        }

        fun dismiss() = dialog.dismiss()
    }
}

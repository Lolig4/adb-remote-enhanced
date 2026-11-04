package com.jcapps.adbremote.ui.install

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.jcapps.adbremote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Rebuild addition: tab "Installed" — apps the user installed on this phone (no system apps). */
class InstalledAppsFragment : InstallListFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        load()
    }

    private fun load() {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext().applicationContext
            val items = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(0)
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != context.packageName }
                    .mapNotNull { app ->
                        runCatching {
                            val info = pm.getPackageInfo(app.packageName, 0)
                            val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
                            val files = listOf(File(app.sourceDir)) + (app.splitSourceDirs?.map { File(it) } ?: emptyList())
                            val label = app.loadLabel(pm).toString()
                            val size = Formatter.formatShortFileSize(context, files.sumOf { it.length() })
                            InstallItem.Entry(
                                title = label,
                                subtitle = listOfNotNull(info.versionName, size).joinToString(" · "),
                                badge = null,
                                icon = runCatching { app.loadIcon(pm) }.getOrNull(),
                                iconUrl = null,
                                source = ApkSource.PhoneApp(label, app.packageName, info.versionName, versionCode, files),
                            )
                        }.getOrNull()
                    }
                    .sortedBy { it.title.lowercase() }
            }
            showLoading(false)
            adapter.items = items.ifEmpty { listOf(InstallItem.Header(getString(R.string.install_no_apps))) }
        }
    }
}

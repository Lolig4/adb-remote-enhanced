package com.jcapps.adbremote.ui.install

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jcapps.adbremote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Rebuild addition: tab "APK's" — APK/bundle files in the Download folder of this phone and of
 * the TV, plus "Can't find APK?" (system file picker; the picked file is installed right away
 * and not added to the list).
 */
class ApkFilesFragment : InstallListFragment() {

    override val extraBottomPadding get() = (resources.displayMetrics.density * 72).toInt()

    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) installPicked(uri)
    }

    private val legacyStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { load() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.installPick.visibility = View.VISIBLE
        binding.installPick.setOnClickListener {
            pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip"))
        }
        host.addOnInstalledListener { if (isAdded) load() }
    }

    /** ViewPager2 resumes only the visible page, so this runs when the tab is opened. */
    override fun onResume() {
        super.onResume()
        if (!hasStorageAccess() && !host.storageAccessAsked) {
            host.storageAccessAsked = true
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.install_storage_title)
                .setMessage(R.string.install_storage_message)
                .setNegativeButton(R.string.install_storage_later, null)
                .setPositiveButton(R.string.install_storage_allow) { _, _ -> requestStorageAccess() }
                .show()
        }
        load()
    }

    private fun hasStorageAccess(): Boolean = if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            val pkg = Uri.parse("package:" + requireContext().packageName)
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyStoragePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun load() {
        if (adapter.items.isEmpty()) showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext().applicationContext
            val access = hasStorageAccess()
            val ip = PreferenceManager.getDefaultSharedPreferences(context).getString("current_device_ip", null)
            val phone = async(Dispatchers.IO) { if (access) phoneEntries() else emptyList() }
            val tv = async(Dispatchers.IO) {
                runCatching { ip?.let { tvEntries(SideloadApi(it)) } ?: error("no device") }
            }
            val phoneItems = phone.await()
            val tvResult = tv.await()
            if (!isAdded) return@launch
            val items = ArrayList<InstallItem>()
            items += when {
                !access -> InstallItem.Header(
                    getString(R.string.install_section_phone),
                    hint = getString(R.string.install_storage_needed),
                    action = getString(R.string.install_storage_button),
                    onAction = { requestStorageAccess() },
                )
                phoneItems.isEmpty() -> InstallItem.Header(getString(R.string.install_section_phone), hint = getString(R.string.install_empty_phone))
                else -> InstallItem.Header(getString(R.string.install_section_phone))
            }
            items += phoneItems
            val tvItems = tvResult.getOrNull()
            items += when {
                tvItems == null -> InstallItem.Header(getString(R.string.install_section_tv), hint = getString(R.string.install_tv_unreachable))
                tvItems.isEmpty() -> InstallItem.Header(getString(R.string.install_section_tv), hint = getString(R.string.install_empty_tv))
                else -> InstallItem.Header(getString(R.string.install_section_tv))
            }
            items += tvItems ?: emptyList()
            showLoading(false)
            adapter.items = items
        }
    }

    private fun phoneEntries(): List<InstallItem.Entry> {
        val context = requireContext().applicationContext
        val download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val temp = File(context.cacheDir, "apkinfo")
        return download.walkTopDown().maxDepth(3)
            .filter { it.isFile && it.extension.lowercase() in ApkInfoReader.EXTENSIONS }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val info = ApkInfoReader.read(file, context.packageManager, temp)
                InstallItem.Entry(
                    title = info.label,
                    subtitle = subtitle(info.versionName, file.length(), file.name),
                    badge = null,
                    icon = info.icon,
                    iconUrl = null,
                    source = ApkSource.PhoneFile(info.label, info.packageName, info.versionName, info.versionCode, file),
                )
            }.toList()
    }

    private fun tvEntries(api: SideloadApi): List<InstallItem.Entry> = api.listTvFiles().map { f ->
        val label = f.label ?: f.name
        val badge = when {
            f.installedVersionCode < 0 -> null
            f.versionCode > f.installedVersionCode -> getString(R.string.install_update)
            else -> getString(R.string.install_installed_on_tv)
        }
        InstallItem.Entry(
            title = label,
            subtitle = subtitle(f.versionName, f.size, f.name),
            badge = badge,
            icon = null,
            iconUrl = api.iconUrl(f.path),
            source = ApkSource.TvFile(label, f.packageName, f.versionName, f.versionCode, f.path),
        )
    }

    private fun subtitle(versionName: String?, size: Long, fileName: String) =
        listOfNotNull(versionName, Formatter.formatShortFileSize(requireContext(), size), fileName).joinToString(" · ")

    /** Copies the picked document into the cache and runs the normal install flow on that copy. */
    private fun installPicked(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext().applicationContext
            val source = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "picked.apk"
                    val dir = File(context.cacheDir, "picked").apply { deleteRecursively(); mkdirs() }
                    val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").let {
                        if (it.substringAfterLast('.', "").lowercase() in ApkInfoReader.EXTENSIONS) it else "$it.apk"
                    }
                    val file = File(dir, safeName)
                    context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                    val info = ApkInfoReader.read(file, context.packageManager, File(context.cacheDir, "apkinfo"))
                    ApkSource.PhoneFile(info.label, info.packageName, info.versionName, info.versionCode, file, deleteAfter = true)
                }.getOrNull()
            }
            if (source != null && isAdded) host.sideloader.start(source)
        }
    }
}

package com.jcapps.adbremote.ui.files

import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.jcapps.adbremote.R
import com.jcapps.adbremote.model.FileSorting
import com.jcapps.adbremote.prefs.JsonPrefs
import org.apache.commons.net.ftp.FTPFile
import java.util.Locale

/**
 * Persisted sort settings ("files_sorting") of the Files screen plus the mapping to the sort
 * menu items (R.menu.files) and the comparator used for listings.
 *
 * RECON-UNSURE: class/member names (unregistered class C3256, claimed by files).
 */
class FileSortingMenu(
    // obf: f11642
    val basePrefs: JsonPrefs,
) {
    // obf: C3256

    // obf: f11641
    val fileSorting: FileSorting

    // obf: f11644 — menu item id -> sort key
    val sortByItems: Map<Int, FileSorting.SortBy>

    // obf: f11643 — "ascending" checkable item
    val orderItemId: Int

    init {
        var sorting: FileSorting? = null
        val json = basePrefs.dataStore.getString("files_sorting", null)
        if (json != null) {
            try {
                // obf: C4045
                sorting = basePrefs.gson.fromJson(json, object : TypeToken<FileSorting>() {}.type)
            } catch (e: JsonSyntaxException) {
            }
        }
        fileSorting = sorting ?: FileSorting(FileSorting.SortBy.NAME)
        sortByItems = mapOf(
            R.id.action_sort_name to FileSorting.SortBy.NAME,
            R.id.action_sort_size to FileSorting.SortBy.SIZE,
            R.id.action_sort_last_modified to FileSorting.SortBy.LAST_MODIFIED,
        )
        orderItemId = R.id.action_ascending
    }

    /** Directories first, then by the selected key; reversed when not ascending. */
    // obf: m5807
    fun sort(directories: List<FTPFile>): List<FTPFile> {
        var comparator = compareBy<FTPFile>(
            // obf: C0728 case 6
            { !it.isDirectory },
            // obf: C2192 case 17
            {
                when (fileSorting.sortBy) {
                    FileSorting.SortBy.NAME -> it.name.lowercase(Locale.ROOT)
                    FileSorting.SortBy.SIZE -> it.size
                    FileSorting.SortBy.LAST_MODIFIED -> it.timestamp.timeInMillis
                }
            },
        )
        if (!fileSorting.orderAscending) {
            comparator = comparator.reversed()
        }
        return directories.sortedWith(comparator)
    }
}

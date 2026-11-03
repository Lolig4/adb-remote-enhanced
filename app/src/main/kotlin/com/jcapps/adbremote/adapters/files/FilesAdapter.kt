package com.jcapps.adbremote.adapters.files

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.jcapps.adbremote.databinding.ItemFileBinding
import com.jcapps.adbremote.provider.common.MimeType
import com.jcapps.adbremote.provider.common.MimeTypeIcon
import com.jcapps.adbremote.provider.common.suffix
import com.jcapps.adbremote.ui.files.FilesActivity
import com.jcapps.adbremote.utils.MimeTypes
import com.jcapps.adbremote.utils.formatFileSize
import com.jcapps.adbremote.utils.mimeType
import org.apache.commons.net.ftp.FTPFile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * List adapter for the Files screen; items are commons-net [FTPFile]s diffed via an
 * AsyncListDiffer. Formats timestamps relative to today / this year. Holder = [FileViewHolder]
 * (item layout cb -> ItemFileBinding).
 */
class FilesAdapter(
    // obf: f11638 — the Files screen (click/menu listener)
    val listener: FilesActivity,
) : RecyclerView.Adapter<FileViewHolder>() {
    // obf: C3254

    // obf: f11636
    val timeFormat: SimpleDateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    // obf: f11640
    val monthDayFormat: SimpleDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    // obf: f11639
    val monthDayYearFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    // obf: f11635
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)

    // obf: f11637 (diff callback = C2403 case 8)
    val differ: AsyncListDiffer<FTPFile> = AsyncListDiffer(
        this,
        object : DiffUtil.ItemCallback<FTPFile>() {
            override fun areItemsTheSame(oldItem: FTPFile, newItem: FTPFile): Boolean =
                oldItem.rawListing == newItem.rawListing

            override fun areContentsTheSame(oldItem: FTPFile, newItem: FTPFile): Boolean =
                oldItem.rawListing == newItem.rawListing
        },
    )

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder =
        FileViewHolder(this, ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val ftpFile = differ.currentList[position]
        val binding = holder.binding
        binding.textAdbRemote.text = ftpFile.name
        val mimeType = ftpFile.mimeType
        val icon = when {
            ftpFile.isDirectory -> MimeTypeIcon.DIRECTORY
            !ftpFile.isFile -> if (ftpFile.isSymbolicLink) MimeTypeIcon.SYMBOLIC_LINK else MimeTypeIcon.GENERIC
            else -> MimeTypes.iconByMimeType[mimeType]
                ?: MimeTypes.iconByGeneralType[mimeType.substring(0, mimeType.indexOf('/'))]
                ?: MimeType(mimeType).suffix?.let { MimeTypes.iconBySuffix[it] }
                ?: MimeTypeIcon.GENERIC
        }
        binding.icon.load(icon.resource)
        var info = ftpFile.timestamp.format()
        if (ftpFile.isFile) {
            info = formatFileSize(ftpFile.size) + ", " + info
        }
        binding.textFileInfo.text = info
    }

    /** Relative timestamp: time today, "MMM d" this year, else "MMM d, yyyy". RECON-UNSURE name. */
    private fun Calendar.format(): String = when {
        DateUtils.isToday(timeInMillis) -> timeFormat.format(time)
        get(Calendar.YEAR) == currentYear -> monthDayFormat.format(time)
        else -> monthDayYearFormat.format(time)
    }
}

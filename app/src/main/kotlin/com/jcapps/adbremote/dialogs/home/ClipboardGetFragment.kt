package com.jcapps.adbremote.dialogs.home

import android.content.ClipData
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.jcapps.adbremote.R
import com.jcapps.adbremote.api.remote.ApiClient
import com.jcapps.adbremote.databinding.DialogClipboardGetBinding
import com.jcapps.adbremote.ui.BaseFragment
import com.jcapps.adbremote.model.Feature
import com.jcapps.adbremote.utils.FeatureUsage
import com.jcapps.adbremote.utils.beginDelayedTransition
import com.jcapps.adbremote.utils.getClipboardManager
import com.jcapps.adbremote.utils.toUri
import com.jcapps.adbremote.views.State
import com.jcapps.adbremote.views.StateView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * "Get clipboard" tab: shows the device clipboard text (ug) or image (card ka / k9) and offers
 * copy (da), share (ec), save (eg, CreateDocument) and open actions.
 */
class ClipboardGetFragment : BaseFragment<DialogClipboardGetBinding>() {
    // obf: C2758

    /** Content type of the downloaded clipboard image. */
    // obf: f10229
    private var mimeType: String = "image/jpeg"

    // obf: f10230
    private var ipAddress: String? = null

    // obf: f10231 (result = C3733 case 10 -> C0546 on IO)
    private val saveImageLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri: Uri? ->
            // obf: C3733 case 10
            if (uri != null) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    // obf: C0546
                    runCatching {
                        requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                            FileInputStream(imageFile).use { it.copyTo(outputStream) }
                        }
                    }
                    val root = binding.root
                    Snackbar.make(root, root.resources.getText(R.string.clipboard_image_saved), Snackbar.LENGTH_SHORT).show()
                }
            }
        }

    /** Downloaded clipboard image in externalCacheDir/Clipboard (deleted in [onDestroyView]). */
    // obf: f10232
    private var imageFile: File? = null

    // obf: f10233 (lambda case 0) / getter m5182
    private val stateView: StateView by lazy { StateView(binding.includeViewState) }

    // obf: f10234 (lambda case 1)
    private val dataStore: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    // obf: mo3020 (C1308)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogClipboardGetBinding
        get() = DialogClipboardGetBinding::inflate

    /**
     * Downloads the clipboard image from the device server into a fresh file under
     * `externalCacheDir/Clipboard` (old files are deleted); null on any failure.
     */
    // obf: m5181 (continuation C1283)
    private suspend fun downloadImage(): File? = runCatching {
        val body = ApiClient.create(ipAddress).getClipboardImage()
        mimeType = body.contentType()?.toString() ?: "image/jpeg"
        // RECON-UNSURE: obf reads MediaType field f4308 — `mediaType` (full string) assumed
        if (!mimeType.startsWith("image/")) {
            throw IllegalAccessException("Failed to get clipboard image.")
        }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
        val dir = File(requireContext().externalCacheDir, "Clipboard")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${UUID.randomUUID()}.$extension")
        body.source().inputStream().use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        file
    }.getOrNull()

    override fun onDestroyView() {
        imageFile?.delete()
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ipAddress = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString("current_device_ip", null)
        // obf: anonymous click listener case 0 — copy
        binding.btnCopy.setOnClickListener {
            val clipboardManager = requireContext().getClipboardManager()
            val file = imageFile
            if (file == null) {
                val text = binding.textContent.text.toString()
                clipboardManager.setPrimaryClip(ClipData.newPlainText("text", text))
            } else {
                clipboardManager.setPrimaryClip(
                    ClipData.newUri(requireContext().contentResolver, "URI", file.toUri(requireContext())),
                )
            }
            val root = binding.root
            Snackbar.make(root, root.resources.getText(R.string.copied_clipboard), Snackbar.LENGTH_SHORT).show()
        }
        // obf: anonymous click listener case 1 — share
        binding.btnShare.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            if (imageFile == null) {
                intent.putExtra(Intent.EXTRA_TEXT, binding.textContent.text.toString())
                intent.type = "text/plain"
            } else {
                intent.putExtra(Intent.EXTRA_STREAM, imageFile!!.toUri(requireContext()))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.type = mimeType
            }
            startActivity(intent)
        }
        // obf: anonymous click listener case 2 — save
        binding.btnSave.setOnClickListener {
            val date = SimpleDateFormat("MMM_d_yyyy HH_mm_ss", Locale.getDefault()).format(Date())
            saveImageLauncher.launch("clipboard_" + date + '.' + imageFile!!.name.substringAfterLast('.', ""))
        }
        // obf: anonymous click listener case 3 — open
        binding.cardClipboardGet.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(imageFile?.toUri(requireContext()), mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        }
        // Actions stay hidden until the clipboard was loaded (save only for images)
        binding.btnSave.visibility = View.GONE
        setImageActionsVisible(false)
        stateView.setState(State.LOADING)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // obf: C0240
            try {
                val response = ApiClient.create(ipAddress).getClipboardText()
                if (response.status || response.data == null) {
                    withContext(Dispatchers.Main) {
                        // obf: C2058
                        stateView.setState(State.EMPTY)
                    }
                } else if (response.data.hasImage) {
                    imageFile = downloadImage()
                    FeatureUsage.trackFeatureUsage(dataStore, Feature.CLIPBOARD)
                    withContext(Dispatchers.Main) {
                        // obf: C3340
                        stateView.setState(if (imageFile == null) State.EMPTY else State.SUCCESS)
                        if (imageFile != null) {
                            setImageActionsVisible(true)
                            beginDelayedTransition()
                            binding.textContent.visibility = View.GONE
                            binding.cardClipboardGet.visibility = View.VISIBLE
                            binding.btnSave.visibility = View.VISIBLE
                            binding.imageImagePlaceholder.load(imageFile)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        // obf: C2156
                        setImageActionsVisible(true)
                        stateView.setState(State.SUCCESS)
                        binding.textContent.text = response.data.text
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // obf: C0979
                    stateView.setState(State.DISCONNECTED)
                }
            }
        }
    }

    /** Shows / hides the image-only actions (da, eg). */
    // obf: m5183
    private fun setImageActionsVisible(isVisible: Boolean) {
        binding.btnCopy.visibility = if (isVisible) View.VISIBLE else View.GONE
        binding.btnShare.visibility = if (isVisible) View.VISIBLE else View.GONE
    }
}

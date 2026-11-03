package com.jcapps.adbremote.dialogs.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.transition.TransitionManager
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.jcapps.adbremote.R
import com.jcapps.adbremote.api.remote.ApiClient
import com.jcapps.adbremote.databinding.DialogClipboardSetBinding
import com.jcapps.adbremote.ui.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.UnknownHostException

/** "Set clipboard" tab: send text (hl) or a picked image (card jx / k9) to the device clipboard. */
class ClipboardSetFragment : BaseFragment<DialogClipboardSetBinding>() {
    // obf: C1704

    /** RECON-UNSURE: name / nesting (claimed as ClipboardSetFragment.ClipboardType). */
    enum class ClipboardType {
        // obf: EnumC2026
        TEXT,
        IMAGE,
    }

    // obf: f7282
    private var type: ClipboardType = ClipboardType.TEXT

    // obf: f7283
    private var progressDrawable: IndeterminateDrawable<CircularProgressIndicatorSpec>? = null

    // obf: f7284
    private var imageUri: Uri? = null

    // obf: f7285
    private var ipAddress: String? = null

    // obf: f7286 (C0875 case 4 = StartActivityForResult; result = C3733 default case -> C1531)
    private val pickImageLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == -1) {
                val data = result.data?.data
                if (data != null) {
                    // obf: C1531
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val isReadable = runCatching {
                            val inputStream = requireContext().contentResolver.openInputStream(data)
                            if (inputStream == null) {
                                false
                            } else {
                                inputStream.close()
                                true
                            }
                        }.getOrElse { false }
                        // obf: C0282
                        withContext(Dispatchers.Main) {
                            if (isReadable) {
                                imageUri = data
                                val imageView = binding.imageImagePlaceholder
                                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                                imageView.load(data)
                                setSendEnabled(true)
                            } else {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.file_access_error)
                                    .setMessage(R.string.cannot_access_selected_file_use)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        }
                    }
                }
            }
        }

    // obf: mo3020 (C2572)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogClipboardSetBinding
        get() = DialogClipboardSetBinding::inflate

    /** Colors the result text (v2) green/red. */
    // obf: m3896 (static, called from C3507 / C0393)
    private fun setResultColor(isSuccess: Boolean) {
        // RECON-UNSURE: inlined app Fragment extension (receiver check "<this>")
        TransitionManager.beginDelayedTransition(requireView().parent as ViewGroup)
        val color = if (isSuccess) R.color.success else R.color.error
        binding.textResult.setTextColor(ContextCompat.getColor(requireContext(), color))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ipAddress = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString("current_device_ip", null)
        val context = requireContext()
        // RECON-UNSURE: inlined app Context extension (receiver check "<this>") building the button spinner
        val spec = CircularProgressIndicatorSpec(context, null, 0, R.style.Widget_MaterialComponents_CircularProgressIndicator_ExtraSmall)
        progressDrawable = IndeterminateDrawable.createCircularDrawable(context, spec).apply {
            setVisible(true, true, true)
        }
        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                binding.textResult.visibility = View.INVISIBLE
                if (checkedId == R.id.btn_text) {
                    type = ClipboardType.TEXT
                    binding.inputLayoutText.visibility = View.VISIBLE
                    binding.cardSelectedImage.visibility = View.GONE
                    setSendEnabled(binding.inputEnterTextHere.text.toString().length > 0)
                    return@addOnButtonCheckedListener
                }
                if (checkedId == R.id.btn_image) {
                    type = ClipboardType.IMAGE
                    binding.inputLayoutText.visibility = View.GONE
                    binding.cardSelectedImage.visibility = View.VISIBLE
                    setSendEnabled(imageUri != null)
                }
            }
        }
        // obf: C3076 default case
        binding.inputEnterTextHere.doAfterTextChanged {
            setSendEnabled(it.toString().length > 0)
            binding.textResult.visibility = View.INVISIBLE
        }
        // lambda case 0
        binding.cardSelectedImage.setOnClickListener {
            val intent = Intent("android.intent.action.PICK")
            intent.type = "image/*"
            intent.putExtra("android.intent.extra.ALLOW_MULTIPLE", false)
            pickImageLauncher.launch(Intent.createChooser(intent, getString(R.string.select_image)))
        }
        // lambda case 1
        binding.btnSetClipboard.setOnClickListener {
            when (type) {
                ClipboardType.TEXT -> {
                    setLoading(true)
                    val text = binding.inputEnterTextHere.text.toString()
                    // obf: C1705
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val isSuccess = runCatching {
                            ApiClient.create(ipAddress).setClipboardText(text)
                            true
                        }.getOrElse { false }
                        // obf: C3507
                        withContext(Dispatchers.Main) {
                            setLoading(false)
                            val resultView = binding.textResult
                            resultView.visibility = View.VISIBLE
                            resultView.setText(if (isSuccess) R.string.set_text_clipboard_successfully else R.string.device_unreachable_try_again_reconnect)
                            setResultColor(isSuccess)
                        }
                    }
                }

                ClipboardType.IMAGE -> {
                    if (imageUri == null) {
                        return@setOnClickListener
                    }
                    setLoading(true)
                    // obf: C2405
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val (isSuccess, message) = runCatching {
                            val contentResolver = requireContext().contentResolver
                            val mimeType = contentResolver.getType(imageUri!!) ?: "image/jpeg"
                            val bytes = (contentResolver.openInputStream(imageUri!!) ?: throw IllegalArgumentException())
                                .use { it.readBytes() }
                            val body = bytes.toRequestBody(mimeType.toMediaType())
                            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
                            val part = MultipartBody.Part.createFormData("image", "image.$extension", body)
                            val response = ApiClient.create(ipAddress).setClipboardImage(part)
                            Pair(response.status, response.message)
                        }.getOrElse { e ->
                            val error = if (e is ConnectException || e is UnknownHostException) {
                                getString(R.string.device_unreachable_try_again_reconnect)
                            } else {
                                e.toString()
                            }
                            Pair(false, error)
                        }
                        // obf: C0393
                        withContext(Dispatchers.Main) {
                            setLoading(false)
                            val resultView = binding.textResult
                            resultView.visibility = View.VISIBLE
                            resultView.text = if (isSuccess) getString(R.string.set_image_clipboard_successfully) else message
                            setResultColor(isSuccess)
                        }
                    }
                }
            }
        }
    }

    // obf: m3897
    private fun setSendEnabled(isEnabled: Boolean) {
        binding.btnSetClipboard.isEnabled = isEnabled
    }

    // obf: m3898
    private fun setLoading(isLoading: Boolean) {
        setSendEnabled(!isLoading)
        val button = binding.btnSetClipboard
        if (isLoading) {
            button.icon = progressDrawable
        } else {
            button.setIconResource(R.drawable.ic_clipboard_send)
        }
    }
}

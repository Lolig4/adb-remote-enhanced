package com.jcapps.adbremote.dialogs.home

import android.app.Dialog
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import coil.EventListener
import coil.ImageLoader
import coil.request.Disposable
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.api.adb.ServerInstaller
import com.jcapps.adbremote.databinding.DialogScreenshotBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.Feature
import com.jcapps.adbremote.utils.FeatureUsage
import com.jcapps.adbremote.utils.Logger
import com.jcapps.adbremote.utils.beginDelayedTransition
import com.jcapps.adbremote.views.State
import com.jcapps.adbremote.views.StateView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Loads `http://<ip>:3125/screenshot` (Coil, header "token") into the zoomable image (k9) and
 * saves it as PNG via CreateDocument.
 */
class DialogScreenshot : BaseBottomSheet<DialogScreenshotBinding>() {
    // obf: C1955

    // obf: f7997
    private var job: Job? = null

    /** Called with the save result after saving (MainActivity C1433 case 5). RECON-UNSURE: name. */
    // obf: f7998
    var onScreenshotSaved: ((Boolean) -> Unit)? = null

    // obf: f7999
    private var isSaved: Boolean = false

    // obf: f8000 (lambda case 1)
    private val dataStore: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    /** Called from [onDestroy] when loaded but not saved (MainActivity C1425 case 27). RECON-UNSURE: name. */
    // obf: f8001
    var onDismiss: (() -> Unit)? = null

    /** Set by the Coil request listener on success. */
    // obf: f8002
    private var isLoaded: Boolean = false

    // obf: f8003 (lambda case 2)
    private val ipAddress: String? by lazy { dataStore.getString("current_device_ip", null) }

    // obf: f8004 (lambda default case)
    private val screenshotUrl: String by lazy {
        "http://" + ipAddress + ':' + ServerInstaller.SERVER_CONSTANT + "/screenshot"
    }

    // obf: f8005 (result = C3733 case 8 -> C1757 on IO)
    private val createDocumentLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
            // obf: C3733 case 8
            val drawable = binding.imageImagePlaceholder.drawable
            if (uri == null || drawable == null || drawable !is BitmapDrawable) {
                return@registerForActivityResult
            }
            binding.btnSave.isEnabled = false
            job = lifecycleScope.launch(Dispatchers.IO) {
                // obf: C1757
                val isSuccess = runCatching {
                    val bitmap = drawable.bitmap
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    true
                }.getOrElse { false }
                launch(Dispatchers.Main) {
                    // obf: C0736
                    Logger.log("Screenshot saved.")
                    binding.btnSave.isEnabled = true
                    onScreenshotSaved?.invoke(isSuccess)
                    isSaved = true
                    dismiss()
                }
            }
        }

    // obf: f8006 (lambda case 0)
    private val stateView: StateView by lazy {
        val params = StateView.defaultParams(requireContext())
        StateView(
            binding.includeViewState,
            params.copy(loading = params.loading.copy(anim = StateView.LOTTIE_LOADING_IMAGE)),
        )
    }

    // obf: f8007
    private var disposable: Disposable? = null

    // obf: mo1933 (C1000)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogScreenshotBinding
        get() = DialogScreenshotBinding::inflate

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // obf: m4416 mask 2 (first default, second explicit true)
        return createBottomSheetDialog(savedInstanceState, true, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isLoaded || isSaved) {
            return
        }
        onDismiss?.invoke()
    }

    override fun onDestroyView() {
        disposable?.dispose()
        job?.cancel()
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Logger.log("Taking screenshot ...")
        stateView.setState(State.LOADING)
        val imageLoader = ImageLoader.Builder(requireContext())
            .eventListener(
                // obf: C1696 (wrapped into an EventListener.Factory by C3733 case 11)
                object : EventListener {
                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        Logger.log("Taking screenshot failed. " + result.throwable.message)
                        stateView.setState(State.DISCONNECTED)
                    }
                },
            )
            .build()
        val token: String = NativeConfig.apiInterceptorToken
        val request = ImageRequest.Builder(requireContext())
            .data(screenshotUrl)
            .addHeader("token", token ?: "")
            .target(
                // obf: i5 (Target.onSuccess)
                onSuccess = { drawable ->
                    Logger.log("Screenshot taken.")
                    stateView.setState(State.SUCCESS)
                    binding.imageImagePlaceholder.setImageDrawable(drawable)
                    beginDelayedTransition()
                    binding.imageImagePlaceholder.visibility = View.VISIBLE
                    binding.btnSave.visibility = View.VISIBLE
                    isLoaded = true
                    FeatureUsage.trackFeatureUsage(dataStore, Feature.SCREENSHOT)
                },
            )
            .build()
        disposable = imageLoader.enqueue(request)
        // obf: ViewOnClickListenerC2219 case 7
        binding.btnSave.setOnClickListener {
            createDocumentLauncher.launch(
                "screenshot_" + SimpleDateFormat("MMM_d_yyyy HH_mm_ss", Locale.getDefault()).format(Date()) + ".png",
            )
        }
    }
}

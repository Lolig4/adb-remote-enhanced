package com.jcapps.adbremote.dialogs.home

import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.preference.PreferenceManager
import androidx.transition.TransitionManager
import com.google.android.material.slider.Slider
import com.jcapps.adbremote.R
import com.jcapps.adbremote.api.adb.ConnectionMethod
import com.jcapps.adbremote.databinding.DialogTrackpadBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.ui.MainActivity
import com.jcapps.adbremote.utils.getConnectionMethod
import kotlin.math.roundToInt

class DialogTrackpad : BaseBottomSheet<DialogTrackpadBinding>() {
    // obf: C0687

    // obf: f4553 (anonymous Function0 case 0)
    // obf: m2479 (getter)
    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    // obf: f4555 (anonymous Function0 case 1)
    private val connectionMethod: ConnectionMethod by lazy {
        prefs.getConnectionMethod()
    }

    // obf: f4554 (MainActivity C0808 case 10)
    var onDismiss: (() -> Unit)? = null

    // obf: f4556 — mouse/key events to MainViewModel.enqueue (MainActivity C1433 case 10)
    var onEvent: ((Any) -> Unit)? = null

    /** Rebuild addition: keyboard button in the header opens the keyboard sheet over the trackpad. */
    var onKeyboardClicked: (() -> Unit)? = null

    // obf: mo1933 (C1968)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogTrackpadBinding
        get() = DialogTrackpadBinding::inflate

    // Deviation (rebuild): no bottom inset padding -> touch area down to the screen edge
    override fun getTheme(): Int = R.style.TrackpadBottomSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Rebuild: swipe down to dismiss like the other sheets (NoSheetDragLayout blocks it below the title)
        return createFullHeightBottomSheetDialog(savedInstanceState, true, true)
    }

    override fun onDestroyView() {
        onDismiss?.invoke()
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.trackpadKeyboard.setOnClickListener { onKeyboardClicked?.invoke() }
        // Rebuild: no setToolbarBackground(true) any more - the full-height sheet covers the status bar
        // itself; the coloured activity background only showed up as a box while the sheet slid.
        val pointersEnabled = prefs.getBoolean("trackpad_pointers_enabled", true)
        var pointerSize = prefs.getInt("trackpad_pointer_size", 100)
        binding.switchEnablePointers.isChecked = pointersEnabled
        binding.switchEnablePointers.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("trackpad_pointers_enabled", isChecked).apply()
            binding.btnExpandPanel.isEnabled = isChecked
            if (isChecked) {
                binding.trackpadTrackpad.setPointerSize(pointerSize, false)
                return@setOnCheckedChangeListener
            }
            binding.trackpadTrackpad.disablePointers()
            if (binding.layoutPointerSettings.visibility == View.VISIBLE) {
                TransitionManager.beginDelayedTransition(requireView().parent as ViewGroup, null)
                binding.layoutPointerSettings.visibility = View.GONE
                binding.btnExpandPanel.setIconResource(R.drawable.ic_expand_panel)
            }
        }
        if (pointersEnabled) {
            binding.trackpadTrackpad.setPointerSize(pointerSize, false)
        }
        val slider = binding.sliderTrackpad
        slider.value = pointerSize.toFloat()
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                pointerSize = value.roundToInt()
                binding.trackpadTrackpad.setPointerSize(pointerSize, true)
            }
        }
        // obf: C1598 (bodies inlined into the Slider)
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                binding.trackpadTrackpad.touchPoints.clear()
                binding.trackpadTrackpad.invalidate()
            }

            override fun onStopTrackingTouch(slider: Slider) {
                binding.trackpadTrackpad.touchPoints.clear()
                binding.trackpadTrackpad.invalidate()
                prefs.edit().putInt("trackpad_pointer_size", pointerSize).apply()
            }
        })
        // obf: ViewOnClickListenerC2376 case 12 — reset size
        binding.btnReset.setOnClickListener {
            pointerSize = 100
            prefs.edit().putInt("trackpad_pointer_size", 100).apply()
            binding.sliderTrackpad.value = pointerSize.toFloat()
            binding.trackpadTrackpad.setPointerSize(pointerSize, false)
        }
        // obf: anonymous click listener case 1 — toggle pointer settings
        binding.btnExpandPanel.setOnClickListener {
            val isVisible = binding.layoutPointerSettings.visibility == View.VISIBLE
            TransitionManager.beginDelayedTransition(requireView().parent as ViewGroup, null)
            if (!isVisible) {
                binding.layoutPointerSettings.visibility = View.VISIBLE
                binding.btnExpandPanel.setIconResource(R.drawable.ic_arrow_up)
            } else {
                binding.layoutPointerSettings.visibility = View.GONE
                binding.btnExpandPanel.setIconResource(R.drawable.ic_expand_panel)
            }
        }
        if (connectionMethod != ConnectionMethod.SOCKET) {
            binding.imageTrackpad.alpha = 0.5f
            binding.textTrackpadOnlyAvailableAdbSocket.visibility = View.VISIBLE
        } else {
            // obf: ViewOnTouchListenerC3083 case 0
            binding.trackpadTrackpad.setOnTouchListener { _, event ->
                if (event != null) {
                    onEvent?.invoke(event)
                    binding.trackpadTrackpad.onTouch(event)
                }
                true
            }
        }
    }
}

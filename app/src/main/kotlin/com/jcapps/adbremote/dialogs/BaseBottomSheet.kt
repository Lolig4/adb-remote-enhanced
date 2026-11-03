package com.jcapps.adbremote.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewbinding.ViewBinding
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Base bottom-sheet dialog fragment that owns a generated [ViewBinding].
 *
 * Subclasses supply [bindingInflater] and read [binding]; the two `create..Dialog` helpers
 * build a [BottomSheetDialog] that expands on show (optionally full-height).
 */
// obf: AbstractC2199 (original name "BaseBottomSheet" from string evidence)
abstract class BaseBottomSheet<T : ViewBinding> : BottomSheetDialogFragment() {

    // obf: f8748
    private var _binding: T? = null

    /** Generated `Binding::inflate` reference: `(inflater, container, attachToParent) -> T`. */
    // obf: mo1933
    protected abstract val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> T

    // obf: m4417
    protected val binding: T
        get() = checkNotNull(_binding) {
            "null cannot be cast to non-null type T of com.jcapps.adbremote.dialogs.BaseBottomSheet"
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = bindingInflater(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** A bottom sheet that is expanded as soon as it is shown. */
    // obf: m4416 (+ synthetic $default; show-listener = DialogInterfaceOnShowListenerC1264 case 0)
    protected fun createBottomSheetDialog(
        savedInstanceState: Bundle?,
        draggable: Boolean = true,
        skipCollapsed: Boolean = false,
    ): BottomSheetDialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val sheet = (dialogInterface as BottomSheetDialog).findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet,
            ) as FrameLayout
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            // obf: f1008 / f1044
            behavior.isDraggable = draggable
            behavior.skipCollapsed = skipCollapsed
        }
        return dialog
    }

    /** A bottom sheet forced to (window height - 100px) and expanded on show. */
    // obf: m4418 (show-listener = DialogInterfaceOnShowListenerC1264 case 1)
    protected fun createFullHeightBottomSheetDialog(
        savedInstanceState: Bundle?,
        draggable: Boolean = true,
        skipCollapsed: Boolean = false,
    ): BottomSheetDialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val sheet = (dialogInterface as BottomSheetDialog).findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet,
            ) as FrameLayout
            val behavior = BottomSheetBehavior.from(sheet)
            val layoutParams = sheet.layoutParams
            val windowMetrics = WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(requireActivity())
            layoutParams?.height = windowMetrics.bounds.height() - 100
            sheet.layoutParams = layoutParams
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = draggable
            behavior.skipCollapsed = skipCollapsed
        }
        return dialog
    }
}

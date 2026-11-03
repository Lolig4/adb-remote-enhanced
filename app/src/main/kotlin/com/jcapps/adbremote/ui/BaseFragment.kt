package com.jcapps.adbremote.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * Base fragment that owns a generated [ViewBinding] for the lifetime of the view.
 *
 * Subclasses supply [bindingInflater] (the generated `XxxBinding::inflate` reference) and read
 * the inflated binding through [binding]; it is cleared in [onDestroyView].
 */
// obf: AbstractC1494 (original name "BaseFragment" from string evidence)
abstract class BaseFragment<T : ViewBinding> : Fragment() {

    // obf: f6711
    private var _binding: T? = null

    /** Generated `Binding::inflate` reference: `(inflater, container, attachToParent) -> T`. */
    // obf: mo3020
    protected abstract val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> T

    // obf: m3434
    protected val binding: T
        get() = checkNotNull(_binding) {
            "null cannot be cast to non-null type T of com.jcapps.adbremote.ui.BaseFragment"
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
}

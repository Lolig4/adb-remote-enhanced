package com.jcapps.adbremote.ui.install

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jcapps.adbremote.databinding.FragmentInstallListBinding
import com.jcapps.adbremote.ui.BaseFragment

/** Rebuild addition: common base of the two sideload tabs (list + loading + optional bottom button). */
abstract class InstallListFragment : BaseFragment<FragmentInstallListBinding>() {

    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> FragmentInstallListBinding
        get() = FragmentInstallListBinding::inflate

    protected val host get() = requireActivity() as InstallActivity
    protected lateinit var adapter: InstallAdapter

    /** Extra bottom padding of the list (room for the bottom button). */
    protected open val extraBottomPadding = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = InstallAdapter(host.imageLoader) { host.sideloader.start(it) }
        binding.installList.adapter = adapter
        val listBottom = binding.installList.paddingBottom
        val pickMargin = (binding.installPick.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            binding.installList.setPadding(0, binding.installList.paddingTop, 0, listBottom + bottom + extraBottomPadding)
            (binding.installPick.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = pickMargin + bottom
            binding.installPick.requestLayout()
            insets
        }
    }

    protected fun showLoading(loading: Boolean) {
        binding.installLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }
}

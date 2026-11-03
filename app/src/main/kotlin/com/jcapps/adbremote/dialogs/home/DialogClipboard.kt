package com.jcapps.adbremote.dialogs.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.DialogClipboardBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.ui.BaseFragment
import com.jcapps.adbremote.utils.openGuide

/** Full-height sheet with two tabs: get / set the connected device's clipboard. */
class DialogClipboard : BaseBottomSheet<DialogClipboardBinding>() {
    // obf: C2825

    // obf: f10370
    private val fragments: List<Pair<BaseFragment<*>, Int>> = listOf(
        Pair(ClipboardGetFragment(), R.string.get_clipboard),
        Pair(ClipboardSetFragment(), R.string.set_clipboard),
    )

    // obf: mo1933 (C1467)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogClipboardBinding
        get() = DialogClipboardBinding::inflate

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return createFullHeightBottomSheetDialog(savedInstanceState, true, true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // obf: ViewOnClickListenerC2219 case 1
        binding.btnHelp.setOnClickListener {
            openGuide(requireContext(), "guide/how-to-use-clipboard")
        }
        binding.pager.adapter = PagerAdapter(this)
        binding.pager.offscreenPageLimit = 2
        // obf: C3733 case 5 — RECON-UNSURE: strategy body lost by jadx (R8-merged lambda class);
        //  reconstructed as the tab title from [fragments].
        TabLayoutMediator(binding.tabsClipboard, binding.pager) { tab, position ->
            tab.setText(fragments[position].second)
        }.attach()
    }

    /** Pages = [fragments]. RECON-UNSURE: class name. */
    inner class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        // obf: C1073

        override fun getItemCount(): Int = fragments.size

        override fun createFragment(position: Int): Fragment = fragments[position].first
    }
}

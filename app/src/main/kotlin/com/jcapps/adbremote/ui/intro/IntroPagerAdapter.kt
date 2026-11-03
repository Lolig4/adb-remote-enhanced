package com.jcapps.adbremote.ui.intro

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class IntroPagerAdapter(
    fragmentActivity: FragmentActivity,
    // obf: f13806
    private val fragments: ArrayList<Fragment>,
) : FragmentStateAdapter(fragmentActivity) {
    // obf: C3942

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]
}

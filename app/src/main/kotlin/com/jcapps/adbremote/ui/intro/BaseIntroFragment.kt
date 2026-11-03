package com.jcapps.adbremote.ui.intro

import androidx.viewbinding.ViewBinding
import com.jcapps.adbremote.ui.BaseFragment

/** Intro page; [canMoveNext] gates paging, [onNextBlocked] runs when the user tries to page while blocked. */
abstract class BaseIntroFragment<T : ViewBinding> : BaseFragment<T>() {
    // obf: AbstractC2972

    // obf: m5360
    val pagerController: PagerController
        get() = (requireActivity() as IntroActivity).pagerController

    // obf: mo3145
    open fun canMoveNext(fromSwipe: Boolean): Boolean = true

    // obf: mo3144
    open fun onNextBlocked() {
    }
}

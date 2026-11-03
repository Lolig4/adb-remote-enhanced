package com.jcapps.adbremote.ui.intro

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.jcapps.adbremote.databinding.IntroNotificationPermissionBinding

class IntroNotificationPermissionFragment : BaseIntroFragment<IntroNotificationPermissionBinding>() {
    // obf: C1148

    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> IntroNotificationPermissionBinding =
        IntroNotificationPermissionBinding::inflate

    // obf: f5806
    private var permissionRequested: Boolean = false

    // obf: f5805 (C0875(3) = RequestPermission, result = C3733 case 13)
    private val permissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionRequested = granted
            pagerController.next()
        }

    override fun onNextBlocked() {
        pagerController.isSwipeEnabled = false
        permissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
    }

    override fun canMoveNext(fromSwipe: Boolean): Boolean = permissionRequested
}

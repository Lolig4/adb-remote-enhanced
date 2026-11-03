package com.jcapps.adbremote.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.databinding.DialogRateAppBinding
import com.jcapps.adbremote.prefs.AppPrefs
import com.jcapps.adbremote.utils.PlayStore

/** Rate prompt: "ed" opens the Telegram channel link from the remote config, "dz" the Play Store page. */
class DialogRateApp : BaseBottomSheet<DialogRateAppBinding>() {
    // obf: C2687

    // obf: mo1933 (C0974)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogRateAppBinding
        get() = DialogRateAppBinding::inflate

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // lambda case 0
        binding.btnSendFeedback.setOnClickListener {
            val link = AppPrefs(PreferenceManager.getDefaultSharedPreferences(requireContext())).config.telegramChannel
            with(PlayStore) {
                requireContext().launchLink(link)
            }
            dismiss()
        }
        // lambda case 1
        binding.btnRateApp.setOnClickListener {
            with(PlayStore) {
                requireContext().launchOnPlayStore(requireContext().packageName)
            }
            dismiss()
        }
    }
}

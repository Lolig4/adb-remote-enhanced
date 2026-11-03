package com.jcapps.adbremote.dialogs.icons

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import coil.load
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.DialogIconPackRequestBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.IconPackInfo
import com.jcapps.adbremote.utils.currentIconPack
import com.jcapps.adbremote.utils.loadIconPack
import kotlinx.coroutines.Job

/** "An app is requesting to apply the %s icon pack" sheet (layout dialog_icon_pack_request). */
class DialogIconPackApply(
    // obf: f6852
    private val iconPackInfo: IconPackInfo,
    // obf: f6850 (C1425 lambda of the caller)
    private val onApplied: () -> Unit,
) : BaseBottomSheet<DialogIconPackRequestBinding>() {
    // obf: C1543

    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogIconPackRequestBinding =
        DialogIconPackRequestBinding::inflate

    // obf: f6849 (C2247 case 11)
    private val dataStore: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    // obf: f6851
    private var loadJob: Job? = null

    override fun onDestroyView() {
        loadJob?.cancel(null)
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.title.text = iconPackInfo.name
        binding.icon.load(iconPackInfo.icon)
        binding.textAppRequestingApplyIconPack.text = getString(R.string.app_requesting_apply_icon_pack, iconPackInfo.name)
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnApply.setOnClickListener {
            binding.btnApply.isEnabled = false
            currentIconPack = null
            dataStore.edit().putString("icon_pack", iconPackInfo.packageName).apply()
            loadJob = loadIconPack(requireContext(), iconPackInfo.packageName) {
                // obf: C2192 case 11
                onApplied()
                dismiss()
            }
        }
    }
}

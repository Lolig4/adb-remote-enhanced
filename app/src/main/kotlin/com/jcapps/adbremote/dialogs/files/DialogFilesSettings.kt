package com.jcapps.adbremote.dialogs.files

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.DialogFilesSettingsBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.FilesSettings
import com.jcapps.adbremote.model.IntentParams
import com.jcapps.adbremote.prefs.JsonPrefs
import com.jcapps.adbremote.ui.files.loadFilesSettings

/**
 * Bottom sheet to choose how files open remotely ("open remotely using" run-type) and the MX Player
 * decode mode. Each choice is persisted immediately ("files_settings_open"); the settings are
 * handed back on dismiss. Binding: DialogFilesSettingsBinding.
 */
class DialogFilesSettings : BaseBottomSheet<DialogFilesSettingsBinding>() {
    // obf: C1389

    // obf: f6468
    var filesSettings: FilesSettings? = null

    // obf: f6469 (C2247 case 10)
    private val basePrefs: JsonPrefs by lazy {
        JsonPrefs(PreferenceManager.getDefaultSharedPreferences(requireContext()))
    }

    /** Invoked on dismiss with the (possibly changed) settings. */
    // obf: f6470
    var onSettingsChanged: ((FilesSettings) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        filesSettings = loadFilesSettings(requireContext())

        val runTypes = resources.getStringArray(R.array.shell_normal_intent_entries)
        binding.dropdownOpenRunType.apply {
            setSimpleItems(runTypes)
            val index = IntentParams.RunType.entries.indexOf(filesSettings!!.openRemotelyUsing)
                .takeIf { it >= 0 }
            setText(runTypes[index ?: 0], false)
            // obf: C1923 case 1
            setOnItemClickListener { _, _, position, _ ->
                filesSettings?.openRemotelyUsing = IntentParams.RunType.entries[position]
                if (filesSettings != null) {
                    basePrefs.put(filesSettings, "files_settings_open")
                }
            }
        }

        val decodeModes = resources.getStringArray(R.array.app_default_hw_decoder_entries)
        val mxPlayerDecodeModeMap = mapOf(
            0 to decodeModes[0],
            1 to decodeModes[1],
            2 to decodeModes[2],
            4 to decodeModes[3],
        )
        binding.dropdownImageDecodeMode.apply {
            setSimpleItems(decodeModes)
            setText(mxPlayerDecodeModeMap.getOrDefault(filesSettings!!.mxPlayerDecodeMode, decodeModes[1]), false)
            // obf: C3843 case 0
            setOnItemClickListener { _, _, position, _ ->
                filesSettings?.mxPlayerDecodeMode = mxPlayerDecodeModeMap.keys.elementAt(position)
                if (filesSettings != null) {
                    basePrefs.put(filesSettings, "files_settings_open")
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        val settings = filesSettings
        if (settings != null) {
            onSettingsChanged?.invoke(settings)
        }
        super.onDismiss(dialog)
    }

    // obf: mo1933 — binding inflater
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogFilesSettingsBinding = DialogFilesSettingsBinding::inflate
}

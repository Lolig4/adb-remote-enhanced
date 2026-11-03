package com.jcapps.adbremote.dialogs.icons

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceManager
import androidx.transition.TransitionManager
import com.jcapps.adbremote.Constants
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.icons.IconPackAdapter
import com.jcapps.adbremote.databinding.DialogIconPackChooserBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.IconPack
import com.jcapps.adbremote.model.IconPackInfo
import com.jcapps.adbremote.model.Link
import com.jcapps.adbremote.utils.PlayStore
import com.jcapps.adbremote.utils.currentIconPack
import com.jcapps.adbremote.utils.loadIconPack
import com.jcapps.adbremote.utils.SpacingItemDecoration
import com.jcapps.adbremote.utils.getIconPacks
import com.jcapps.adbremote.utils.getSelectedIconPack
import kotlinx.coroutines.Job
import kotlin.math.roundToInt

/** Bottom sheet listing installed icon packs (+ "add" item opening the Play Store search). */
class DialogIconPackChooser : BaseBottomSheet<DialogIconPackChooserBinding>() {
    // obf: C1156

    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogIconPackChooserBinding =
        DialogIconPackChooserBinding::inflate

    // obf: f5828
    private lateinit var iconPackAdapter: IconPackAdapter

    // obf: f5829 (C3579 case 1)
    private val dataStore: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    // obf: f5830
    private var loadJob: Job? = null

    // obf: f5831 (set by SettingsFragment: C2192 case 7)
    var onSelected: ((IconPackInfo) -> Unit)? = null

    // obf: f5832 (C0875(4) = StartActivityForResult, result = C3733 case 7)
    private val addLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            loadIconPacks()
        }

    override fun onDestroyView() {
        loadJob?.cancel(null)
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // obf: ViewOnClickListenerC2219 case 5 (opens Constants.applyIconPackUrl via PlayStore.launchLink)
        binding.btnHelp.setOnClickListener {
            with(PlayStore) { requireContext().launchLink(Link(Constants.applyIconPackUrl, null)) }
        }
        val selectedIconPack = getSelectedIconPack(requireContext())
        iconPackAdapter = IconPackAdapter(
            requireContext(),
            selectedIconPack,
            // obf: C2192 case 13
            { iconPack ->
                val iconPackInfo = iconPack.info!!
                currentIconPack = null
                dataStore.edit().putString("icon_pack", iconPackInfo.packageName).apply()
                TransitionManager.beginDelayedTransition(requireView().parent as ViewGroup)
                binding.progress.visibility = View.VISIBLE
                loadJob = loadIconPack(requireContext(), iconPackInfo.packageName) {
                    // obf: C3629 case 5
                    onSelected?.invoke(iconPackInfo)
                    dismiss()
                }
            },
            // obf: C3579 case 0
            {
                val uri = Uri.parse("https://play.google.com/store/search").buildUpon()
                    .appendQueryParameter("q", "icon pack free")
                    .appendQueryParameter("c", "apps")
                    .build()
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = uri
                    intent.setPackage("com.android.vending")
                    addLauncher.launch(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = uri
                        addLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), R.string.no_app_found_open_link, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
        binding.list.setHasFixedSize(true)
        binding.list.adapter = iconPackAdapter
        binding.list.addItemDecoration(SpacingItemDecoration(binding.list.resources.getDimension(R.dimen.list_spacing).roundToInt()))
        loadIconPacks()
    }

    // obf: m3153
    private fun loadIconPacks() {
        val iconPacks = getIconPacks(requireContext()).map { IconPack(it) }.toMutableList()
        iconPacks.add(IconPack(null))
        iconPackAdapter.submitList(iconPacks)
    }
}

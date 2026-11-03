package com.jcapps.adbremote.dialogs.icons

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.R
import com.jcapps.adbremote.adapters.icons.IconShapeAdapter
import com.jcapps.adbremote.databinding.DialogIconShapeChooserBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.IconShapeData
import com.jcapps.adbremote.ui.icons.IconShapeActivity
import com.jcapps.adbremote.utils.getCustomIconShape
import com.jcapps.adbremote.utils.iconShapes

/** Bottom sheet listing the icon shapes (custom first); "customize" opens IconShapeActivity. */
class DialogIconShapeChooser : BaseBottomSheet<DialogIconShapeChooserBinding>() {
    // obf: C0950

    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogIconShapeChooserBinding =
        DialogIconShapeChooserBinding::inflate

    // obf: f5238 (C3275 case 0)
    private val dataStore: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    // obf: f5237 (C3275 case 1)
    private val cornerSize: Float by lazy { requireContext().resources.getDimension(R.dimen.app_icon_height) }

    // obf: f5239
    private var selectedIconShape: Int = 0

    // obf: f5241 (set by SettingsFragment: C2192 case 6)
    var onSelected: ((IconShapeData) -> Unit)? = null

    // obf: f5243
    private lateinit var iconShapeAdapter: IconShapeAdapter

    // obf: f5240
    private val icons = ArrayList<IconShapeData>()

    // obf: f5242 (C0875(4) = StartActivityForResult, result = C3733 case 6)
    private val customizeLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                dataStore.edit().putInt("icon_shape", -1).apply()
                icons[0] = getCustomIconShape(dataStore).copy()
                iconShapeAdapter.isCustomShapeAvailable = true
                iconShapeAdapter.selectedId = -1
                iconShapeAdapter.notifyItemRangeChanged(0, icons.size)
                onSelected?.invoke(icons[0])
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isCustomShapeAvailable = dataStore.contains("custom_icon_shape")
        selectedIconShape = dataStore.getInt("icon_shape", 0)
        icons.add(getCustomIconShape(dataStore))
        icons.addAll(iconShapes)
        iconShapeAdapter = IconShapeAdapter(
            isCustomShapeAvailable,
            cornerSize,
            selectedIconShape,
            icons,
            // obf: C2192 case 12
            { iconShape ->
                dataStore.edit().putInt("icon_shape", iconShape.id).apply()
                onSelected?.invoke(iconShape)
                dismiss()
            },
            // obf: C3275 case 2
            { customizeLauncher.launch(Intent(requireContext(), IconShapeActivity::class.java)) },
        )
        binding.list.setHasFixedSize(true)
        binding.list.adapter = iconShapeAdapter
    }
}

package com.jcapps.adbremote.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.PreferenceIconBinding
import com.jcapps.adbremote.model.IconShapeData
import com.jcapps.adbremote.utils.createShapeAppearanceModel
import com.jcapps.adbremote.utils.getIconShape

/** "Icon shape" preference (key `icon_shape`); click handled by SettingsFragment. */
class IconShapePreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    // obf: C1286

    // obf: f1573
    private var cornerSize: Float = 1f

    // obf: f1575
    private val strokeWidth: Float = context.resources.getDimension(R.dimen.icon_stroke_width)

    // obf: f1576
    private val iconRes: Int = R.drawable.ic_icon_shape

    // obf: f1577
    private val titleRes: Int = R.string.apps_icon_shape

    // obf: f1574
    lateinit var iconData: IconShapeData

    // RECON: R8 inlined this public wrapper at its only call site (SettingsActivity)
    fun notifyIconChanged() = notifyChanged()

    init {
        layoutResource = R.layout.preference_icon
        iconData = getIconShape(getContext())
        cornerSize = getContext().resources.getDimension(R.dimen.app_icon_height)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val binding = PreferenceIconBinding.bind(holder.itemView)
        binding.icon.setImageResource(iconRes)
        binding.textPreferenceTitle.setText(titleRes)
        binding.imagePreview.setImageResource(R.drawable.ic_icon_preview)
        binding.imagePreview.strokeWidth = strokeWidth
        binding.imagePreview.shapeAppearanceModel = createShapeAppearanceModel(iconData.shape, cornerSize)
        binding.textPreferenceSummary.setText(iconData.title)
        binding.imagePreview.imageTintList = null
    }
}

package com.jcapps.adbremote.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.PreferenceIconBinding
import com.jcapps.adbremote.utils.IconPackResources
import com.jcapps.adbremote.utils.getSelectedIconPack

/** "Icon pack" preference (key `icon_style`); click handled by SettingsFragment. */
class IconPackPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    // obf: C1421

    // obf: f1597
    private val iconRes: Int = R.drawable.ic_extension

    // obf: f1596
    private val titleRes: Int = R.string.apps_icon_style

    // obf: f1598
    private var iconPackIcon: Drawable? = null

    // obf: f1599
    private lateinit var iconPackName: String

    init {
        layoutResource = R.layout.preference_icon
        setIconPack(getSelectedIconPack(getContext()))
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val binding = PreferenceIconBinding.bind(holder.itemView)
        binding.icon.setImageResource(iconRes)
        binding.textPreferenceTitle.setText(titleRes)
        binding.imagePreview.setImageDrawable(iconPackIcon)
        binding.imagePreview.imageTintList = null
        binding.textPreferenceSummary.text = iconPackName
    }

    // obf: m839
    fun setIconPack(iconPackPackageName: String) {
        val context = context
        val packageName = context.packageName
        val iconPack = IconPackResources.create(context, iconPackPackageName)
        if (iconPack != null) {
            if (iconPack.packageName == packageName) {
                iconPackName = context.getString(R.string.system_icons)
                iconPackIcon = ContextCompat.getDrawable(context, R.drawable.ic_icon_preview)
            } else {
                iconPackName = iconPack.label
                iconPackIcon = iconPack.loadIcon(context)
            }
        } else {
            iconPackName = context.getString(R.string.system_icons)
            iconPackIcon = ContextCompat.getDrawable(context, R.drawable.ic_icon_preview)
        }
        notifyChanged()
    }
}

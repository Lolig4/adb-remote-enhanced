package com.jcapps.adbremote.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import com.google.android.material.materialswitch.MaterialSwitch
import com.jcapps.adbremote.R

/** MaterialSwitch that shows a check thumb icon while checked (referenced from many layouts). */
class MaterialSwitchView(context: Context, attrs: AttributeSet) : MaterialSwitch(context, attrs) {
    // obf: C1422

    // obf: f1601
    private val defaultThumbIcon: Drawable? = thumbIconDrawable

    // obf: f1600 — true once construction finished (setChecked is called from the super constructor)
    private val initialized: Boolean = true

    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        if (initialized) {
            if (checked) {
                setThumbIconResource(R.drawable.ic_check)
            } else {
                thumbIconDrawable = defaultThumbIcon
            }
        }
    }
}

package com.jcapps.adbremote.dialogs.apps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.jcapps.adbremote.R
import com.jcapps.adbremote.databinding.DialogAppFilterBinding
import com.jcapps.adbremote.dialogs.BaseBottomSheet
import com.jcapps.adbremote.model.AppOpenable
import com.jcapps.adbremote.model.AppState
import com.jcapps.adbremote.model.AppType
import com.jcapps.adbremote.model.TvSupport
import com.jcapps.adbremote.utils.reversed

/** Bottom sheet with the four app-list filters of [com.jcapps.adbremote.ui.apps.AppsActivity]. */
class DialogAppFilter(
    // obf: f11207
    private val type: AppType,
    // obf: f11202
    private val state: AppState,
    // obf: f11201
    private val openable: AppOpenable,
    // obf: f11206
    private val tvSupport: TvSupport,
    // obf: f11211 (C3704 case 1)
    private val onApply: (AppType, AppState, AppOpenable, TvSupport) -> Unit,
) : BaseBottomSheet<DialogAppFilterBinding>() {
    // obf: C3120

    // obf: f11204
    private val typeIds = mapOf(
        AppType.ALL to R.id.btn_type_all,
        AppType.USER to R.id.btn_type_user,
        AppType.SYSTEM to R.id.btn_type_system,
    )

    // obf: f11208
    private val idTypes = typeIds.reversed()

    // obf: f11209
    private val stateIds = mapOf(
        AppState.ALL to R.id.btn_state_all,
        AppState.ENABLED to R.id.btn_state_enabled,
        AppState.DISABLED to R.id.btn_state_disabled,
    )

    // obf: f11212
    private val idStates = stateIds.reversed()

    // obf: f11200
    private val openableIds = mapOf(
        AppOpenable.ALL to R.id.btn_openable_all,
        AppOpenable.YES to R.id.btn_openable_yes,
        AppOpenable.NO to R.id.btn_openable_no,
    )

    // obf: f11210
    private val idOpenables = openableIds.reversed()

    // obf: f11205
    private val tvSupportIds = mapOf(
        TvSupport.ALL to R.id.btn_tv_support_all,
        TvSupport.SUPPORTED to R.id.btn_tv_support_yes,
        TvSupport.NA to R.id.btn_tv_support_no,
    )

    // obf: f11203
    private val idTvSupports = tvSupportIds.reversed()

    // obf: mo1933 (C1134)
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> DialogAppFilterBinding
        get() = DialogAppFilterBinding::inflate

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toggleType.check(typeIds[type]!!)
        binding.toggleState.check(stateIds[state]!!)
        binding.toggleOpenable.check(openableIds[openable]!!)
        binding.toggleTvSupport.check(tvSupportIds[tvSupport]!!)
        // cancel
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        // apply
        binding.btnApply.setOnClickListener {
            onApply(
                idTypes[binding.toggleType.checkedButtonId]!!,
                idStates[binding.toggleState.checkedButtonId]!!,
                idOpenables[binding.toggleOpenable.checkedButtonId]!!,
                idTvSupports[binding.toggleTvSupport.checkedButtonId]!!,
            )
            dismiss()
        }
    }
}

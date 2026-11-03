package com.jcapps.adbremote.utils

import androidx.appcompat.app.AppCompatActivity
import com.jcapps.adbremote.R
import com.jcapps.adbremote.dialogs.DialogOption
import com.jcapps.adbremote.model.App
import com.jcapps.adbremote.model.Option

/**
 * Per-app option sheet (the [com.jcapps.adbremote.model.AppAction] keys) of the apps screen.
 *
 * R8 inlined [show] into its only caller (AppsActivity, lambda C3704 case 0; leftover parameter
 * checks "activity", "restrictedSystemApps", "app", "onOptionSelected") and merged this class into
 * the helper class `AbstractC1557`; the fragment tag is `<this class>::class.simpleName`.
 * RECON-UNSURE: object and function names (the tag text depends on the object name).
 */
object AppOptions {
    // obf: AbstractC1557 (merged)

    fun show(
        activity: AppCompatActivity,
        restrictedSystemApps: List<String>,
        app: App,
        isFav: Boolean,
        onOptionSelected: (Option) -> Unit,
    ) {
        val options = ArrayList<Option>()
        if (app.isLauncherAvailable) {
            options.add(Option("LAUNCH", R.drawable.ic_open_in_new, activity.getString(R.string.open_app)))
        }
        options.add(
            if (isFav) {
                Option("REMOVE_FROM_FAV_APPS", R.drawable.ic_favorite_remove, activity.getString(R.string.remove_favorite_apps))
            } else {
                Option("ADD_TO_FAV_APPS", R.drawable.ic_favorite_add, activity.getString(R.string.add_favorite_apps))
            },
        )
        options.add(Option("INFO", R.drawable.ic_info, activity.getString(R.string.app_info)))
        if (!restrictedSystemApps.contains(app.packageName)) {
            options.add(Option("FORCE_STOP", R.drawable.ic_stop, activity.getString(R.string.force_stop)))
        }
        options.add(Option("CLEAR_DATA", R.drawable.ic_clear_data, activity.getString(R.string.clear_data)))
        options.add(
            if (app.isEnabled) {
                Option("DISABLE", R.drawable.ic_disabled, activity.getString(R.string.disable_app))
            } else {
                Option("ENABLE", R.drawable.ic_check_circle, activity.getString(R.string.enable_app))
            },
        )
        options.add(Option("UNINSTALL", R.drawable.ic_delete, activity.getString(R.string.uninstall_app)))
        options.add(Option("OPEN_APP_INFO_SETTINGS", R.drawable.ic_app_settings, activity.getString(R.string.open_app_info_settings)))
        options.add(Option("OPEN_IN_PLAY_STORE", R.drawable.ic_play_store, activity.getString(R.string.open_play_store)))
        options.add(Option("OPEN_IN_LOCAL_PLAY_STORE", R.drawable.ic_play_store, activity.getString(R.string.open_local_play_store)))
        val dialog = DialogOption()
        dialog.title = app.name
        dialog.options = options
        dialog.onOptionSelected = onOptionSelected
        dialog.show(activity.supportFragmentManager, AppOptions::class.simpleName)
    }
}

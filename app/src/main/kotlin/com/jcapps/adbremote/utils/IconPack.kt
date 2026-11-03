package com.jcapps.adbremote.utils

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import coil.load
import com.google.android.material.imageview.ShapeableImageView
import com.jcapps.adbremote.R
import com.jcapps.adbremote.model.IconPackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// obf: AbstractC3690 (file facade)

// obf: f13073
val iconPackIntents: List<Intent> = listOf(
    Intent("app.lawnchair.icons.THEMED_ICON"),
    Intent("com.novalauncher.THEME"),
    Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
    Intent("com.dlto.atom.launcher.THEME"),
    Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME"),
)

// obf: f13074
var currentIconPack: IconPackResources? = null

/**
 * Shows the icon of [packageName] from the current icon pack, falling back to the remote app
 * icon (via [appIconLoader]) with stroke/padding/background.
 *
 * RECON-UNSURE: AppIconLoader (C1872) is unregistered (apps group); its `m4051` is assumed to be
 * `load(imageView, packageName)`.
 */
// obf: m6257
fun ShapeableImageView.loadAppIcon(
    packageName: String,
    appIconLoader: AppIconLoader,
    density: Int,
    strokeWidth: Float,
    backgroundColor: Int,
) {
    val iconPack = currentIconPack
    if (iconPack == null) {
        appIconLoader.load(this, packageName)
        setStrokeWidth(strokeWidth)
        val padding = strokeWidth.toInt()
        setPadding(padding, padding, padding, padding)
        setBackgroundColor(backgroundColor)
        return
    }
    val drawableName = iconPack.iconMap[packageName]
    val drawable: Drawable? = if (drawableName != null) {
        val id = iconPack.drawableIds.getOrPut(drawableName) {
            iconPack.packResources.getIdentifier(drawableName, "drawable", iconPack.packageName)
        }
        if (id == 0) {
            null
        } else {
            try {
                iconPack.packResources.getDrawableForDensity(id, density, null)
            } catch (_: Resources.NotFoundException) {
                null
            }
        }
    } else {
        null
    }
    if (drawable == null) {
        appIconLoader.load(this, packageName)
        setStrokeWidth(strokeWidth)
        val padding = strokeWidth.toInt()
        setPadding(padding, padding, padding, padding)
        setBackgroundColor(backgroundColor)
        return
    }
    load(drawable)
    setStrokeWidth(0f)
    background = null
    setPadding(0, 0, 0, 0)
}

// obf: m6258
fun getSelectedIconPack(context: Context): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val packageName = context.packageName
    return prefs.getString("icon_pack", context.packageName) ?: packageName
}

// obf: m6259
fun getIconPacks(context: Context): List<IconPackInfo> {
    val packageManager = context.packageManager
    val resolveInfos = buildList {
        for (intent in iconPackIntents) {
            addAll(packageManager.queryIntentActivities(intent, 0))
        }
    }
    val iconPacks = resolveInfos
        .associateBy { it.activityInfo.packageName }
        .map { (_, resolveInfo) ->
            IconPackInfo(
                resolveInfo.loadLabel(packageManager).toString(),
                resolveInfo.activityInfo.packageName,
                resolveInfo.loadIcon(packageManager),
            )
        }
        .toSet()
    return listOf(
        IconPackInfo(
            context.getString(R.string.system_icons),
            context.packageName,
            ContextCompat.getDrawable(context, R.drawable.ic_icon_preview)!!,
        ),
    ) + iconPacks.sortedWith { a, b -> compareValues(a.name, b.name) }
}

/**
 * Loads the icon pack [packageName] (or the selected one) on IO and calls [callback] on Main.
 * Returns null (and calls back synchronously) when a pack is already loaded or the system icons
 * are requested.
 */
// obf: m6260 (body = suspend lambda C3671, callback = C2355)
fun loadIconPack(context: Context, packageName: String?, callback: (IconPackResources?) -> Unit): Job? {
    val iconPackName = packageName ?: getSelectedIconPack(context)
    if (currentIconPack != null || packageName == context.packageName) {
        callback(currentIconPack)
        return null
    }
    return CoroutineScope(Dispatchers.IO).launch {
        currentIconPack = IconPackResources.create(context, iconPackName)?.also { it.load(context) }
        launch(Dispatchers.Main) {
            callback(currentIconPack)
        }
    }
}

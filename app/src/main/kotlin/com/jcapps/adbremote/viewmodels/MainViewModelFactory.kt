package com.jcapps.adbremote.viewmodels

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.jcapps.adbremote.api.adb.AdbBinary
import com.jcapps.adbremote.prefs.DevicesStore

/**
 * Creates [MainViewModel] with its constructor dependencies.
 * RECON-UNSURE: name — R8 merged this app factory into androidx `SavedStateViewModelFactory`
 * (extra ctor `(C3193, C1963, owner, defaultArgs)` + create body m4683).
 */
class MainViewModelFactory(
    private val adb: AdbBinary,
    private val adbPrefs: DevicesStore,
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle?,
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(adb, adbPrefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ".plus(modelClass.name))
    }
}

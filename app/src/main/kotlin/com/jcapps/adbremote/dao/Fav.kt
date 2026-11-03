package com.jcapps.adbremote.dao

import com.jcapps.adbremote.model.App

/** Item of the favourite-apps grid on the main screen. */
sealed class Fav {
    // obf: AbstractC0889

    data class Data(
        // obf: f9962
        val app: App,
    ) : Fav() {
        // obf: C2661
    }

    data object Add : Fav() {
        // obf: C2122
    }
}

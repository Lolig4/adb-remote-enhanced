package com.jcapps.adbremote.model

// App-list filter: enabled / disabled apps (App.isEnabled).
// RECON-UNSURE: class name (R8-renamed; only the DialogAppFilter parameter name "state" survived).
enum class AppState {
    // obf: EnumC1837
    ALL,
    ENABLED,
    DISABLED,
}

package com.jcapps.adbremote.model

// App-list filter: user / system apps (App.isSystemApp).
// RECON-UNSURE: class name (R8-renamed; only the DialogAppFilter parameter name "type" survived).
enum class AppType {
    // obf: EnumC0595
    ALL,
    USER,
    SYSTEM,
}

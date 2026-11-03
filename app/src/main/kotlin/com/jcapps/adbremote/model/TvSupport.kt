package com.jcapps.adbremote.model

// App-list filter: TV-supported apps (App.isTvSupported).
// RECON-UNSURE: class name (R8-renamed; only the DialogAppFilter parameter name "tvSupport" survived).
enum class TvSupport {
    // obf: EnumC2870
    ALL,
    SUPPORTED,
    NA,
}

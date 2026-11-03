package com.jcapps.adbremote.model

// App-list filter: apps with / without launcher activity (App.isLauncherAvailable).
// RECON-UNSURE: class name (R8-renamed; only the DialogAppFilter parameter name "openable" survived).
enum class AppOpenable {
    // obf: EnumC2664
    ALL,
    YES,
    NO,
}

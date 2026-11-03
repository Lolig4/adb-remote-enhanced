package com.jcapps.adbremote.model

/**
 * RECON-UNSURE: toString prints "Data(id=…" — the original was probably a nested `Data` class of
 * some icon-shape holder; property names after `id` have no plaintext (decrypt keys without
 * comment): `title` / `shape` (`shape` proven by the constructor parameter check).
 */
data class IconShapeData(
    // obf: f9062
    val id: Int,
    // obf: f9061
    val title: Int,
    // obf: f9063
    val shape: IconShape,
) {
    // obf: C2320
}

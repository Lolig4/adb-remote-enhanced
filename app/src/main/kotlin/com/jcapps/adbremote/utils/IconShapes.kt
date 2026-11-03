package com.jcapps.adbremote.utils

import com.jcapps.adbremote.model.IconShape
import com.jcapps.adbremote.model.IconShape.Corner
import com.jcapps.adbremote.model.IconShape.Corners
import com.jcapps.adbremote.model.IconShape.Edge
import com.jcapps.adbremote.model.IconShape.Edges

/** Built-in icon shape presets. RECON-UNSURE: object vs. top-level vals, constant names derived from the preset string names. */
object IconShapes {
    // obf: AbstractC0164

    // obf: f3006
    val CURVED_SQUIRCLE = IconShape(
        Corners(Corner(0, 60), Corner(0, 60), Corner(0, 60), Corner(0, 60)),
        Edges(Edge(1, 20), Edge(1, 20), Edge(1, 20), Edge(1, 20)),
    )

    // obf: f3003
    val CURVED_SQUIRCLE_2 = IconShape(
        Corners(Corner(0, 60), Corner(0, 60), Corner(0, 60), Corner(0, 60)),
        Edges(Edge(2, 20), Edge(2, 20), Edge(2, 20), Edge(2, 20)),
    )

    // obf: f3010
    val CURVED_SQUARE = IconShape(
        Corners(Corner(0, 20), Corner(0, 20), Corner(0, 20), Corner(0, 20)),
        Edges(Edge(2, 20), Edge(2, 20), Edge(2, 20), Edge(2, 20)),
    )

    // obf: f3008
    val CIRCLE = IconShape(
        Corners(Corner(0, 100), Corner(0, 100), Corner(0, 100), Corner(0, 100)),
        Edges(Edge(0, 0), Edge(0, 0), Edge(0, 0), Edge(0, 0)),
    )

    // obf: f3013
    val DIAMOND = IconShape(
        Corners(Corner(1, 100), Corner(1, 100), Corner(1, 100), Corner(1, 100)),
        Edges(Edge(0, 0), Edge(0, 0), Edge(0, 0), Edge(0, 0)),
    )

    // obf: f3012
    val SHARP_SQUARE = IconShape(
        Corners(Corner(0, 0), Corner(0, 0), Corner(0, 0), Corner(0, 0)),
        Edges(Edge(0, 0), Edge(0, 0), Edge(0, 0), Edge(0, 0)),
    )

    // obf: f3007
    val IOS = IconShape(
        Corners(Corner(0, 30), Corner(0, 30), Corner(0, 30), Corner(0, 30)),
        Edges(Edge(0, 0), Edge(0, 0), Edge(0, 0), Edge(0, 0)),
    )

    // obf: f3009
    val ROUNDED_SQUARE = IconShape(
        Corners(Corner(0, 50), Corner(0, 50), Corner(0, 50), Corner(0, 50)),
        Edges(Edge(0, 0), Edge(0, 0), Edge(0, 0), Edge(0, 0)),
    )

    // obf: f3001
    val ONE_UI = IconShape(
        Corners(Corner(0, 70), Corner(0, 70), Corner(0, 70), Corner(0, 70)),
        Edges(Edge(0, 0), Edge(0, 0), Edge(0, 0), Edge(0, 0)),
    )

    // obf: f3004
    val EGG = IconShape(
        Corners(Corner(0, 100), Corner(0, 100), Corner(0, 50), Corner(0, 50)),
        Edges(Edge(0, 0), Edge(0, 0), Edge(0, 0), Edge(0, 0)),
    )

    // obf: f3005
    val OCTAGON = IconShape(
        Corners(Corner(1, 40), Corner(1, 40), Corner(1, 40), Corner(1, 40)),
        Edges(Edge(0, 0), Edge(0, 0), Edge(0, 0), Edge(0, 0)),
    )

    // obf: f3011
    val CURVED_OCTAGON = IconShape(
        Corners(Corner(1, 40), Corner(1, 40), Corner(1, 40), Corner(1, 40)),
        Edges(Edge(1, 20), Edge(1, 20), Edge(1, 20), Edge(1, 20)),
    )

    // obf: f3002
    val TEARDROP = IconShape(
        Corners(Corner(0, 100), Corner(0, 100), Corner(0, 100), Corner(0, 30)),
        Edges(Edge(0, 0), Edge(0, 0), Edge(0, 0), Edge(0, 0)),
    )
}

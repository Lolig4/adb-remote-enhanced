package com.jcapps.adbremote.views

import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapePath

/**
 * Edge that bulges by [size] (scaled by the interpolation) as a quadratic curve through the
 * edge's midpoint. R8 merged this class with Material's `TriangleEdgeTreatment(size, true)`
 * (the `f10115 == 1` branch of C2710).
 */
class CurvedEdgeTreatment(private val size: Float) : EdgeTreatment() {
    // obf: C2710 (case 0)

    // obf: mo2193
    override fun getEdgePath(length: Float, center: Float, interpolation: Float, shapePath: ShapePath) {
        shapePath.quadToPoint(length / 2f, size * interpolation, length, 0f)
    }
}

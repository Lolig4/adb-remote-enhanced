package com.jcapps.adbremote.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.TriangleEdgeTreatment
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.jcapps.adbremote.R
import com.jcapps.adbremote.model.IconShape
import com.jcapps.adbremote.model.IconShapeData
import com.jcapps.adbremote.views.CurvedEdgeTreatment

// obf: AbstractC1480 (file facade)

// obf: f6690
val iconShapes: List<IconShapeData> = listOf(
    IconShapeData(0, R.string.rounded_square, IconShapes.ROUNDED_SQUARE),
    IconShapeData(1, R.string.curved_squircle, IconShapes.CURVED_SQUIRCLE),
    IconShapeData(2, R.string.egg, IconShapes.EGG),
    IconShapeData(3, R.string.octagon, IconShapes.OCTAGON),
    IconShapeData(4, R.string.curved_squircle_2, IconShapes.CURVED_SQUIRCLE_2),
    IconShapeData(5, R.string.curved_square, IconShapes.CURVED_SQUARE),
    IconShapeData(6, R.string.circle, IconShapes.CIRCLE),
    IconShapeData(7, R.string.diamond, IconShapes.DIAMOND),
    IconShapeData(8, R.string.sharp_square, IconShapes.SHARP_SQUARE),
    IconShapeData(9, R.string.ios, IconShapes.IOS),
    IconShapeData(10, R.string.one_ui, IconShapes.ONE_UI),
    IconShapeData(11, R.string.curved_octagon, IconShapes.CURVED_OCTAGON),
    IconShapeData(12, R.string.teardrop, IconShapes.TEARDROP),
)

// obf: m3419
fun getCustomIconShape(dataStore: SharedPreferences): IconShapeData =
    IconShapeData(-1, R.string.custom_icon_shape, parseIconShape(dataStore.getString("custom_icon_shape", null)))

// obf: m3420
fun createShapeAppearanceModel(iconShape: IconShape, size: Float): ShapeAppearanceModel {
    val corners = iconShape.corners
    val edges = iconShape.edges
    var topEdge = EdgeTreatment()
    var rightEdge = EdgeTreatment()
    var bottomEdge = EdgeTreatment()
    var leftEdge = EdgeTreatment()
    val builder = ShapeAppearanceModel.builder()
        .setTopLeftCorner(corners.topLeft.family, percentToSize(size, corners.topLeft.size))
        .setTopRightCorner(corners.topRight.family, percentToSize(size, corners.topRight.size))
        .setBottomLeftCorner(corners.bottomLeft.family, percentToSize(size, corners.bottomLeft.size))
        .setBottomRightCorner(corners.bottomRight.family, percentToSize(size, corners.bottomRight.size))
    if (edges.left.type != 0) {
        leftEdge = createEdgeTreatment(percentToSize(size, edges.left.size), edges.left.type)
    }
    if (edges.top.type != 0) {
        topEdge = createEdgeTreatment(percentToSize(size, edges.top.size), edges.top.type)
    }
    if (edges.right.type != 0) {
        rightEdge = createEdgeTreatment(percentToSize(size, edges.right.size), edges.right.type)
    }
    if (edges.bottom.type != 0) {
        bottomEdge = createEdgeTreatment(percentToSize(size, edges.bottom.size), edges.bottom.type)
    }
    return builder
        .setTopEdge(topEdge)
        .setRightEdge(rightEdge)
        .setBottomEdge(bottomEdge)
        .setLeftEdge(leftEdge)
        .build()
}

// obf: m3421
fun parseIconShape(json: String?): IconShape {
    if (json == null) return IconShapes.ROUNDED_SQUARE
    return try {
        val shape = Gson().fromJson(json, IconShape::class.java)
        val range = 0..100
        if (shape.corners.topLeft.size in range &&
            shape.corners.topRight.size in range &&
            shape.corners.bottomLeft.size in range &&
            shape.corners.bottomRight.size in range &&
            shape.edges.left.size in range &&
            shape.edges.top.size in range &&
            shape.edges.right.size in range &&
            shape.edges.bottom.size in range
        ) {
            shape
        } else {
            IconShapes.ROUNDED_SQUARE
        }
    } catch (_: JsonSyntaxException) {
        IconShapes.ROUNDED_SQUARE
    }
}

// obf: m3422
fun getIconShape(context: Context): IconShapeData {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val index = prefs.getInt("icon_shape", 0)
    return iconShapes.getOrElse(index) { getCustomIconShape(prefs) }
}

// obf: m3423
private fun percentToSize(size: Float, percent: Int): Float = ((size / 2f) / 100) * percent

// obf: m3424
private fun createEdgeTreatment(size: Float, type: Int): EdgeTreatment = when (type) {
    1 -> CurvedEdgeTreatment(size)
    2 -> TriangleEdgeTreatment(size, true)
    else -> EdgeTreatment()
}

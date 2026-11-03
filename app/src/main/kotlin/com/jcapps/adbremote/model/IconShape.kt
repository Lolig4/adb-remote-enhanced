package com.jcapps.adbremote.model

import com.google.gson.annotations.SerializedName

data class IconShape(
    // obf: f8958 / m4481
    @SerializedName("corners") val corners: Corners,
    // obf: f8957 / m4482
    @SerializedName("edges") val edges: Edges,
) {
    // obf: C2281

    data class Corners(
        // obf: f8960 / m4485
        @SerializedName("top_left") val topLeft: Corner,
        // obf: f8959 / m4487
        @SerializedName("top_right") val topRight: Corner,
        // obf: f8962 / m4484
        @SerializedName("bottom_left") val bottomLeft: Corner,
        // obf: f8961 / m4486
        @SerializedName("bottom_right") val bottomRight: Corner,
    ) {
        // obf: C2282
    }

    data class Corner(
        // obf: f8964 / m4489
        @SerializedName("family") val family: Int,
        // obf: f8963 / m4490
        @SerializedName("size") val size: Int,
    ) {
        // obf: C2283
    }

    data class Edges(
        // obf: f8966 / m4494
        @SerializedName("left") val left: Edge,
        // obf: f8965 / m4495
        @SerializedName("top") val top: Edge,
        // obf: f8968 / m4493
        @SerializedName("right") val right: Edge,
        // obf: f8967 / m4492
        @SerializedName("bottom") val bottom: Edge,
    ) {
        // obf: C2284
    }

    data class Edge(
        // obf: f8970 / m4498
        @SerializedName("type") val type: Int,
        // obf: f8969 / m4497
        @SerializedName("size") val size: Int,
    ) {
        // obf: C2285
    }
}

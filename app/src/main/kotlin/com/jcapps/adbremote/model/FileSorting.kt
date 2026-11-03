package com.jcapps.adbremote.model

import com.google.android.gms.measurement.api.AppMeasurementSdk
import com.google.gson.annotations.SerializedName

/** Persisted file-list sort configuration for the Files screen. */
class FileSorting(
    // obf: f7024 / m3703 / m3705
    @SerializedName("sort_by") var sortBy: SortBy,
) {
    // obf: C1602

    // obf: f7023 / m3704 / m3706
    @SerializedName("order_ascending")
    var orderAscending: Boolean = true

    // obf: EnumC1603
    enum class SortBy {
        @SerializedName(AppMeasurementSdk.ConditionalUserProperty.NAME) NAME,
        @SerializedName("size") SIZE,
        @SerializedName("last_modified") LAST_MODIFIED,
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileSorting) return false
        return sortBy == other.sortBy && orderAscending == other.orderAscending
    }

    override fun hashCode(): Int = sortBy.hashCode() * 31 + if (orderAscending) 1231 else 1237

    override fun toString(): String = "FileSorting(sortBy=$sortBy, orderAscending=$orderAscending)"
}

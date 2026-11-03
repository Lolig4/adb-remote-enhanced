package com.jcapps.adbremote.model

import com.google.gson.annotations.SerializedName

// obf: C0133
data class Performance(
    // obf: f2934
    @SerializedName("cpu") val cpuList: List<CPU>,
    // obf: f2933
    @SerializedName("memory") val memory: Memory,
) {
    // obf: C0134
    data class Memory(
        // obf: f2936
        @SerializedName("available") val available: Long,
        // obf: f2935
        @SerializedName("remaining") val inUse: Long,
        // obf: f2937
        @SerializedName("total") val total: Long,
    )

    // obf: C0135
    data class CPU(
        // obf: f2939
        @SerializedName("name") val name: String,
        // obf: f2938
        @SerializedName("percent") val percent: Int,
    )
}

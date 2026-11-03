package com.jcapps.adbremote.model

import com.google.gson.JsonPrimitive
import com.google.gson.annotations.SerializedName

/**
 * Serialized intent description sent to the on-device server to open a file remotely
 * ("open remotely with"). Mutable because a few fields are filled in after construction.
 */
class IntentParams(
    // obf: f8403
    @SerializedName("run_type") val runType: RunType?,
    // obf: f8401
    @SerializedName("action") var action: String?,
    // obf: f8405
    @SerializedName("file_path") var filePath: FilePath?,
    // obf: f8409
    @SerializedName("mime_type") var mimeType: String?,
    // obf: f8400
    @SerializedName("flags") var flags: Int?,
) {
    // obf: C2082

    // obf: f8407
    @SerializedName("data_uri") var dataUri: String? = null
    // obf: f8408
    @SerializedName("category") var category: String? = null
    // obf: f8404 — setter m4269
    @SerializedName("component_name") var componentName: String? = null
    // obf: f8406
    @SerializedName("package_name") var packageName: String? = null
    // obf: f8402
    @SerializedName("extras") var extras: Map<String, JsonPrimitive>? = null

    // obf: EnumC2083
    enum class RunType {
        @SerializedName("shell") SHELL,
        @SerializedName("normal") NORMAL,
    }

    // obf: C2084
    data class FilePath(
        // obf: f8415
        @SerializedName("parent") val parent: String?,
        // obf: f8414
        @SerializedName("fallback_parent") val fallbackParent: String,
        // obf: f8416
        @SerializedName("child") val child: String,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntentParams) return false
        return runType == other.runType && action == other.action && dataUri == other.dataUri &&
            filePath == other.filePath && mimeType == other.mimeType && category == other.category &&
            componentName == other.componentName && packageName == other.packageName &&
            flags == other.flags && extras == other.extras
    }

    override fun hashCode(): Int {
        var result = (runType?.hashCode() ?: 0) * 31
        result = (result + (action?.hashCode() ?: 0)) * 31
        result = (result + (dataUri?.hashCode() ?: 0)) * 31
        result = (result + (filePath?.hashCode() ?: 0)) * 31
        result = (result + (mimeType?.hashCode() ?: 0)) * 31
        result = (result + (category?.hashCode() ?: 0)) * 31
        result = (result + (componentName?.hashCode() ?: 0)) * 31
        result = (result + (packageName?.hashCode() ?: 0)) * 31
        result = (result + (flags?.hashCode() ?: 0)) * 31
        return result + (extras?.hashCode() ?: 0)
    }

    override fun toString(): String =
        "IntentParams(runType=$runType, action=$action, dataUri=$dataUri, filePath=$filePath, " +
            "mimeType=$mimeType, category=$category, componentName=$componentName, " +
            "packageName=$packageName, flags=$flags, extras=$extras)"
}

package com.jcapps.adbremote.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/** Per-device, user-editable FTP server settings (edited in [DialogFtpSettings]). */
@Parcelize
data class FTPSettings(
    // obf: f9605 / m4822 / m4827
    @SerializedName("port") var port: Int,
    // obf: f9610 / m4825 / m4815
    @SerializedName("default_dir") var defaultDir: String?,
    // obf: f9607 / m4817 / m4824
    @SerializedName("fallback_dir") var fallbackDir: String,
    // obf: f9606 / m4819 / m4823
    @SerializedName("read_only") var readOnly: Boolean,
    // obf: f9609 / m4813 / m4828
    @SerializedName("passive_mode") var passiveMode: Boolean,
    // obf: f9612 / m4820 / m4826
    @SerializedName("anonymous_login") var anonymousLogin: Boolean,
    // obf: f9608 / m4821 / m4816
    @SerializedName("username") var username: String,
    // obf: f9611 / m4814 / m4818
    @SerializedName("password") var password: String?,
) : Parcelable {
    // obf: C2532 — static m4812 is the generated copy().
}

package com.jcapps.adbremote.provider.ftp

import com.jcapps.adbremote.ui.files.FilesActivity

/**
 * Supplies the FTP password to [FtpClientHelper.createClient] (MaterialFiles Authenticator). The
 * artifact's instance (merged class C1710, case 6) is created by FilesActivity.onCreate as
 * `FtpClientHelper.authenticator = FtpAuthenticator(this)`; R8 devirtualized the call to
 * `activity.ftpConfig().password`.
 *
 * RECON-UNSURE: named class vs. anonymous object inside FilesActivity.
 */
class FtpAuthenticator(
    // obf: f7314
    private val activity: FilesActivity,
) {
    // obf: C1710 (case 6)

    fun getPassword(authority: FtpAuthority): String? = activity.ftpConfig().password
}

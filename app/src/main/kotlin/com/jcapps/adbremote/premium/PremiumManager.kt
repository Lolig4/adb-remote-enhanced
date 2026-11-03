package com.jcapps.adbremote.premium

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.jcapps.adbremote.AdbRemoteApp
import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.api.remote.ApiClient
import com.jcapps.adbremote.ntp.SntpClient
import com.jcapps.adbremote.utils.PlayStore
import com.jcapps.adbremote.utils.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Premium entitlement of this device: stored (Keystore-encrypted JSON of [PremiumAccess]) under
 * the "access" default-preferences key, validated against the Android ID and the NTP-safe time.
 */
object PremiumManager {
    // obf: C1299

    private const val KEY_ACCESS = "access"

    /** The stored access of this device, or null when unset / undecryptable / unparsable. */
    // obf: m3238
    fun getAccess(context: Context): PremiumAccess? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val encrypted = prefs.getString(KEY_ACCESS, null) ?: return null
        val json = SecureStorage.decrypt(encrypted) ?: return null
        return try {
            Gson().fromJson(json, PremiumAccess::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Adds [addition] to the stored access ([now] = NTP-safe current time) and persists it.
     * Returns the new [PremiumAccess.totalEndsAt].
     */
    // obf: m3239
    fun addAccess(context: Context, now: Long, addition: PremiumGrant): Long {
        val androidId = with(PlayStore) { context.getAndroidId() }
        val current = getAccess(context)
        val totalEndsAt = max(current?.totalEndsAt ?: 0L, now)
        val access = when (addition) {
            is Reward -> PremiumAccess(
                androidId,
                current?.promoEndsAt ?: 0L,
                totalEndsAt + NativeConfig.premiumConstant,
                false,
                null,
            )
            is Promo -> {
                val remainingPromo = max(current?.promoEndsAt ?: 0L, now) - now
                val promoEndsAt = max(addition.endsAt, now)
                PremiumAccess(
                    androidId,
                    promoEndsAt,
                    (totalEndsAt - remainingPromo) + (promoEndsAt - now),
                    false,
                    addition.promoCode,
                )
            }
            is LifetimePromo -> PremiumAccess(androidId, 0L, 0L, true, addition.promoCode)
            else -> throw IllegalStateException()
        }
        saveAccess(context, access)
        return access.totalEndsAt
    }

    /**
     * Reports this device to the backend (`POST update`) on IO; on the main thread calls
     * [onSuccess] when the server answered `status = true`, otherwise
     * `onFailed(isServerError, message)` (`false, null` on HTTP != 200, null body or exception).
     */
    // obf: m3240 (body = suspend lambdas C3084, C2184, C2914)
    fun updateDevice(
        scope: LifecycleCoroutineScope,
        promoCode: String,
        androidId: String,
        isLogout: Boolean,
        onSuccess: () -> Unit,
        onFailed: (Boolean, String?) -> Unit,
    ): Job = scope.launch(Dispatchers.IO) {
        try {
            val request = UpdateDeviceRequest(
                promoCode = promoCode,
                deviceId = androidId,
                deviceVersion = Build.VERSION.SDK_INT,
                deviceName = PlayStore.getDeviceName(),
                isLogout = isLogout,
            )
            val response = ApiClient.createPremiumService().update(request)
            val body = response.body()
            val code = response.code()
            withContext(Dispatchers.Main) {
                // obf: C2184
                if (code != 200 || body == null) {
                    onFailed(false, null)
                } else if (body.status) {
                    onSuccess()
                } else {
                    onFailed(true, body.message)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                // obf: C2914
                onFailed(false, null)
            }
        }
    }

    /** Whether [access] (or the stored one) belongs to this device and is lifetime or not expired. */
    // obf: m3241
    fun isPremiumCached(context: Context, safeTime: SntpClient, access: PremiumAccess?): Boolean {
        val current = access ?: getAccess(context) ?: return false
        if (current.deviceId != with(PlayStore) { context.getAndroidId() }) {
            return false
        }
        if (!current.isLifetime &&
            current.totalEndsAt <= (safeTime.cachedTime()?.timestamp ?: System.currentTimeMillis())
        ) {
            return false
        }
        return true
    }

    /** Drops the promo part of the stored access (logout of a promo code) and persists it. */
    // obf: m3242
    fun removePromo(context: Context, now: Long): PremiumAccess? {
        val access = getAccess(context) ?: return null
        // RECON-UNSURE: arithmetic exactly as decompiled (remaining promo time + now).
        val updated = access.copy(
            promoEndsAt = 0L,
            totalEndsAt = (max(access.promoEndsAt, now) - now) + now,
            isLifetime = false,
            promoCode = null,
        )
        saveAccess(context, updated)
        return updated
    }

    /** Persists [access] encrypted under "access". */
    // obf: m3243
    fun saveAccess(context: Context, access: PremiumAccess) {
        val json = Gson().toJson(access)
        val encrypted = SecureStorage.encrypt(json!!)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY_ACCESS, encrypted)
            .apply()
    }

    /** [isPremiumCached] with the app's [SntpClient]; kills the process if [application] is foreign. */
    // obf: m3244
    fun isPremium(application: Application): Boolean {
        // RECON-UNSURE: inlined app extension (receiver check "<this>"): anti-tamper cast check
        if (application is AdbRemoteApp) {
            return isPremiumCached(application, application.sntpClient, null)
        }
        Process.killProcess(Process.myPid())
        throw IllegalStateException()
    }
}

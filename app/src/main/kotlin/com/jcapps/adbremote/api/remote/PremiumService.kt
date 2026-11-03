package com.jcapps.adbremote.api.remote

import com.jcapps.adbremote.premium.ApplyPromoRequest
import com.jcapps.adbremote.premium.ApplyPromoResponse
import com.jcapps.adbremote.premium.UpdateDeviceRequest
import com.jcapps.adbremote.premium.UpdateDeviceResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Premium backend ([com.jcapps.adbremote.NativeConfig.apiBaseUrl]), built by [ApiClient.createPremiumService]. */
interface PremiumService {
    // obf: InterfaceC3915

    // obf: m6528
    @POST("apply")
    suspend fun apply(@Body request: ApplyPromoRequest): Response<ApplyPromoResponse>

    // obf: m6529
    @POST("update")
    suspend fun update(@Body request: UpdateDeviceRequest): Response<UpdateDeviceResponse>
}

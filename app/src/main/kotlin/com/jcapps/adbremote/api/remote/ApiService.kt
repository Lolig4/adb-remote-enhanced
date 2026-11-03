package com.jcapps.adbremote.api.remote

import com.jcapps.adbremote.model.App
import com.jcapps.adbremote.model.AppDetails
import com.jcapps.adbremote.model.DeviceInfo
import com.jcapps.adbremote.model.FTPConfig
import com.jcapps.adbremote.model.GetClipboard
import com.jcapps.adbremote.model.IntentParams
import com.jcapps.adbremote.model.Performance
import com.jcapps.adbremote.model.SupportedApp
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Retrofit API of the on-device ADB server (`http://<ip>:3125/`, built by [ApiClient.create]).
 *
 * RECON-UNSURE: the method names (R8-renamed) are reconstructed from the endpoint paths; the
 * annotations, paths, parameter names and types are exact.
 */
interface ApiService {
    // obf: InterfaceC3558

    // obf: m6108
    @POST("ftp/stop")
    suspend fun stopFtp(): BaseResponse<String>

    // obf: m6109
    @GET("performance")
    suspend fun getPerformance(): BaseResponse<Performance>

    // obf: m6110
    @GET("app?type=enable")
    suspend fun enableApp(@Query("package_name") packageName: String): BaseResponse<String>

    // obf: m6111
    @FormUrlEncoded
    @POST("ntp")
    suspend fun setNtpServer(@Field("server") server: String): BaseResponse<String>

    // obf: m6112
    @GET("app?type=info")
    suspend fun getAppInfo(@Query("package_name") packageName: String): BaseResponse<AppDetails>

    // obf: m6113
    @POST("clipboard/image")
    @Multipart
    suspend fun setClipboardImage(@Part image: MultipartBody.Part): BaseResponse<String>

    // obf: m6114
    @GET("app?type=launch")
    suspend fun launchApp(@Query("package_name") packageName: String): BaseResponse<String>

    // obf: m6115
    @FormUrlEncoded
    @POST("clipboard/text")
    suspend fun setClipboardText(@Field("text") text: String): BaseResponse<String>

    // obf: m6116
    @FormUrlEncoded
    @POST("run")
    suspend fun run(@Field("command") command: String): BaseResponse<Any>

    // obf: m6117
    @GET("device-info")
    suspend fun getDeviceInfo(): BaseResponse<DeviceInfo>

    // obf: m6118
    @Headers("Content-Type: application/json")
    @POST("ftp/start")
    suspend fun startFtp(@Body config: FTPConfig): BaseResponse<String>

    // obf: m6119
    @GET("ntp")
    suspend fun getNtpServer(): BaseResponse<String>

    // obf: m6120
    @GET("running-apps")
    suspend fun getRunningApps(): BaseResponse<List<App>>

    // obf: m6121
    @GET("app?type=info-settings")
    suspend fun openAppInfoSettings(@Query("package_name") packageName: String): BaseResponse<String>

    // obf: m6122
    @GET("app?type=uninstall")
    suspend fun uninstallApp(@Query("package_name") packageName: String): BaseResponse<String>

    // obf: m6123
    @GET("app?type=clear")
    suspend fun clearAppData(@Query("package_name") packageName: String): BaseResponse<String>

    // obf: m6124 — raw image bytes (AbstractC1398 = okhttp3.ResponseBody)
    @GET("clipboard?type=image")
    suspend fun getClipboardImage(): ResponseBody

    // obf: m6125
    @Headers("Content-Type: application/json")
    @POST("intent")
    suspend fun startIntent(@Body params: IntentParams): BaseResponse<String>

    // obf: m6126
    @GET("app?type=disable")
    suspend fun disableApp(@Query("package_name") packageName: String): BaseResponse<String>

    // obf: m6127
    @GET("apps")
    suspend fun getApps(): BaseResponse<List<App>>

    // obf: m6128
    @GET("clipboard?type=text")
    suspend fun getClipboardText(): BaseResponse<GetClipboard>

    // obf: m6129
    @GET("app?type=store")
    suspend fun openAppInStore(@Query("package_name") packageName: String): BaseResponse<String>

    // obf: m6130
    @GET("supported-apps")
    suspend fun getSupportedApps(
        @Query("mime_type") mimeType: String,
        @Query("file_path") filePath: String?,
        @Query("uri") uri: String?,
    ): BaseResponse<List<SupportedApp>>

    // obf: m6131
    @POST("ntp/delete")
    suspend fun deleteNtpServer(): BaseResponse<String>

    // obf: m6132
    @GET("app?type=stop")
    suspend fun forceStopApp(@Query("package_name") packageName: String): BaseResponse<String>
}

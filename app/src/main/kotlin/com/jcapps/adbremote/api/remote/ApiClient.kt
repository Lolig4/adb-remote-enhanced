package com.jcapps.adbremote.api.remote

import com.google.gson.Gson
import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.api.adb.ServerInstaller
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Builds the Retrofit services: [ApiService] for the on-device ADB server
 * (`http://<ip>:<ServerInstaller.SERVER_CONSTANT>/`, optional 1-day HTTP cache, "token" header)
 * (the premium backend client was removed together with ads/premium).
 */
object ApiClient {
    // obf: C0416

    private const val HTTP_CACHE_DIR = "http_cache"

    // RECON-UNSURE: R8 dropped the Cache maxSize argument entirely (the DiskLruCache ctor in the
    //  artifact takes only (directory, taskRunner)); 10 MiB is a placeholder.
    private const val HTTP_CACHE_SIZE = 10L * 1024 * 1024

    /**
     * ADB-server API for [ipAddress]; when [cacheDir] is given, responses are cached for 1 day
     * (network interceptor rewrites Cache-Control, drops Pragma).
     */
    // obf: m2144 (and m2147 = `create$default` with cacheDir = null)
    fun create(ipAddress: String?, cacheDir: File? = null): ApiService {
        val baseUrl = "http://" + ipAddress + ':' + ServerInstaller.SERVER_CONSTANT + '/'
        val token = NativeConfig.apiInterceptorToken
        val builder = OkHttpClient.Builder()
        if (cacheDir != null) {
            builder.cache(Cache(File(cacheDir, HTTP_CACHE_DIR), HTTP_CACHE_SIZE))
            builder.addNetworkInterceptor(
                // obf: C1212 case 3
                Interceptor { chain ->
                    val cacheControl = CacheControl.Builder()
                        .maxAge(1, TimeUnit.DAYS)
                        .build()
                    chain.proceed(chain.request()).newBuilder()
                        .removeHeader("Pragma")
                        .removeHeader("Cache-Control")
                        .header("Cache-Control", cacheControl.toString())
                        .build()
                },
            )
        }
        builder.addInterceptor(
            // obf: C2265 case 2
            Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("token", token)
                    .build()
                chain.proceed(request)
            },
        )
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(builder.build())
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(ApiService::class.java)
    }

    /** Removes every cached `.../apps` response from the HTTP cache in [cacheDir]. */
    // obf: m2145
    fun clearAppsCache(cacheDir: File) {
        val urls = Cache(File(cacheDir, HTTP_CACHE_DIR), HTTP_CACHE_SIZE).urls()
        while (urls.hasNext()) {
            if (urls.next().endsWith("/apps")) {
                urls.remove()
            }
        }
    }
}

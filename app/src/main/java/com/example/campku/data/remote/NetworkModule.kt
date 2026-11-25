package com.example.campku.data.remote

import android.os.Build
import com.example.campku.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Pembuat Retrofit + OkHttp (interceptor token & pemilihan host otomatis) untuk seluruh aplikasi. */
object NetworkModule {

    private const val PORT = 3000

    /**
     * IP Wi-Fi laptop sebagai cadangan bila HP fisik tidak memakai kabel USB (adb reverse).
     * Emulator tetap memakai 10.0.2.2, sedangkan HP fisik otomatis mencoba 127.0.0.1 (USB adb reverse)
     * lalu fallback ke IP Wi-Fi ini.
     */
    private const val LAN_FALLBACK_HOST = "10.20.30.41"

    private val isEmulator: Boolean by lazy {
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.lowercase().contains("emulator") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
    }

    @Volatile
    private var activeHost: String = if (isEmulator) "10.0.2.2" else "127.0.0.1"

    /** Alamat default untuk inisialisasi Retrofit. */
    const val baseUrl: String = "http://10.0.2.2:3000/"

    /** Alamat aktif yang terakhir berhasil dihubungi (dipakai untuk URL foto & refresh token). */
    fun currentBaseUrl(): String = "http://$activeHost:$PORT/"

    private val hostFallbackInterceptor = Interceptor { chain ->
        val original = chain.request()
        val candidates = if (isEmulator) {
            listOf(activeHost, "10.0.2.2", "127.0.0.1").distinct()
        } else {
            listOf(activeHost, "127.0.0.1", LAN_FALLBACK_HOST, "10.0.2.2").distinct()
        }

        var lastError: IOException? = null
        for (host in candidates) {
            val newUrl = original.url.newBuilder().host(host).port(PORT).build()
            val req = original.newBuilder().url(newUrl).build()
            try {
                val response: Response = chain.proceed(req)
                activeHost = host
                return@Interceptor response
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Tidak dapat terhubung ke server")
    }

    fun createApi(tokenStore: TokenStore): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore) { currentBaseUrl() })
            .addInterceptor(hostFallbackInterceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /** Mengubah path relatif dari server ("uploads/...") menjadi URL penuh. */
    fun photoUrl(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { currentBaseUrl() + it.trimStart('/') }
}



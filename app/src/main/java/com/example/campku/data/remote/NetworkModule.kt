package com.example.campku.data.remote

import com.example.campku.data.local.TokenStore
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Pembuat Retrofit + OkHttp (interceptor token) untuk seluruh aplikasi. */
object NetworkModule {

    /**
     * Alamat backend REST API.
     * - Emulator Android : "http://10.0.2.2:3000/"
     * - HP fisik (Wi-Fi) : ganti dengan IP laptop, mis. "http://192.168.1.10:3000/" (akhiri dengan "/")
     */
    const val baseUrl: String = "http://10.0.2.2:3000/"

    fun createApi(tokenStore: TokenStore): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore, baseUrl))
            .connectTimeout(15, TimeUnit.SECONDS)
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
    fun photoUrl(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { baseUrl + it.trimStart('/') }
}


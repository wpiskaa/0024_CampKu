package com.example.campku.data.remote

import com.example.campku.data.local.TokenStore
import com.example.campku.data.model.RefreshRequest
import com.example.campku.data.model.TokenResponse
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * FR-03: menyisipkan access token pada setiap request, dan bila server membalas 401
 * (token kedaluwarsa) otomatis menukar refresh token lalu mengulang request.
 * Jika refresh token juga ditolak, sesi dihapus sehingga aplikasi kembali ke layar Login.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val baseUrl: String,
) : Interceptor {

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val lock = Any()

    // Klien terpisah tanpa interceptor ini agar tidak terjadi rekursi.
    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (isPublicEndpoint(original)) return chain.proceed(original)

        val usedToken = runBlocking { tokenStore.accessToken() }
        val response = chain.proceed(original.withToken(usedToken))
        if (response.code != 401 || usedToken == null) return response

        // Access token ditolak -> coba perbarui (satu per satu agar tidak balapan).
        val newToken = synchronized(lock) {
            val current = runBlocking { tokenStore.accessToken() }
            when {
                current == null -> null // sudah logout
                current != usedToken -> current // thread lain sudah memperbarui
                else -> refreshTokens()
            }
        }

        return if (newToken != null) {
            response.close()
            chain.proceed(original.withToken(newToken))
        } else {
            response // tetap 401; sesi sudah dibersihkan
        }
    }

    private fun isPublicEndpoint(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.endsWith("/api/auth/login") ||
            path.endsWith("/api/auth/register") ||
            path.endsWith("/api/auth/refresh")
    }

    private fun Request.withToken(token: String?): Request =
        if (token == null) this else newBuilder().header("Authorization", "Bearer $token").build()

    /**
     * @return access token baru, atau null jika refresh token ditolak (sesi dihapus).
     * @throws IOException jika server tidak terjangkau (sesi dipertahankan).
     */
    private fun refreshTokens(): String? {
        val refreshToken = runBlocking { tokenStore.refreshToken() }
        if (refreshToken == null) {
            runBlocking { tokenStore.clear() }
            return null
        }

        val body = gson.toJson(RefreshRequest(refreshToken)).toRequestBody(jsonType)
        val request = Request.Builder().url("${baseUrl}api/auth/refresh").post(body).build()

        refreshClient.newCall(request).execute().use { res ->
            return when {
                res.isSuccessful -> {
                    val tokens = gson.fromJson(res.body?.string(), TokenResponse::class.java)
                    runBlocking { tokenStore.saveTokens(tokens.accessToken, tokens.refreshToken) }
                    tokens.accessToken
                }
                res.code in 400..499 -> {
                    runBlocking { tokenStore.clear() }
                    null
                }
                else -> throw IOException("Gagal memperbarui sesi (HTTP ${res.code})")
            }
        }
    }
}


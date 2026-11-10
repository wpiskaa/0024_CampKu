package com.example.campku.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.campku.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "campku_session")

/** Sesi login: token + data pengguna. */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val user: User,
)

/** Penyimpanan token memakai DataStore Preferences (SRS bagian 4). */
class TokenStore(private val context: Context) {

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val USER_ID = intPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_ROLE = stringPreferencesKey("user_role")
        val USER_PHONE = stringPreferencesKey("user_phone")
    }

    /** null = belum login / sesi berakhir. */
    val sessionFlow: Flow<Session?> = context.sessionDataStore.data.map { it.toSession() }

    suspend fun accessToken(): String? = context.sessionDataStore.data.first()[Keys.ACCESS]

    suspend fun refreshToken(): String? = context.sessionDataStore.data.first()[Keys.REFRESH]

    suspend fun saveSession(accessToken: String, refreshToken: String, user: User) {
        context.sessionDataStore.edit {
            it[Keys.ACCESS] = accessToken
            it[Keys.REFRESH] = refreshToken
            it.putUser(user)
        }
    }

    /** Dipakai interceptor setelah refresh token berhasil. */
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.sessionDataStore.edit {
            it[Keys.ACCESS] = accessToken
            it[Keys.REFRESH] = refreshToken
        }
    }

    suspend fun updateUser(user: User) {
        context.sessionDataStore.edit { prefs ->
            // jangan membuat sesi "setengah" jika sudah logout
            if (prefs[Keys.ACCESS] != null) prefs.putUser(user)
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.putUser(user: User) {
        this[Keys.USER_ID] = user.id
        this[Keys.USER_NAME] = user.name
        this[Keys.USER_EMAIL] = user.email
        this[Keys.USER_ROLE] = user.role
        if (user.phone != null) this[Keys.USER_PHONE] = user.phone else remove(Keys.USER_PHONE)
    }

    private fun Preferences.toSession(): Session? {
        val access = this[Keys.ACCESS] ?: return null
        val refresh = this[Keys.REFRESH] ?: return null
        val id = this[Keys.USER_ID] ?: return null
        val user = User(
            id = id,
            name = this[Keys.USER_NAME].orEmpty(),
            email = this[Keys.USER_EMAIL].orEmpty(),
            role = this[Keys.USER_ROLE].orEmpty(),
            phone = this[Keys.USER_PHONE],
        )
        return Session(access, refresh, user)
    }
}


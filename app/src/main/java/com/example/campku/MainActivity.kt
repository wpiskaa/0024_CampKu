package com.example.campku

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campku.data.local.TokenStore
import com.example.campku.ui.navigation.AppNavHost
import com.example.campku.ui.navigation.SessionState
import com.example.campku.ui.theme.CampKuTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as CampKuApp).container
        setContent {
            val themeMode by container.tokenStore.themeModeFlow
                .collectAsStateWithLifecycle(initialValue = TokenStore.THEME_SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                TokenStore.THEME_DARK -> true
                TokenStore.THEME_LIGHT -> false
                else -> systemDark
            }

            LaunchedEffect(darkTheme) {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            CampKuTheme(darkTheme = darkTheme) {
                val sessionFlow = remember {
                    container.authRepository.sessionFlow.map { session ->
                        if (session == null) SessionState.LoggedOut else SessionState.LoggedIn(session.user)
                    }
                }
                val sessionState by sessionFlow.collectAsStateWithLifecycle(initialValue = SessionState.Loading)

                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavHost(sessionState)
                }
            }
        }
    }
}

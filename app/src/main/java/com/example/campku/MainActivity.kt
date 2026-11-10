package com.example.campku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            CampKuTheme {
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

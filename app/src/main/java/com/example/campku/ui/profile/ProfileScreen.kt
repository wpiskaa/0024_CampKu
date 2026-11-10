package com.example.campku.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.example.campku.appViewModel
import com.example.campku.data.model.User
import com.example.campku.data.repository.AuthRepository
import com.example.campku.ui.components.ConfirmDialog
import com.example.campku.ui.components.InfoRow
import com.example.campku.ui.components.OnResume
import com.example.campku.ui.components.StatusChip
import com.example.campku.ui.navigation.MainScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> = _loggingOut.asStateFlow()

    /** Menyegarkan data akun (mis. nomor HP) dari server; gagal diabaikan agar data lokal tetap tampil. */
    fun refresh() {
        viewModelScope.launch { repo.me() }
    }

    /** Menghapus refresh token di server dan sesi lokal; navigasi ke Login otomatis oleh sesi. */
    fun logout() {
        if (_loggingOut.value) return
        viewModelScope.launch {
            _loggingOut.value = true
            repo.logout()
            _loggingOut.value = false
        }
    }
}

@Composable
fun ProfileScreen(navController: NavHostController, user: User) {
    val vm = appViewModel { ProfileViewModel(it.authRepository) }
    val loggingOut by vm.loggingOut.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    OnResume(vm::refresh)

    MainScreen(navController = navController, isAdmin = user.isAdmin, title = "Profil") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(user.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            StatusChip(
                if (user.isAdmin) "Admin" else "Penyewa",
                if (user.isAdmin) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    InfoRow("Nama", user.name)
                    InfoRow("Email", user.email)
                    InfoRow("Nomor HP", user.phone?.takeIf { it.isNotBlank() } ?: "-")
                    InfoRow("Peran", if (user.isAdmin) "Admin" else "Penyewa")
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { confirmLogout = true },
                enabled = !loggingOut,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(20.dp))
                Text("  Keluar")
            }
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Keluar dari akun?",
            message = "Anda perlu login kembali untuk menggunakan aplikasi.",
            confirmText = "Keluar",
            onConfirm = { confirmLogout = false; vm.logout() },
            onDismiss = { confirmLogout = false },
        )
    }
}


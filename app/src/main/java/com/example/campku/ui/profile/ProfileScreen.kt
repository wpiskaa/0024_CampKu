package com.example.campku.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.example.campku.appViewModel
import com.example.campku.data.model.ProfileUpdateRequest
import com.example.campku.data.model.User
import com.example.campku.data.remote.NetworkModule
import com.example.campku.data.repository.AuthRepository
import com.example.campku.ui.components.AppTextField
import com.example.campku.ui.components.ConfirmDialog
import com.example.campku.ui.components.LoadingButton
import com.example.campku.ui.components.MessageEffect
import com.example.campku.ui.components.OnResume
import com.example.campku.ui.components.StatusChip
import com.example.campku.ui.components.SubScreen
import com.example.campku.ui.navigation.MainScreen
import com.example.campku.ui.navigation.Routes
import com.example.campku.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileConfigState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val idNumber: String = "",
    val password: String = "",
    val serverHost: String = NetworkModule.currentHost(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
)

class ProfileViewModel(private val repo: AuthRepository, initialUser: User) : ViewModel() {
    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> = _loggingOut.asStateFlow()

    private val _state = MutableStateFlow(
        ProfileConfigState(
            name = initialUser.name,
            email = initialUser.email,
            phone = initialUser.phone?.takeIf { it != "-" }.orEmpty(),
            address = initialUser.address.orEmpty(),
            idNumber = initialUser.idNumber.orEmpty(),
            serverHost = NetworkModule.currentHost(),
        ),
    )
    val state: StateFlow<ProfileConfigState> = _state.asStateFlow()

    /** Menyegarkan data akun dari server dan mengisi form konfigurasi. */
    fun refresh() {
        viewModelScope.launch {
            repo.me().onSuccess { u ->
                _state.update {
                    it.copy(
                        name = u.name,
                        email = u.email,
                        phone = u.phone?.takeIf { p -> p != "-" }.orEmpty(),
                        address = u.address.orEmpty(),
                        idNumber = u.idNumber.orEmpty(),
                        serverHost = NetworkModule.currentHost(),
                    )
                }
            }
        }
    }

    fun update(transform: ProfileConfigState.() -> ProfileConfigState) {
        _state.update { it.transform().copy(errors = emptyMap()) }
    }

    fun saveProfile() {
        val s = _state.value
        if (s.saving) return
        val errors = buildMap {
            Validators.required(s.name, "Nama")?.let { put("name", it) }
            Validators.email(s.email)?.let { put("email", it) }
            if (s.phone.isNotBlank()) {
                Validators.phone(s.phone)?.let { put("phone", it) }
            }
            if (s.password.isNotBlank()) {
                Validators.password(s.password)?.let { put("password", it) }
            }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val req = ProfileUpdateRequest(
                name = s.name.trim(),
                email = s.email.trim(),
                phone = s.phone.trim().ifEmpty { null },
                address = s.address.trim().ifEmpty { null },
                idNumber = s.idNumber.trim().ifEmpty { null },
                password = s.password.trim().ifEmpty { null },
            )
            repo.updateProfile(req).fold(
                onSuccess = { u ->
                    _state.update {
                        it.copy(
                            saving = false,
                            name = u.name,
                            email = u.email,
                            phone = u.phone?.takeIf { p -> p != "-" }.orEmpty(),
                            address = u.address.orEmpty(),
                            idNumber = u.idNumber.orEmpty(),
                            password = "",
                            message = "Pengaturan akun disimpan",
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, message = e.message ?: "Gagal menyimpan") }
                },
            )
        }
    }

    fun applyServerHost() {
        NetworkModule.setCustomHost(_state.value.serverHost.trim())
        _state.update {
            it.copy(
                serverHost = NetworkModule.currentHost(),
                message = "Server: ${NetworkModule.currentBaseUrl()}",
            )
        }
    }

    fun resetServerHost() {
        NetworkModule.setCustomHost(null)
        _state.update {
            it.copy(
                serverHost = NetworkModule.currentHost(),
                message = "Server kembali ke mode otomatis",
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
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

/** Profil minimalis: identitas ringkas + ikon gerigi untuk membuka Pengaturan Akun. */
@Composable
fun ProfileScreen(navController: NavHostController, user: User) {
    val vm = appViewModel(key = "profile_${user.id}") { ProfileViewModel(it.authRepository, user) }
    val loggingOut by vm.loggingOut.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    OnResume(vm::refresh)

    MainScreen(
        navController = navController,
        isAdmin = user.isAdmin,
        title = "Profil",
        actions = {
            IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Pengaturan akun")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                state.name.ifBlank { user.name }.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.name.ifBlank { user.name },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                state.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        StatusChip(
                            if (user.isAdmin) "Admin" else "Penyewa",
                            if (user.isAdmin) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        )
                    }
                    val rows = listOf(
                        "No. HP" to state.phone,
                        "Identitas" to state.idNumber,
                        "Alamat" to state.address,
                    ).filter { it.second.isNotBlank() }
                    if (rows.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        rows.forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(
                                    label,
                                    modifier = Modifier.width(80.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    value,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { confirmLogout = true },
                enabled = !loggingOut,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text("Keluar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
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

/** Pengaturan Akun (dibuka dari ikon gerigi di Profil): ubah data akun dan alamat server. */
@Composable
fun AccountSettingsScreen(navController: NavHostController, user: User) {
    val vm = appViewModel(key = "profile_${user.id}") { ProfileViewModel(it.authRepository, user) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showServer by remember { mutableStateOf(false) }

    MessageEffect(state.message, snackbar, vm::consumeMessage)

    SubScreen(
        title = "Pengaturan Akun",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppTextField(
                state.name, { v -> vm.update { copy(name = v) } }, "Nama lengkap",
                error = state.errors["name"],
            )
            AppTextField(
                state.email, { v -> vm.update { copy(email = v) } }, "Email",
                error = state.errors["email"], keyboardType = KeyboardType.Email,
            )
            AppTextField(
                state.phone, { v -> vm.update { copy(phone = v) } }, "No. HP",
                error = state.errors["phone"], keyboardType = KeyboardType.Phone,
            )
            AppTextField(
                state.idNumber, { v -> vm.update { copy(idNumber = v) } }, "No. identitas (KTP/SIM)",
            )
            AppTextField(
                state.address, { v -> vm.update { copy(address = v) } }, "Alamat",
                singleLine = false, minLines = 2,
            )
            AppTextField(
                state.password, { v -> vm.update { copy(password = v) } }, "Password baru (opsional)",
                error = state.errors["password"], isPassword = true,
            )
            Spacer(Modifier.height(4.dp))
            LoadingButton("Simpan", onClick = vm::saveProfile, loading = state.saving)

            // Server: disembunyikan agar halaman tetap ringkas
            TextButton(onClick = { showServer = !showServer }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    if (showServer) "Sembunyikan pengaturan server" else "Pengaturan server",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (showServer) {
                Text(
                    "Aktif: ${NetworkModule.currentBaseUrl()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppTextField(
                    state.serverHost, { v -> vm.update { copy(serverHost = v) } }, "Host / IP server",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = vm::resetServerHost,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Otomatis") }
                    androidx.compose.material3.Button(
                        onClick = vm::applyServerHost,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Terapkan") }
                }
            }
        }
    }
}

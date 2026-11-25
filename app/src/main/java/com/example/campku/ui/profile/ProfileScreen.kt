package com.example.campku.ui.profile

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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.campku.ui.components.InfoRow
import com.example.campku.ui.components.LoadingButton
import com.example.campku.ui.components.MessageEffect
import com.example.campku.ui.components.OnResume
import com.example.campku.ui.components.SectionTitle
import com.example.campku.ui.components.StatusChip
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
                            message = "Profil & konfigurasi data berhasil disimpan",
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, message = e.message ?: "Gagal menyimpan profil") }
                },
            )
        }
    }

    fun applyServerHost() {
        val host = _state.value.serverHost.trim()
        NetworkModule.setCustomHost(host)
        _state.update {
            it.copy(
                serverHost = NetworkModule.currentHost(),
                message = "Alamat server diatur ke ${NetworkModule.currentBaseUrl()}",
            )
        }
    }

    fun resetServerHost() {
        NetworkModule.setCustomHost(null)
        _state.update {
            it.copy(
                serverHost = NetworkModule.currentHost(),
                message = "Alamat server dikembalikan ke mode deteksi otomatis (${NetworkModule.currentBaseUrl()})",
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

@Composable
fun ProfileScreen(navController: NavHostController, user: User) {
    val vm = appViewModel(key = "profile_${user.id}") { ProfileViewModel(it.authRepository, user) }
    val loggingOut by vm.loggingOut.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    var showServerSettings by remember { mutableStateOf(false) }

    OnResume(vm::refresh)
    MessageEffect(state.message, snackbar, vm::consumeMessage)

    MainScreen(
        navController = navController,
        isAdmin = user.isAdmin,
        title = "Profil & Pengaturan",
        snackbarHostState = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.name.ifBlank { user.name }.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                state.name.ifBlank { user.name },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            StatusChip(
                if (user.isAdmin) "Administrator" else "Penyewa",
                if (user.isAdmin) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            )

            // 1. Form Konfigurasi / Edit Data Profil & Identitas
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Konfigurasi Data Profil",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "Ubah data akun, kontak, alamat, nomor identitas, atau password Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    AppTextField(
                        value = state.name,
                        onValueChange = { v -> vm.update { copy(name = v) } },
                        label = "Nama Lengkap",
                        error = state.errors["name"],
                    )
                    AppTextField(
                        value = state.email,
                        onValueChange = { v -> vm.update { copy(email = v) } },
                        label = "Email",
                        error = state.errors["email"],
                        keyboardType = KeyboardType.Email,
                    )
                    AppTextField(
                        value = state.phone,
                        onValueChange = { v -> vm.update { copy(phone = v) } },
                        label = "Nomor HP / WhatsApp",
                        error = state.errors["phone"],
                        keyboardType = KeyboardType.Phone,
                    )
                    AppTextField(
                        value = state.idNumber,
                        onValueChange = { v -> vm.update { copy(idNumber = v) } },
                        label = "No. Identitas (KTP / SIM / KTM)",
                    )
                    AppTextField(
                        value = state.address,
                        onValueChange = { v -> vm.update { copy(address = v) } },
                        label = "Alamat Lengkap",
                        singleLine = false,
                        minLines = 2,
                    )
                    AppTextField(
                        value = state.password,
                        onValueChange = { v -> vm.update { copy(password = v) } },
                        label = "Password Baru (kosongkan jika tidak diganti)",
                        error = state.errors["password"],
                        isPassword = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    LoadingButton(
                        text = "Simpan Perubahan Data",
                        onClick = vm::saveProfile,
                        loading = state.saving,
                    )
                }
            }

            // 2. Pengaturan Manajemen (Khusus Admin)
            if (user.isAdmin) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Pengaturan & Manajemen Admin",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Pintasan konfigurasi data master aplikasi CampKu:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { navController.navigate(Routes.USERS) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.ManageAccounts, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Kelola Akun & Peran Pengguna")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { navController.navigate(Routes.CATEGORIES) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Kategori")
                            }
                            OutlinedButton(
                                onClick = { navController.navigate(Routes.CUSTOMERS) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pelanggan")
                            }
                        }
                    }
                }
            }

            // 3. Pengaturan Koneksi Server (Settings)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Pengaturan Koneksi Server",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Aktif: ${NetworkModule.currentBaseUrl()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showServerSettings = !showServerSettings }) {
                            Text(if (showServerSettings) "Tutup" else "Atur")
                        }
                    }
                    if (showServerSettings) {
                        HorizontalDivider()
                        AppTextField(
                            value = state.serverHost,
                            onValueChange = { v -> vm.update { copy(serverHost = v) } },
                            label = "Host / IP Server (mis. 127.0.0.1 atau 10.20.30.41)",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = vm::applyServerHost,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Terapkan IP")
                            }
                            OutlinedButton(
                                onClick = vm::resetServerHost,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Otomatis")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
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
                Text("  Keluar dari Akun")
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



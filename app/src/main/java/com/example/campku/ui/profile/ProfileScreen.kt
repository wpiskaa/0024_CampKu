package com.example.campku.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.example.campku.R
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
import com.example.campku.ui.navigation.navigateToTab
import com.example.campku.ui.navigation.safePopBack
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

private enum class ProfileInfoModal { HELP, TERMS, TIPS, CONTACT, ABOUT }

/** Profil minimalis dengan ikon gerigi untuk Pengaturan Akun + menu fitur pendukung lengkap. */
@Composable
fun ProfileScreen(navController: NavHostController, user: User) {
    val vm = appViewModel(key = "profile_${user.id}") { ProfileViewModel(it.authRepository, user) }
    val loggingOut by vm.loggingOut.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    var activeModal by remember { mutableStateOf<ProfileInfoModal?>(null) }

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
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Kartu Identitas Ringkas
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
                                .size(54.dp)
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
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        rows.forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    label,
                                    modifier = Modifier.width(78.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    value,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            // 2. Aktivitas & Pesanan
            ProfileMenuSection(title = "Aktivitas & Pesanan") {
                ProfileMenuItem(
                    icon = Icons.Filled.History,
                    title = if (user.isAdmin) "Kelola Riwayat Penyewaan" else "Riwayat Pesanan Saya",
                    subtitle = if (user.isAdmin) "Pantau status sewa, diskon & pengembalian" else "Lihat status & riwayat sewa alat Anda",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { navController.navigateToTab(Routes.RENTALS, user.isAdmin) },
                )
                ProfileMenuDivider()
                ProfileMenuItem(
                    icon = Icons.Filled.AddShoppingCart,
                    title = "Buat Pengajuan Sewa",
                    subtitle = "Pilih alat camping dengan katalog foto",
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = { navController.navigate(Routes.rentalForm()) { launchSingleTop = true } },
                )
                ProfileMenuDivider()
                ProfileMenuItem(
                    icon = Icons.Filled.Terrain,
                    title = "Jelajahi Katalog Alat",
                    subtitle = "Cek stok & harga sewa perlengkapan terbaru",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { navController.navigateToTab(Routes.EQUIPMENTS, user.isAdmin) },
                )
            }

            // 3. Manajemen Khusus Admin
            if (user.isAdmin) {
                ProfileMenuSection(title = "Manajemen Admin") {
                    ProfileMenuItem(
                        icon = Icons.Filled.ManageAccounts,
                        title = "Kelola Akun Pengguna",
                        subtitle = "Atur akun Admin dan Penyewa",
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { navController.navigate(Routes.USERS) { launchSingleTop = true } },
                    )
                    ProfileMenuDivider()
                    ProfileMenuItem(
                        icon = Icons.Filled.Category,
                        title = "Kelola Kategori Alat",
                        subtitle = "Tambah atau ubah kategori perlengkapan",
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = { navController.navigate(Routes.CATEGORIES) { launchSingleTop = true } },
                    )
                    ProfileMenuDivider()
                    ProfileMenuItem(
                        icon = Icons.Filled.Group,
                        title = "Data Pelanggan",
                        subtitle = "Lihat dan perbarui profil penyewa",
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { navController.navigateToTab(Routes.CUSTOMERS, user.isAdmin) },
                    )
                }
            }

            // 4. Bantuan & Informasi Pendukung
            ProfileMenuSection(title = "Bantuan & Informasi") {
                ProfileMenuItem(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = "Pusat Bantuan (FAQ)",
                    subtitle = "Panduan sewa, diskon & pengembalian alat",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { activeModal = ProfileInfoModal.HELP },
                )
                ProfileMenuDivider()
                ProfileMenuItem(
                    icon = Icons.Filled.Checklist,
                    title = "Tips & Checklist Camping",
                    subtitle = "Daftar perlengkapan wajib sebelum berangkat",
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = { activeModal = ProfileInfoModal.TIPS },
                )
                ProfileMenuDivider()
                ProfileMenuItem(
                    icon = Icons.Filled.Description,
                    title = "Syarat & Ketentuan Sewa",
                    subtitle = "Aturan jaminan, foto kondisi & denda",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { activeModal = ProfileInfoModal.TERMS },
                )
                ProfileMenuDivider()
                ProfileMenuItem(
                    icon = Icons.Filled.SupportAgent,
                    title = "Hubungi Admin CampKu",
                    subtitle = "Layanan pelanggan & lokasi basecamp",
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = { activeModal = ProfileInfoModal.CONTACT },
                )
                ProfileMenuDivider()
                ProfileMenuItem(
                    icon = Icons.Filled.Info,
                    title = "Tentang Aplikasi",
                    subtitle = "CampKu v1.0 • Sistem Rental Alat Camping",
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { activeModal = ProfileInfoModal.ABOUT },
                )
            }

            // 5. Tombol Keluar
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

    activeModal?.let { modal ->
        ProfileInfoDialog(modal = modal, onDismiss = { activeModal = null })
    }
}

@Composable
private fun ProfileMenuSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
    )
}

@Composable
private fun ProfileInfoDialog(modal: ProfileInfoModal, onDismiss: () -> Unit) {
    val (title, body) = when (modal) {
        ProfileInfoModal.HELP -> "Pusat Bantuan (FAQ)" to """
            1. Bagaimana cara menyewa alat?
            Buka menu Alat atau tombol Buat Sewa, pilih satu atau beberapa barang beserta jumlahnya, lalu tentukan tanggal sewa dan kembali.

            2. Bagaimana proses pengambilan alat?
            Tunjukkan pesanan Anda ke Admin basecamp. Admin akan memfoto kondisi alat sebelum disewa lalu mengubah status menjadi Disewa.

            3. Apakah tersedia diskon atau bonus hari?
            Admin dapat memberikan potongan diskon (%) maupun bonus hari sewa secara langsung pada pesanan Anda.

            4. Bagaimana cara mengubah data akun?
            Ketuk ikon gerigi (⚙) di pojok kanan atas halaman Profil untuk mengubah nama, email, HP, KTP/SIM, alamat, atau password.
        """.trimIndent()

        ProfileInfoModal.TIPS -> "Tips & Checklist Camping" to """
            Perlengkapan wajib yang disarankan sebelum berangkat:
            • Tenda double layer + pasak & footprint
            • Carrier 50L–60L dengan rain cover
            • Sleeping bag polar & matras gulung
            • Kompor portable + gas hi-cook cadangan
            • Headlamp LED + baterai cadangan
            • Jaket gunung, jas hujan, & P3K pribadi

            Tips: Cek kelengkapan tenda bersama Admin saat pengambilan alat agar perjalanan aman dan nyaman!
        """.trimIndent()

        ProfileInfoModal.TERMS -> "Syarat & Ketentuan Sewa" to """
            • Penyewa wajib melampirkan identitas asli (KTP / SIM / KTM) yang masih berlaku sebagai jaminan.
            • Kondisi alat wajib didokumentasikan dengan foto sebelum disewa dan saat dikembalikan.
            • Keterlambatan pengembalian dikenakan denda otomatis sebesar 10% dari harga sewa harian per hari terlambat.
            • Kerusakan atau kehilangan komponen alat menjadi tanggung jawab penyewa sesuai kesepakatan basecamp.
        """.trimIndent()

        ProfileInfoModal.CONTACT -> "Hubungi Admin CampKu" to """
            Butuh bantuan cepat atau konsultasi alat?

            • WhatsApp Admin : +62 812-3456-7890
            • Email Dukungan : admin@campku.com
            • Jam Operasional: Setiap hari, 08.00 – 21.00 WIB
            • Basecamp       : CampKu Outdoor Gear Store
        """.trimIndent()

        ProfileInfoModal.ABOUT -> "Tentang CampKu" to """
            CampKu v1.0
            Sistem Rental Alat Camping berbasis Android (Jetpack Compose, MVVM, REST API & MySQL).

            Dirancang untuk memudahkan penyewaan perlengkapan pendakian, manajemen stok otomatis, dokumentasi kondisi alat, serta perhitungan biaya & diskon secara transparan.
        """.trimIndent()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = if (modal == ProfileInfoModal.ABOUT) {
            {
                Image(
                    painter = painterResource(id = R.drawable.ic_campku_logo),
                    contentDescription = "Logo CampKu",
                    modifier = Modifier.size(52.dp),
                )
            }
        } else null,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
    )
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
        onBack = { navController.safePopBack() },
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

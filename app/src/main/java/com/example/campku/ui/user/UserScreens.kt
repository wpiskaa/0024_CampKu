package com.example.campku.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.campku.appViewModel
import com.example.campku.data.model.Role
import com.example.campku.data.model.User
import com.example.campku.ui.components.AppTextField
import com.example.campku.ui.components.ConfirmDialog
import com.example.campku.ui.components.DropdownField
import com.example.campku.ui.components.EmptyState
import com.example.campku.ui.components.ErrorState
import com.example.campku.ui.components.ListCard
import com.example.campku.ui.components.LoadingButton
import com.example.campku.ui.components.MessageEffect
import com.example.campku.ui.components.OnResume
import com.example.campku.ui.components.SearchField
import com.example.campku.ui.components.StateContent
import com.example.campku.ui.components.StatusChip
import com.example.campku.ui.components.SubScreen
import com.example.campku.ui.navigation.MainScreen
import com.example.campku.ui.navigation.Routes

private val roleOptions = listOf(0 to "Penyewa", 1 to "Admin")
private fun roleToId(role: String) = if (role == Role.ADMIN) 1 else 0
private fun idToRole(id: Int) = if (id == 1) Role.ADMIN else Role.PENYEWA

@Composable
fun UserListScreen(navController: NavHostController, currentUser: User) {
    val vm = appViewModel { UserListViewModel(it.userRepository) }
    val items by vm.items.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<User?>(null) }

    OnResume(vm::refresh)
    MessageEffect(message, snackbar, vm::consumeMessage)

    MainScreen(
        navController = navController,
        isAdmin = true,
        title = "Pengguna",
        snackbarHostState = snackbar,
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.userForm()) }) {
                Icon(Icons.Filled.Add, "Tambah pengguna")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = search,
                onValueChange = vm::onSearchChange,
                placeholder = "Cari nama atau email",
                modifier = Modifier.padding(16.dp),
            )
            StateContent(items, onRetry = vm::retry) { list ->
                if (list.isEmpty()) {
                    EmptyState(if (search.isBlank()) "Belum ada pengguna" else "Pengguna tidak ditemukan")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(list, key = { it.id }) { u ->
                            val isSelf = u.id == currentUser.id
                            ListCard(onClick = { navController.navigate(Routes.userForm(u.id)) }) {
                                Icon(Icons.Filled.AccountCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(
                                        if (isSelf) "${u.name} (Anda)" else u.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    StatusChip(
                                        if (u.isAdmin) "Admin" else "Penyewa",
                                        if (u.isAdmin) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = { navController.navigate(Routes.userForm(u.id)) }) {
                                    Icon(Icons.Filled.Edit, "Ubah", tint = MaterialTheme.colorScheme.primary)
                                }
                                if (!isSelf) {
                                    IconButton(onClick = { deleteTarget = u }) {
                                        Icon(Icons.Filled.Delete, "Hapus", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Hapus pengguna?",
            message = "Akun \"${target.name}\" akan dihapus permanen.",
            confirmText = "Hapus",
            onConfirm = { vm.delete(target.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
fun UserFormScreen(id: Int, currentUser: User, onBack: () -> Unit) {
    val vm = appViewModel(key = "user_form_$id") { UserFormViewModel(it.userRepository, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val editingSelf = id == currentUser.id

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    SubScreen(title = if (vm.isEdit) "Ubah Pengguna" else "Tambah Pengguna", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> ErrorState(state.loadError!!, vm::load, Modifier.align(Alignment.Center))
                else -> Column(
                    modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppTextField(state.name, { vm.update { copy(name = it) } }, "Nama", error = state.errors["name"])
                    AppTextField(
                        state.email, { vm.update { copy(email = it) } }, "Email",
                        error = state.errors["email"], keyboardType = KeyboardType.Email,
                    )
                    AppTextField(
                        state.phone, { vm.update { copy(phone = it) } }, "Nomor HP (opsional)",
                        error = state.errors["phone"], keyboardType = KeyboardType.Phone,
                    )
                    DropdownField(
                        label = "Peran",
                        options = roleOptions,
                        selectedId = roleToId(state.role),
                        onSelected = { r -> vm.update { copy(role = idToRole(r)) } },
                        enabled = !editingSelf,
                    )
                    if (editingSelf) {
                        Text(
                            "Peran akun Anda sendiri tidak dapat diubah.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppTextField(
                        state.password, { vm.update { copy(password = it) } },
                        if (vm.isEdit) "Password baru (kosongkan jika tidak diganti)" else "Password (min. 6 karakter)",
                        error = state.errors["password"], isPassword = true,
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(8.dp))
                    LoadingButton("Simpan", onClick = vm::save, loading = state.saving)
                }
            }
        }
    }
}


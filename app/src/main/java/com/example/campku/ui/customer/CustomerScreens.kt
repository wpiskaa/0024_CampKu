package com.example.campku.ui.customer

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
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
import com.example.campku.data.model.Customer
import com.example.campku.ui.components.AppTextField
import com.example.campku.ui.components.ConfirmDialog
import com.example.campku.ui.components.EmptyState
import com.example.campku.ui.components.ErrorState
import com.example.campku.ui.components.ListCard
import com.example.campku.ui.components.LoadingButton
import com.example.campku.ui.components.MessageEffect
import com.example.campku.ui.components.OnResume
import com.example.campku.ui.components.SearchField
import com.example.campku.ui.components.StateContent
import com.example.campku.ui.components.SubScreen
import com.example.campku.ui.navigation.MainScreen
import com.example.campku.ui.navigation.Routes

@Composable
fun CustomerListScreen(navController: NavHostController, isAdmin: Boolean) {
    val vm = appViewModel { CustomerListViewModel(it.customerRepository) }
    val items by vm.items.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<Customer?>(null) }

    OnResume(vm::refresh)
    MessageEffect(message, snackbar, vm::consumeMessage)

    MainScreen(
        navController = navController,
        isAdmin = isAdmin,
        title = "Pelanggan",
        snackbarHostState = snackbar,
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.customerForm()) }) {
                Icon(Icons.Filled.Add, "Tambah pelanggan")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = search,
                onValueChange = vm::onSearchChange,
                placeholder = "Cari nama atau nomor HP",
                modifier = Modifier.padding(16.dp),
            )
            StateContent(items, onRetry = vm::retry) { list ->
                if (list.isEmpty()) {
                    EmptyState(if (search.isBlank()) "Belum ada pelanggan" else "Pelanggan tidak ditemukan")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(list, key = { it.id }) { c ->
                            ListCard(onClick = { navController.navigate(Routes.customerForm(c.id)) }) {
                                Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(c.phone, style = MaterialTheme.typography.bodyMedium)
                                    c.address?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { navController.navigate(Routes.customerForm(c.id)) }) {
                                    Icon(Icons.Filled.Edit, "Ubah", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { deleteTarget = c }) {
                                    Icon(Icons.Filled.Delete, "Hapus", tint = MaterialTheme.colorScheme.error)
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
            title = "Hapus pelanggan?",
            message = "Pelanggan \"${target.name}\" akan dihapus. Pelanggan yang punya riwayat sewa tidak dapat dihapus.",
            confirmText = "Hapus",
            onConfirm = { vm.delete(target.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
fun CustomerFormScreen(id: Int, onBack: () -> Unit) {
    val vm = appViewModel(key = "customer_form_$id") { CustomerFormViewModel(it.customerRepository, id) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    SubScreen(title = if (vm.isEdit) "Ubah Pelanggan" else "Tambah Pelanggan", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> ErrorState(state.loadError!!, vm::load, Modifier.align(Alignment.Center))
                else -> Column(
                    modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppTextField(state.name, { vm.update { copy(name = it) } }, "Nama lengkap", error = state.errors["name"])
                    AppTextField(
                        state.phone, { vm.update { copy(phone = it) } }, "Nomor HP",
                        error = state.errors["phone"], keyboardType = KeyboardType.Phone,
                    )
                    AppTextField(
                        state.address, { vm.update { copy(address = it) } }, "Alamat",
                        error = state.errors["address"], singleLine = false, minLines = 2,
                    )
                    AppTextField(
                        state.idNumber, { vm.update { copy(idNumber = it) } }, "Nomor identitas (KTP/SIM)",
                        error = state.errors["idNumber"],
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(8.dp))
                    LoadingButton("Simpan", onClick = vm::save, loading = state.saving)
                }
            }
        }
    }
}


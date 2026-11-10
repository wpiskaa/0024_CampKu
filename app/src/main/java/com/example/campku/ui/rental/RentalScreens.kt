package com.example.campku.ui.rental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.campku.CampKuApp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.campku.appViewModel
import com.example.campku.data.local.PhotoStorage
import com.example.campku.data.model.Rental
import com.example.campku.data.model.RentalStatus
import com.example.campku.ui.components.AppTextField
import com.example.campku.ui.components.ConfirmDialog
import com.example.campku.ui.components.DateField
import com.example.campku.ui.components.DropdownField
import com.example.campku.ui.components.EmptyState
import com.example.campku.ui.components.ErrorState
import com.example.campku.ui.components.InfoRow
import com.example.campku.ui.components.ListCard
import com.example.campku.ui.components.LoadingButton
import com.example.campku.ui.components.MessageEffect
import com.example.campku.ui.components.OnResume
import com.example.campku.ui.components.PhotoImage
import com.example.campku.ui.components.PhotoPickerButtons
import com.example.campku.ui.components.SearchField
import com.example.campku.ui.components.SectionTitle
import com.example.campku.ui.components.StateContent
import com.example.campku.ui.components.StatusChip
import com.example.campku.ui.components.SubScreen
import com.example.campku.ui.navigation.MainScreen
import com.example.campku.ui.navigation.Routes
import com.example.campku.ui.theme.StatusLate
import com.example.campku.ui.theme.StatusOrdered
import com.example.campku.ui.theme.StatusRented
import com.example.campku.ui.theme.StatusReturned
import com.example.campku.util.estimateTotal
import com.example.campku.util.formatDisplayDate
import com.example.campku.util.formatRupiah
import com.example.campku.util.rentalDays
import com.example.campku.util.statusFilterLabel
import com.example.campku.util.statusLabel

fun Rental.statusColor(): Color = when {
    isLate -> StatusLate
    status == RentalStatus.DIPESAN -> StatusOrdered
    status == RentalStatus.DISEWA -> StatusRented
    else -> StatusReturned
}

private val statusFilters = listOf(
    null,
    RentalStatus.DIPESAN,
    RentalStatus.DISEWA,
    RentalStatus.TERLAMBAT,
    RentalStatus.DIKEMBALIKAN,
)

/** Daftar Sewa (cari + filter status). Penyewa hanya melihat miliknya (dibatasi server). */
@Composable
fun RentalListScreen(navController: NavHostController, isAdmin: Boolean) {
    val vm = appViewModel { RentalListViewModel(it.rentalRepository) }
    val items by vm.items.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    OnResume(vm::refresh)
    MessageEffect(message, snackbar, vm::consumeMessage)

    MainScreen(
        navController = navController,
        isAdmin = isAdmin,
        title = if (isAdmin) "Penyewaan" else "Sewa Saya",
        snackbarHostState = snackbar,
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.rentalForm()) }) {
                Icon(Icons.Filled.Add, "Buat sewa")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = search,
                onValueChange = vm::onSearchChange,
                placeholder = if (isAdmin) "Cari pelanggan atau alat" else "Cari nama alat",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(statusFilters) { status ->
                    FilterChip(
                        selected = filter == status,
                        onClick = { vm.onFilterChange(status) },
                        label = { Text(statusFilterLabel(status.orEmpty())) },
                    )
                }
            }
            StateContent(items, onRetry = vm::retry) { list ->
                if (list.isEmpty()) {
                    EmptyState(
                        if (search.isBlank() && filter == null) "Belum ada penyewaan.\nTekan + untuk membuat sewa."
                        else "Penyewaan tidak ditemukan",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(list, key = { it.id }) { r ->
                            RentalCard(r, showCustomer = isAdmin) {
                                navController.navigate(Routes.rentalDetail(r.id))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RentalCard(rental: Rental, showCustomer: Boolean, onClick: () -> Unit) {
    ListCard(onClick = onClick) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rental.equipmentName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                StatusChip(rental.statusLabel(), rental.statusColor())
            }
            if (showCustomer) {
                Text(rental.customerName, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${formatDisplayDate(rental.rentDate)} - ${formatDisplayDate(rental.dueDate)}  •  ${rental.quantity} unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatRupiah(rental.totalPrice + rental.lateFee),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Detail Sewa: rincian biaya, denda, foto pengembalian, dan aksi Admin. */
@Composable
fun RentalDetailScreen(
    id: Int,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val vm = appViewModel(key = "rental_detail_$id") { RentalDetailViewModel(it.rentalRepository, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val photoVersion by vm.photoVersion.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val storage = (LocalContext.current.applicationContext as CampKuApp).container.photoStorage
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmStatus by remember { mutableStateOf<String?>(null) }

    OnResume { vm.load() }
    MessageEffect(message, snackbar, vm::consumeMessage)
    LaunchedEffect(deleted) { if (deleted) onBack() }

    val current = (state as? com.example.campku.ui.components.UiState.Success)?.data

    SubScreen(
        title = "Detail Sewa",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (isAdmin && current != null) {
                if (current.status == RentalStatus.DIPESAN) {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Ubah") }
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Hapus") }
            }
        },
    ) { padding ->
        StateContent(state, onRetry = { vm.load(silent = false) }, modifier = Modifier.padding(padding)) { r ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.equipmentName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    StatusChip(r.statusLabel(), r.statusColor())
                }

                if (r.isLate) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusLate.copy(alpha = 0.12f)),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = StatusLate)
                            Text(
                                "Terlambat ${r.lateDays} hari. Denda berjalan ${formatRupiah(r.lateFee)}.",
                                modifier = Modifier.padding(start = 12.dp),
                                color = StatusLate,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                SectionTitle("Rincian Sewa")
                if (isAdmin) {
                    InfoRow("Pelanggan", r.customerName)
                    InfoRow("No. HP", r.customerPhone ?: "-")
                }
                InfoRow("Jumlah", "${r.quantity} unit")
                InfoRow("Tanggal sewa", formatDisplayDate(r.rentDate))
                InfoRow("Tanggal kembali", formatDisplayDate(r.dueDate))
                if (r.returnDate != null) InfoRow("Dikembalikan", formatDisplayDate(r.returnDate))
                InfoRow("Durasi", "${r.days} hari")
                r.processedByName?.let { InfoRow("Diproses oleh", it) }

                SectionTitle("Biaya")
                InfoRow("Harga / hari", formatRupiah(r.dailyPrice))
                InfoRow("Biaya sewa", formatRupiah(r.totalPrice))
                if (r.lateFee > 0 || r.isLate) {
                    InfoRow("Denda", formatRupiah(r.lateFee), valueColor = StatusLate)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                InfoRow("Total", formatRupiah(r.totalPrice + r.lateFee), valueColor = MaterialTheme.colorScheme.primary)

                // 1. Foto Kondisi Sebelum Disewa
                val hasPickupPhoto = r.pickupPhotoPath != null ||
                    storage.localFileOrNull(PhotoStorage.KIND_PICKUP, r.id) != null
                if (isAdmin || hasPickupPhoto) {
                    SectionTitle("Foto Kondisi Sebelum Disewa")
                    if (hasPickupPhoto) {
                        PhotoImage(
                            kind = PhotoStorage.KIND_PICKUP,
                            id = r.id,
                            serverPath = r.pickupPhotoPath,
                            version = photoVersion,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                        )
                    } else {
                        Text(
                            "Belum ada foto kondisi sebelum disewa (wajib difoto sebelum alat diserahkan).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (isAdmin && r.status == RentalStatus.DIPESAN) {
                        Spacer(Modifier.height(8.dp))
                        PhotoPickerButtons(onPicked = vm::uploadPickupPhoto, enabled = !busy)
                    }
                }

                // 2. Foto Kondisi Setelah Disewa (Saat Dikembalikan)
                val hasReturnPhoto = r.returnPhotoPath != null ||
                    storage.localFileOrNull(PhotoStorage.KIND_RETURN, r.id) != null
                if ((isAdmin && r.status != RentalStatus.DIPESAN) || hasReturnPhoto) {
                    SectionTitle("Foto Kondisi Setelah Disewa (Saat Dikembalikan)")
                    if (hasReturnPhoto) {
                        PhotoImage(
                            kind = PhotoStorage.KIND_RETURN,
                            id = r.id,
                            serverPath = r.returnPhotoPath,
                            version = photoVersion,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                        )
                    } else {
                        Text(
                            "Belum ada foto kondisi setelah disewa (wajib difoto saat alat dikembalikan).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (isAdmin && r.status == RentalStatus.DISEWA) {
                        Spacer(Modifier.height(8.dp))
                        PhotoPickerButtons(onPicked = vm::uploadReturnPhoto, enabled = !busy)
                    }
                }

                if (isAdmin) {
                    Spacer(Modifier.height(16.dp))
                    when (r.status) {
                        RentalStatus.DIPESAN -> {
                            if (!hasPickupPhoto) {
                                Text(
                                    "Ambil/unggah foto kondisi sebelum disewa terlebih dahulu untuk mengaktifkan tombol di bawah.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            LoadingButton(
                                "Mulai Sewa (Tandai Disewa)",
                                onClick = { confirmStatus = RentalStatus.DISEWA },
                                loading = busy,
                                enabled = hasPickupPhoto,
                            )
                        }
                        RentalStatus.DISEWA -> {
                            if (!hasReturnPhoto) {
                                Text(
                                    "Ambil/unggah foto kondisi setelah disewa terlebih dahulu untuk mengaktifkan tombol di bawah.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            LoadingButton(
                                "Tandai Dikembalikan",
                                onClick = { confirmStatus = RentalStatus.DIKEMBALIKAN },
                                loading = busy,
                                enabled = hasReturnPhoto,
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    confirmStatus?.let { next ->
        val toRented = next == RentalStatus.DISEWA
        ConfirmDialog(
            title = if (toRented) "Mulai sewa?" else "Tandai dikembalikan?",
            message = if (toRented) {
                "Foto kondisi sebelum disewa sudah tercatat. Status menjadi Disewa dan stok alat akan berkurang."
            } else {
                "Foto kondisi setelah disewa sudah tercatat. Stok alat akan bertambah dan denda keterlambatan (jika ada) dihitung otomatis."
            },
            confirmText = "Ya, lanjutkan",
            onConfirm = { vm.changeStatus(next); confirmStatus = null },
            onDismiss = { confirmStatus = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus penyewaan?",
            message = "Data penyewaan ini akan dihapus permanen. Jika masih berstatus Disewa, stok akan dikembalikan.",
            confirmText = "Hapus",
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Form penyewaan (Admin memilih pelanggan; Penyewa otomatis untuk dirinya sendiri). */
@Composable
fun RentalFormScreen(
    id: Int,
    presetEquipmentId: Int,
    isAdmin: Boolean,
    onBack: () -> Unit,
) {
    val vm = appViewModel(key = "rental_form_${id}_$presetEquipmentId") {
        RentalFormViewModel(
            it.rentalRepository, it.equipmentRepository, it.customerRepository, id, presetEquipmentId, isAdmin,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    SubScreen(title = if (vm.isEdit) "Ubah Sewa" else "Buat Sewa", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> ErrorState(state.loadError!!, vm::load, Modifier.align(Alignment.Center))
                else -> {
                    val equipmentMap = state.equipments.associateBy { it.id }
                    val days = rentalDays(state.rentDate, state.dueDate)
                    val validLines = state.items.mapNotNull { item ->
                        val eq = item.equipmentId?.let { equipmentMap[it] } ?: return@mapNotNull null
                        val qty = item.quantity.trim().toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                        val sub = estimateTotal(state.rentDate, state.dueDate, eq.dailyPrice, qty) ?: return@mapNotNull null
                        Triple(eq, qty, sub)
                    }
                    val grandTotal = validLines.takeIf { it.isNotEmpty() }?.sumOf { it.third }

                    Column(
                        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isAdmin) {
                            DropdownField(
                                label = "Pelanggan",
                                options = state.customers.map { it.id to "${it.name} (${it.phone})" },
                                selectedId = state.customerId,
                                onSelected = { c -> vm.update { copy(customerId = c) } },
                                error = state.errors["customer"],
                            )
                            if (state.customers.isEmpty()) {
                                Text(
                                    "Belum ada pelanggan. Tambahkan di menu Pelanggan.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        SectionTitle(if (vm.isEdit) "Alat yang Disewa" else "Daftar Alat yang Disewa")
                        state.items.forEachIndexed { index, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!vm.isEdit && state.items.size > 1) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "Alat #${index + 1}",
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            IconButton(onClick = { vm.removeItem(index) }) {
                                                Icon(Icons.Filled.Delete, "Hapus alat", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    DropdownField(
                                        label = "Pilih Alat",
                                        options = state.equipments.map {
                                            it.id to "${it.name} - ${formatRupiah(it.dailyPrice)}/hari (stok ${it.stock})"
                                        },
                                        selectedId = item.equipmentId,
                                        onSelected = { e -> vm.updateItem(index) { copy(equipmentId = e) } },
                                        error = state.errors["equipment_$index"],
                                    )
                                    AppTextField(
                                        item.quantity,
                                        { v -> vm.updateItem(index) { copy(quantity = v.filter(Char::isDigit)) } },
                                        "Jumlah unit",
                                        error = state.errors["quantity_$index"],
                                        keyboardType = KeyboardType.Number,
                                    )
                                }
                            }
                        }

                        if (!vm.isEdit) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = vm::addItem,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Add, null)
                                Text("  Tambah Alat Lain")
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        DateField(
                            "Tanggal sewa", state.rentDate,
                            { v -> vm.update { copy(rentDate = v) } }, error = state.errors["rentDate"],
                        )
                        DateField(
                            "Tanggal kembali", state.dueDate,
                            { v -> vm.update { copy(dueDate = v) } }, error = state.errors["dueDate"],
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Perkiraan Biaya", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    if (grandTotal != null) formatRupiah(grandTotal) else "-",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (days != null && validLines.isNotEmpty()) {
                                    validLines.forEach { (eq, qty, sub) ->
                                        Text(
                                            "• ${eq.name}: $days hari × ${formatRupiah(eq.dailyPrice)} × $qty = ${formatRupiah(sub)}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                Text(
                                    "Denda keterlambatan: 10% dari harga sewa per hari.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }

                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.height(8.dp))
                        LoadingButton(
                            if (vm.isEdit) "Simpan Perubahan" else "Buat Sewa",
                            onClick = vm::save,
                            loading = state.saving,
                        )
                    }
                }
            }
        }
    }
}

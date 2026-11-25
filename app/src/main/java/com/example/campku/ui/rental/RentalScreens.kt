package com.example.campku.ui.rental

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.campku.CampKuApp
import com.example.campku.appViewModel
import com.example.campku.data.local.PhotoStorage
import com.example.campku.data.model.Equipment
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
import com.example.campku.util.addDaysIso
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

/** Kelompok penyewaan per orang penyewa (untuk daftar Admin). */
private data class CustomerRentalGroup(
    val customerId: Int,
    val customerName: String,
    val customerPhone: String?,
    val rentals: List<Rental>,
) {
    val primaryId: Int get() = rentals.first().id
    val totalUnits: Int get() = rentals.sumOf { it.quantity }
    val totalAmount: Int get() = rentals.sumOf { it.totalPrice + it.lateFee }
    val representative: Rental
        get() = rentals.firstOrNull { it.isLate }
            ?: rentals.firstOrNull { it.status == RentalStatus.DISEWA }
            ?: rentals.firstOrNull { it.status == RentalStatus.DIPESAN }
            ?: rentals.first()
}

/**
 * Daftar Sewa (cari + filter status).
 * - Untuk Admin: dikelompokkan berdasarkan Orang Penyewa (pelanggan). Saat diklik, baru mendetail barang apa saja yang disewa.
 * - Untuk Penyewa: menampilkan daftar sewa miliknya beserta info promo 2 hari jadi 3 hari.
 */
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
        title = if (isAdmin) "Daftar Penyewa" else "Sewa Saya",
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
                placeholder = if (isAdmin) "Cari nama penyewa atau alat" else "Cari nama alat",
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
                } else if (isAdmin) {
                    val groups = remember(list) {
                        list.groupBy { it.customerId }.map { (custId, rentals) ->
                            val first = rentals.first()
                            CustomerRentalGroup(
                                customerId = custId,
                                customerName = first.customerName,
                                customerPhone = first.customerPhone,
                                rentals = rentals,
                            )
                        }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(groups, key = { it.customerId }) { group ->
                            CustomerRentalGroupCard(group) {
                                navController.navigate(Routes.rentalDetail(group.primaryId))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(list, key = { it.id }) { r ->
                            RentalCard(r, showCustomer = false) {
                                navController.navigate(Routes.rentalDetail(r.id))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Kartu daftar penyewa untuk Admin: menampilkan nama orang penyewa; saat diklik baru mendetail barangnya. */
@Composable
private fun CustomerRentalGroupCard(group: CustomerRentalGroup, onClick: () -> Unit) {
    val rep = group.representative
    val hasBonus = group.rentals.any { it.bonusDays > 0 }
    ListCard(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    group.customerName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusChip(rep.statusLabel(), rep.statusColor())
            }
            group.customerPhone?.takeIf { it.isNotBlank() && it != "-" }?.let { phone ->
                Text(
                    "HP: $phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${group.rentals.size} jenis barang (${group.totalUnits} unit) • Ketuk untuk detail barang",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatRupiah(group.totalAmount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (hasBonus) {
                    Text(
                        "🎁 Promo +1 Hari",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun RentalCard(rental: Rental, showCustomer: Boolean, onClick: () -> Unit) {
    ListCard(onClick = onClick) {
        PhotoImage(
            kind = PhotoStorage.KIND_EQUIPMENT,
            id = rental.equipmentId,
            serverPath = rental.equipmentPhoto,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rental.equipmentName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusChip(rental.statusLabel(), rental.statusColor())
            }
            if (showCustomer) {
                Text(rental.customerName, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${formatDisplayDate(rental.rentDate)} - ${formatDisplayDate(rental.dueDate)}  •  ${rental.quantity} unit (${rental.days} hari)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rental.bonusDays > 0) {
                Text(
                    "🎁 Sudah termasuk Bonus +${rental.bonusDays} Hari Gratis dari Admin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                formatRupiah(rental.totalPrice + rental.lateFee),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Detail Sewa:
 * - Menampilkan informasi Orang Penyewa beserta seluruh rincian barang yang disewanya.
 * - Setiap barang memiliki foto alat, rincian biaya/denda, tombol promo bonus +1 hari (Admin),
 *   serta foto kondisi sebelum & sesudah sewa.
 */
@Composable
fun RentalDetailScreen(
    id: Int,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
) {
    val vm = appViewModel(key = "rental_detail_$id") { RentalDetailViewModel(it.rentalRepository, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val customerRentals by vm.customerRentals.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val photoVersion by vm.photoVersion.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val storage = (LocalContext.current.applicationContext as CampKuApp).container.photoStorage
    var deleteTarget by remember { mutableStateOf<Rental?>(null) }
    var statusTarget by remember { mutableStateOf<Pair<Rental, String>?>(null) }
    var discountTarget by remember { mutableStateOf<Rental?>(null) }

    OnResume { vm.load() }
    MessageEffect(message, snackbar, vm::consumeMessage)
    LaunchedEffect(deleted) { if (deleted) onBack() }

    SubScreen(
        title = if (isAdmin) "Detail Sewa Pelanggan" else "Detail Sewa",
        onBack = onBack,
        snackbarHostState = snackbar,
    ) { padding ->
        StateContent(state, onRetry = { vm.load(silent = false) }, modifier = Modifier.padding(padding)) { primary ->
            val itemsToShow = if (isAdmin && customerRentals.isNotEmpty()) customerRentals else listOf(primary)
            val grandTotal = itemsToShow.sumOf { it.totalPrice + it.lateFee }
            val totalUnits = itemsToShow.sumOf { it.quantity }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 1. Kartu Ringkasan Orang Penyewa
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    primary.customerName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    "No. HP: ${primary.customerPhone?.takeIf { it.isNotBlank() } ?: "-"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        InfoRow("Total Jenis Barang", "${itemsToShow.size} jenis ($totalUnits unit)")
                        InfoRow(
                            "Total Tagihan Keseluruhan",
                            formatRupiah(grandTotal),
                            valueColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                SectionTitle("Daftar Barang yang Disewa (${itemsToShow.size})")

                // 2. Rincian Setiap Barang yang Disewa oleh Penyewa Tersebut
                itemsToShow.forEachIndexed { index, r ->
                    RentalEquipmentDetailCard(
                        index = index + 1,
                        r = r,
                        isAdmin = isAdmin,
                        busy = busy,
                        photoVersion = photoVersion,
                        storage = storage,
                        onEdit = { onEdit(r.id) },
                        onDelete = { deleteTarget = r },
                        onApplyBonusDay = { vm.applyBonusDay(r.id) },
                        onOpenDiscount = { discountTarget = r },
                        onUploadPickupPhoto = { uri -> vm.uploadPickupPhoto(r.id, uri) },
                        onUploadReturnPhoto = { uri -> vm.uploadReturnPhoto(r.id, uri) },
                        onConfirmStatus = { nextStatus -> statusTarget = r to nextStatus },
                    )
                }
            }
        }
    }

    discountTarget?.let { target ->
        DiscountDialog(
            rental = target,
            onApply = { pct ->
                vm.applyDiscount(target.id, pct)
                discountTarget = null
            },
            onDismiss = { discountTarget = null },
        )
    }

    statusTarget?.let { (rental, next) ->
        val toRented = next == RentalStatus.DISEWA
        ConfirmDialog(
            title = if (toRented) "Mulai sewa ${rental.equipmentName}?" else "Tandai ${rental.equipmentName} dikembalikan?",
            message = if (toRented) {
                "Foto kondisi sebelum disewa sudah tercatat. Status menjadi Disewa dan stok alat akan berkurang."
            } else {
                "Foto kondisi setelah disewa sudah tercatat. Stok alat akan bertambah dan denda keterlambatan (jika ada) dihitung otomatis."
            },
            confirmText = "Ya, lanjutkan",
            onConfirm = { vm.changeStatus(rental.id, next); statusTarget = null },
            onDismiss = { statusTarget = null },
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Hapus ${target.equipmentName}?",
            message = "Data penyewaan barang ini akan dihapus permanen. Jika masih berstatus Disewa, stok akan dikembalikan.",
            confirmText = "Hapus",
            onConfirm = { vm.delete(target.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun RentalEquipmentDetailCard(
    index: Int,
    r: Rental,
    isAdmin: Boolean,
    busy: Boolean,
    photoVersion: Int,
    storage: PhotoStorage,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onApplyBonusDay: () -> Unit,
    onOpenDiscount: () -> Unit,
    onUploadPickupPhoto: (android.net.Uri) -> Unit,
    onUploadReturnPhoto: (android.net.Uri) -> Unit,
    onConfirmStatus: (String) -> Unit,
) {
    val hasPickupPhoto = r.pickupPhotoPath != null ||
        storage.localFileOrNull(PhotoStorage.KIND_PICKUP, r.id) != null
    val hasReturnPhoto = r.returnPhotoPath != null ||
        storage.localFileOrNull(PhotoStorage.KIND_RETURN, r.id) != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhotoImage(
                    kind = PhotoStorage.KIND_EQUIPMENT,
                    id = r.equipmentId,
                    serverPath = r.equipmentPhoto,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Barang #$index",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        r.equipmentName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${formatRupiah(r.dailyPrice)}/hari × ${r.quantity} unit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusChip(r.statusLabel(), r.statusColor())
                    if (isAdmin) {
                        Row {
                            if (r.status == RentalStatus.DIPESAN) {
                                IconButton(onClick = onEdit) {
                                    Icon(Icons.Filled.Edit, "Ubah", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Filled.Delete, "Hapus", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (r.isLate) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusLate.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = StatusLate)
                        Text(
                            "Terlambat ${r.lateDays} hari. Denda berjalan ${formatRupiah(r.lateFee)}.",
                            modifier = Modifier.padding(start = 10.dp),
                            color = StatusLate,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            InfoRow("Tanggal Sewa", formatDisplayDate(r.rentDate))
            InfoRow("Tanggal Kembali", formatDisplayDate(r.dueDate))
            if (r.returnDate != null) InfoRow("Dikembalikan", formatDisplayDate(r.returnDate))
            InfoRow(
                "Durasi Sewa",
                if (r.bonusDays > 0) "${r.days} hari (termasuk +${r.bonusDays} hari bonus promo)"
                else "${r.days} hari",
            )
            r.processedByName?.let { InfoRow("Diproses oleh", it) }
            InfoRow("Biaya Sewa", formatRupiah(r.totalPrice))
            if (r.discountPercent > 0) {
                InfoRow(
                    "Diskon (${r.discountPercent}%)",
                    "-${formatRupiah(r.discountAmount)}",
                    valueColor = MaterialTheme.colorScheme.secondary,
                )
            }
            if (r.lateFee > 0 || r.isLate) {
                InfoRow("Denda", formatRupiah(r.lateFee), valueColor = StatusLate)
            }
            InfoRow(
                "Subtotal Barang",
                formatRupiah(r.totalPrice + r.lateFee),
                valueColor = MaterialTheme.colorScheme.primary,
            )

            // Sistem Diskon & Bonus Hari (Khusus Admin)
            if (isAdmin && r.status != RentalStatus.DIKEMBALIKAN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenDiscount,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (r.discountPercent > 0) "Diskon ${r.discountPercent}%" else "Tambah Diskon", maxLines = 1)
                    }
                    if (r.bonusDays == 0 && r.days >= 2) {
                        OutlinedButton(
                            onClick = onApplyBonusDay,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bonus +1 Hr", maxLines = 1)
                        }
                    }
                }
            }
            if (r.bonusDays > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Termasuk Bonus +${r.bonusDays} Hari Gratis dari Administrator",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // 1. Foto Kondisi Sebelum Disewa
            if (isAdmin || hasPickupPhoto) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "Foto Kondisi Sebelum Disewa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (hasPickupPhoto) {
                    PhotoImage(
                        kind = PhotoStorage.KIND_PICKUP,
                        id = r.id,
                        serverPath = r.pickupPhotoPath,
                        version = photoVersion,
                        modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    Text(
                        "Belum ada foto kondisi sebelum disewa (wajib difoto sebelum alat diserahkan).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (isAdmin && r.status == RentalStatus.DIPESAN) {
                    PhotoPickerButtons(onPicked = onUploadPickupPhoto, enabled = !busy)
                }
            }

            // 2. Foto Kondisi Setelah Disewa (Saat Dikembalikan)
            if ((isAdmin && r.status != RentalStatus.DIPESAN) || hasReturnPhoto) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "Foto Kondisi Setelah Disewa (Saat Dikembalikan)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (hasReturnPhoto) {
                    PhotoImage(
                        kind = PhotoStorage.KIND_RETURN,
                        id = r.id,
                        serverPath = r.returnPhotoPath,
                        version = photoVersion,
                        modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    Text(
                        "Belum ada foto kondisi setelah disewa (wajib difoto saat alat dikembalikan).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (isAdmin && r.status == RentalStatus.DISEWA) {
                    PhotoPickerButtons(onPicked = onUploadReturnPhoto, enabled = !busy)
                }
            }

            if (isAdmin) {
                when (r.status) {
                    RentalStatus.DIPESAN -> {
                        if (!hasPickupPhoto) {
                            Text(
                                "Ambil/unggah foto kondisi sebelum disewa terlebih dahulu untuk mengaktifkan tombol Mulai Sewa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        LoadingButton(
                            "Mulai Sewa ${r.equipmentName}",
                            onClick = { onConfirmStatus(RentalStatus.DISEWA) },
                            loading = busy,
                            enabled = hasPickupPhoto,
                        )
                    }
                    RentalStatus.DISEWA -> {
                        if (!hasReturnPhoto) {
                            Text(
                                "Ambil/unggah foto kondisi setelah disewa terlebih dahulu untuk mengaktifkan tombol Dikembalikan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        LoadingButton(
                            "Tandai ${r.equipmentName} Dikembalikan",
                            onClick = { onConfirmStatus(RentalStatus.DIKEMBALIKAN) },
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

/**
 * Form penyewaan:
 * - Memilih alat menggunakan katalog visual dengan FOTO dan detail nama/kategori/deskripsi/stok barang.
 * - Menampilkan informasi promo Sewa 2 Hari Jadi 3 Hari untuk Penyewa, serta opsi aktivasi promo untuk Admin.
 */
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
    var activePickerIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    SubScreen(title = if (vm.isEdit) "Ubah Sewa" else "Pengajuan Sewa Alat", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> ErrorState(state.loadError!!, vm::load, Modifier.align(Alignment.Center))
                else -> {
                    val equipmentMap = state.equipments.associateBy { it.id }
                    val baseDays = rentalDays(state.rentDate, state.dueDate)
                    val eligibleForBonus = (baseDays ?: 0) >= 2
                    val effectiveBonus = isAdmin && state.bonusDay && eligibleForBonus
                    val finalDueDatePreview = if (effectiveBonus) addDaysIso(state.dueDate, 1) else state.dueDate

                    val validLines = state.items.mapNotNull { item ->
                        val eq = item.equipmentId?.let { equipmentMap[it] } ?: return@mapNotNull null
                        val qty = item.quantity.trim().toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                        val sub = estimateTotal(state.rentDate, state.dueDate, eq.dailyPrice, qty) ?: return@mapNotNull null
                        Triple(eq, qty, sub)
                    }
                    val discountPct = if (isAdmin) (state.discountPercent.trim().toIntOrNull()?.coerceIn(0, 100) ?: 0) else 0
                    val rawTotal = validLines.takeIf { it.isNotEmpty() }?.sumOf { it.third }
                    val discountAmount = rawTotal?.let { (it * discountPct) / 100 } ?: 0
                    val grandTotal = rawTotal?.let { (it - discountAmount).coerceAtLeast(0) }

                    Column(
                        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isAdmin) {
                            DropdownField(
                                label = "Pelanggan / Orang Penyewa",
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

                        SectionTitle(if (vm.isEdit) "Barang yang Disewa" else "Pilih Barang Camping (Dengan Foto & Detail)")
                        state.items.forEachIndexed { index, item ->
                            val selectedEq = item.equipmentId?.let { equipmentMap[it] }
                            val eqError = state.errors["equipment_$index"]
                            val qtyError = state.errors["quantity_$index"]

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                ),
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Barang #${index + 1}",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        if (!vm.isEdit && state.items.size > 1) {
                                            IconButton(onClick = { vm.removeItem(index) }) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    "Hapus barang",
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }

                                    if (selectedEq != null) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { activePickerIndex = index },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                PhotoImage(
                                                    kind = PhotoStorage.KIND_EQUIPMENT,
                                                    id = selectedEq.id,
                                                    serverPath = selectedEq.photoPath,
                                                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)),
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    selectedEq.categoryName?.let { cat ->
                                                        Text(
                                                            cat,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.secondary,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                    }
                                                    Text(
                                                        selectedEq.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                    selectedEq.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                                        Text(
                                                            desc,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                    Text(
                                                        "${formatRupiah(selectedEq.dailyPrice)} / hari  •  Stok: ${selectedEq.stock} unit",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { activePickerIndex = index },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Ganti Barang Lain (Lihat Foto Katalog)")
                                        }
                                    } else {
                                        Button(
                                            onClick = { activePickerIndex = index },
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            shape = RoundedCornerShape(14.dp),
                                        ) {
                                            Icon(Icons.Filled.ShoppingBag, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Pilih Barang Camping (Lihat Foto & Detail)")
                                        }
                                    }

                                    if (eqError != null) {
                                        Text(
                                            eqError,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }

                                    // Kontrol Jumlah Unit
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        val currentQty = item.quantity.trim().toIntOrNull() ?: 1
                                        OutlinedButton(
                                            onClick = {
                                                val next = (currentQty - 1).coerceAtLeast(1)
                                                vm.updateItem(index) { copy(quantity = next.toString()) }
                                            },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(44.dp),
                                        ) {
                                            Icon(Icons.Filled.Remove, "Kurangi")
                                        }
                                        Box(Modifier.weight(1f)) {
                                            AppTextField(
                                                value = item.quantity,
                                                onValueChange = { v ->
                                                    vm.updateItem(index) { copy(quantity = v.filter(Char::isDigit)) }
                                                },
                                                label = "Jumlah unit disewa",
                                                error = qtyError,
                                                keyboardType = KeyboardType.Number,
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val maxStock = selectedEq?.stock ?: 99
                                                val next = (currentQty + 1).coerceAtMost(maxStock.coerceAtLeast(1))
                                                vm.updateItem(index) { copy(quantity = next.toString()) }
                                            },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(44.dp),
                                        ) {
                                            Icon(Icons.Filled.Add, "Tambah")
                                        }
                                    }
                                }
                            }
                        }

                        if (!vm.isEdit) {
                            OutlinedButton(
                                onClick = vm::addItem,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Add, null)
                                Text("  Tambah Barang Lain Sekaligus")
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

                        if (isAdmin) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.LocalOffer, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Diskon & Bonus (Khusus Admin)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(listOf(0, 5, 10, 15, 20, 25)) { pct ->
                                            FilterChip(
                                                selected = discountPct == pct,
                                                onClick = { vm.update { copy(discountPercent = pct.toString()) } },
                                                label = { Text(if (pct == 0) "Tanpa Diskon" else "$pct%") },
                                            )
                                        }
                                    }
                                    AppTextField(
                                        value = state.discountPercent,
                                        onValueChange = { v ->
                                            val digits = v.filter(Char::isDigit).take(3)
                                            val clamped = digits.toIntOrNull()?.coerceIn(0, 100)?.toString() ?: ""
                                            vm.update { copy(discountPercent = clamped) }
                                        },
                                        label = "Diskon Custom (0 - 100%)",
                                        keyboardType = KeyboardType.Number,
                                    )
                                    HorizontalDivider()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "Bonus +1 Hari Gratis (Sewa ≥ 2 Hari)",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                if (effectiveBonus) {
                                                    "Aktif: kembali ${formatDisplayDate(finalDueDatePreview)}"
                                                } else if (eligibleForBonus) {
                                                    "Opsional: tambah +1 hari tanpa biaya tambahan"
                                                } else {
                                                    "Pilih durasi minimal 2 hari"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = effectiveBonus,
                                            onCheckedChange = { checked -> vm.update { copy(bonusDay = checked) } },
                                            enabled = eligibleForBonus,
                                        )
                                    }
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Ringkasan & Perkiraan Biaya", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    if (grandTotal != null) formatRupiah(grandTotal) else "-",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (baseDays != null && validLines.isNotEmpty()) {
                                    validLines.forEach { (eq, qty, sub) ->
                                        Text(
                                            "• ${eq.name}: $baseDays hari × ${formatRupiah(eq.dailyPrice)} × $qty unit = ${formatRupiah(sub)}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (discountPct > 0 && discountAmount > 0) {
                                        Text(
                                            "• Diskon ($discountPct%): -${formatRupiah(discountAmount)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                    if (effectiveBonus) {
                                        Text(
                                            "• Bonus Admin: +1 Hari Gratis (Total durasi ${baseDays + 1} hari, kembali ${formatDisplayDate(finalDueDatePreview)})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                                Text(
                                    "Denda keterlambatan: dihitung 1 hari sewa penuh per hari terlambat.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.height(6.dp))
                        LoadingButton(
                            if (vm.isEdit) "Simpan Perubahan" else "Ajukan Penyewaan",
                            onClick = vm::save,
                            loading = state.saving,
                        )
                    }

                    // Dialog Katalog Pilih Alat dengan Foto & Detail Barang
                    activePickerIndex?.let { rowIndex ->
                        EquipmentPhotoPickerDialog(
                            equipments = state.equipments,
                            selectedId = state.items.getOrNull(rowIndex)?.equipmentId,
                            onSelect = { eq ->
                                vm.updateItem(rowIndex) { copy(equipmentId = eq.id) }
                                activePickerIndex = null
                            },
                            onDismiss = { activePickerIndex = null },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog visual pemilihan alat camping dengan Foto, Nama Barang Detail, Kategori, Deskripsi, Harga, dan Stok.
 */
@Composable
private fun EquipmentPhotoPickerDialog(
    equipments: List<Equipment>,
    selectedId: Int?,
    onSelect: (Equipment) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(equipments, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) equipments
        else equipments.filter {
            it.name.lowercase().contains(q) ||
                it.categoryName?.lowercase()?.contains(q) == true ||
                it.description?.lowercase()?.contains(q) == true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Pilih Barang Camping",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Cari nama atau kategori barang...",
                )
                if (filtered.isEmpty()) {
                    Text(
                        "Barang tidak ditemukan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.id }) { eq ->
                            val isSelected = eq.id == selectedId
                            val outOfStock = eq.stock <= 0
                            Card(
                                onClick = { if (!outOfStock) onSelect(eq) },
                                enabled = !outOfStock,
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    },
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PhotoImage(
                                        kind = PhotoStorage.KIND_EQUIPMENT,
                                        id = eq.id,
                                        serverPath = eq.photoPath,
                                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        eq.categoryName?.let { cat ->
                                            Text(
                                                cat,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        Text(
                                            eq.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        eq.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                            Text(
                                                desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "${formatRupiah(eq.dailyPrice)}/hari",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            StatusChip(
                                                text = if (outOfStock) "Habis" else "Stok ${eq.stock}",
                                                color = if (outOfStock) StatusLate else StatusReturned,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
    )
}

/** Dialog atur potongan diskon (%) khusus Admin pada Detail Sewa. */
@Composable
private fun DiscountDialog(
    rental: Rental,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(rental.discountPercent.toString()) }
    val pct = input.trim().toIntOrNull()?.coerceIn(0, 100) ?: 0
    val payableDays = (rental.days - rental.bonusDays).coerceAtLeast(1)
    val baseTotal = payableDays * rental.dailyPrice * rental.quantity
    val discountAmount = (baseTotal * pct) / 100
    val finalTotal = (baseTotal - discountAmount).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur Diskon Sewa", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    rental.equipmentName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(0, 5, 10, 15, 20, 25, 30, 50)) { preset ->
                        FilterChip(
                            selected = pct == preset,
                            onClick = { input = preset.toString() },
                            label = { Text("$preset%") },
                        )
                    }
                }
                AppTextField(
                    value = input,
                    onValueChange = { v ->
                        val digits = v.filter(Char::isDigit).take(3)
                        input = digits.toIntOrNull()?.coerceIn(0, 100)?.toString() ?: ""
                    },
                    label = "Persentase Diskon (0 - 100%)",
                    keyboardType = KeyboardType.Number,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        InfoRow("Harga normal", formatRupiah(baseTotal))
                        InfoRow("Potongan ($pct%)", "-${formatRupiah(discountAmount)}", valueColor = MaterialTheme.colorScheme.secondary)
                        InfoRow("Total baru", formatRupiah(finalTotal), valueColor = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(pct) }) { Text("Terapkan Diskon") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}


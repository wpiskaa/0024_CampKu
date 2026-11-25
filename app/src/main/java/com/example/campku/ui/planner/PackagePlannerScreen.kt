package com.example.campku.ui.planner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.campku.appViewModel
import com.example.campku.data.local.PhotoStorage
import com.example.campku.data.model.Customer
import com.example.campku.data.model.Equipment
import com.example.campku.data.model.RentalRequest
import com.example.campku.data.repository.CustomerRepository
import com.example.campku.data.repository.EquipmentRepository
import com.example.campku.data.repository.RentalRepository
import com.example.campku.ui.components.AppTextField
import com.example.campku.ui.components.DateField
import com.example.campku.ui.components.DropdownField
import com.example.campku.ui.components.ErrorState
import com.example.campku.ui.components.InfoRow
import com.example.campku.ui.components.LoadingButton
import com.example.campku.ui.components.PhotoImage
import com.example.campku.ui.components.StatusChip
import com.example.campku.ui.components.SubScreen
import com.example.campku.ui.components.appFieldColors
import com.example.campku.ui.theme.StatusLate
import com.example.campku.util.addDaysIso
import com.example.campku.util.formatDisplayDate
import com.example.campku.util.formatRupiah
import com.example.campku.util.todayIso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil

data class PackagePlannerState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val equipments: List<Equipment> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val customerId: Int? = null,
    val peopleInput: String = "",
    val daysInput: String = "",
    val rentDate: String = todayIso(),
    val generated: Boolean = false,
    /** Peta equipmentId -> jumlah unit (String agar bisa diedit manual). */
    val quantities: Map<Int, String> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val savedRentalId: Int? = null,
) {
    val people: Int get() = peopleInput.trim().toIntOrNull() ?: 0
    val days: Int get() = daysInput.trim().toIntOrNull() ?: 0
    val dueDate: String get() = addDaysIso(rentDate, days.coerceAtLeast(1))
}

/** Menghitung rekomendasi jumlah alat otomatis berdasarkan jumlah orang & stok tersedia. */
private fun computeRecommendedQuantities(people: Int, equipments: List<Equipment>): Map<Int, String> {
    if (people <= 0) return emptyMap()
    val result = mutableMapOf<Int, Int>()
    val available = equipments.filter { it.stock > 0 }

    val tent4 = available.firstOrNull {
        val n = it.name.lowercase()
        (n.contains("tenda") || it.categoryName.orEmpty().lowercase().contains("tenda")) && n.contains("4")
    }
    val tent2 = available.firstOrNull {
        val n = it.name.lowercase()
        (n.contains("tenda") || it.categoryName.orEmpty().lowercase().contains("tenda")) && n.contains("2")
    } ?: available.firstOrNull {
        val n = it.name.lowercase()
        (n.contains("tenda") || it.categoryName.orEmpty().lowercase().contains("tenda")) && it.id != tent4?.id
    }

    var remainingPeople = people
    if (tent4 != null && remainingPeople >= 3) {
        val need4 = if (tent2 == null) ceil(remainingPeople / 4.0).toInt() else (remainingPeople / 4) + if (remainingPeople % 4 == 3) 1 else 0
        val alloc4 = need4.coerceAtMost(tent4.stock)
        if (alloc4 > 0) {
            result[tent4.id] = alloc4
            remainingPeople = (remainingPeople - alloc4 * 4).coerceAtLeast(0)
        }
    }
    if (remainingPeople > 0 && tent2 != null) {
        val need2 = ceil(remainingPeople / 2.0).toInt().coerceAtMost(tent2.stock)
        if (need2 > 0) result[tent2.id] = need2
    } else if (remainingPeople > 0 && tent4 != null) {
        val cur = result[tent4.id] ?: 0
        val extra = ceil(remainingPeople / 4.0).toInt().coerceAtMost(tent4.stock - cur)
        if (extra > 0) result[tent4.id] = cur + extra
    }

    available.firstOrNull {
        it.name.lowercase().contains("sleeping") || it.categoryName.orEmpty().lowercase().contains("sleeping")
    }?.let { sb -> result[sb.id] = people.coerceAtMost(sb.stock) }

    available.firstOrNull {
        it.name.lowercase().contains("carrier") || it.categoryName.orEmpty().lowercase().contains("carrier")
    }?.let { carrier -> result[carrier.id] = ceil(people / 2.0).toInt().coerceAtLeast(1).coerceAtMost(carrier.stock) }

    available.firstOrNull {
        it.name.lowercase().contains("kompor") || it.categoryName.orEmpty().lowercase().contains("kompor")
    }?.let { stove -> result[stove.id] = ceil(people / 4.0).toInt().coerceAtLeast(1).coerceAtMost(stove.stock) }

    available.firstOrNull {
        it.name.lowercase().contains("headlamp") || it.name.lowercase().contains("senter") ||
            it.categoryName.orEmpty().lowercase().contains("aksesoris")
    }?.let { lamp -> result[lamp.id] = people.coerceAtMost(lamp.stock) }

    return result.mapValues { it.value.toString() }
}

class PackagePlannerViewModel(
    private val equipmentRepo: EquipmentRepository,
    private val customerRepo: CustomerRepository,
    private val rentalRepo: RentalRepository,
    val isAdmin: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(PackagePlannerState())
    val state: StateFlow<PackagePlannerState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val eqs = equipmentRepo.list(null, null).getOrElse { e ->
                _state.update { it.copy(loading = false, loadError = e.message) }
                return@launch
            }
            val custs = if (isAdmin) {
                customerRepo.list().getOrElse { e ->
                    _state.update { it.copy(loading = false, loadError = e.message) }
                    return@launch
                }
            } else emptyList()

            _state.update {
                it.copy(
                    loading = false,
                    equipments = eqs,
                    customers = custs,
                )
            }
        }
    }

    fun onPeopleChange(raw: String) {
        val digits = raw.filter(Char::isDigit).take(3)
        _state.update { s ->
            val p = digits.toIntOrNull() ?: 0
            val shouldGen = p > 0 && s.days > 0
            s.copy(
                peopleInput = digits,
                generated = shouldGen,
                quantities = if (shouldGen) computeRecommendedQuantities(p, s.equipments) else emptyMap(),
                error = null,
            )
        }
    }

    fun onDaysChange(raw: String) {
        val digits = raw.filter(Char::isDigit).take(2)
        _state.update { s ->
            val d = digits.toIntOrNull() ?: 0
            val shouldGen = s.people > 0 && d > 0
            s.copy(
                daysInput = digits,
                generated = shouldGen,
                quantities = if (shouldGen) {
                    if (s.generated && s.quantities.isNotEmpty()) s.quantities
                    else computeRecommendedQuantities(s.people, s.equipments)
                } else emptyMap(),
                error = null,
            )
        }
    }

    fun resetRecommendation() {
        _state.update { s ->
            if (s.people > 0 && s.days > 0) {
                s.copy(
                    generated = true,
                    quantities = computeRecommendedQuantities(s.people, s.equipments),
                    error = null,
                )
            } else s
        }
    }

    fun setRentDate(date: String) {
        _state.update { it.copy(rentDate = date, error = null) }
    }

    fun setCustomer(id: Int) {
        _state.update { it.copy(customerId = id, error = null) }
    }

    fun setItemQuantityInput(equipmentId: Int, raw: String) {
        val maxStock = _state.value.equipments.firstOrNull { it.id == equipmentId }?.stock ?: 0
        val digits = raw.filter(Char::isDigit).take(3)
        val clamped = if (digits.isEmpty()) "" else (digits.toIntOrNull() ?: 0).coerceIn(0, maxStock).toString()
        _state.update { s ->
            val updated = s.quantities.toMutableMap()
            if (clamped.isEmpty() || clamped == "0") updated.remove(equipmentId) else updated[equipmentId] = clamped
            s.copy(quantities = updated, error = null)
        }
    }

    fun validateBeforeConfirm(): Boolean {
        val s = _state.value
        if (s.people <= 0 || s.days <= 0) {
            _state.update { it.copy(error = "Isi jumlah orang dan durasi sewa minimal 1") }
            return false
        }
        if (isAdmin && s.customerId == null) {
            _state.update { it.copy(error = "Silakan pilih pelanggan terlebih dahulu") }
            return false
        }
        val selected = s.quantities.mapNotNull { (eqId, qtyStr) ->
            val q = qtyStr.toIntOrNull() ?: 0
            if (q > 0) eqId to q else null
        }
        if (selected.isEmpty()) {
            _state.update { it.copy(error = "Pilih minimal 1 barang dalam paket") }
            return false
        }
        return true
    }

    fun submitPackage() {
        val s = _state.value
        if (s.saving) return
        if (!validateBeforeConfirm()) return
        val selected = s.quantities.mapNotNull { (eqId, qtyStr) ->
            val q = qtyStr.toIntOrNull() ?: 0
            if (q > 0) eqId to q else null
        }

        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val custId = if (isAdmin) s.customerId else null
            var firstId: Int? = null
            for ((eqId, qty) in selected) {
                val req = RentalRequest(
                    customerId = custId,
                    equipmentId = eqId,
                    quantity = qty,
                    rentDate = s.rentDate,
                    dueDate = s.dueDate,
                )
                val res = rentalRepo.create(req)
                if (res.isFailure) {
                    _state.update { it.copy(saving = false, error = res.exceptionOrNull()?.message) }
                    return@launch
                }
                if (firstId == null) firstId = res.getOrNull()?.id
            }
            _state.update { it.copy(saving = false, saved = true, savedRentalId = firstId) }
        }
    }
}

/**
 * Paket Otomatis ringkas & hemat ruang:
 * - Default jumlah orang & durasi dimulai dari 0 (kosong) dan tidak menampilkan katalog.
 * - Begitu jumlah orang & durasi diisi manual berupa angka (> 0), rekomendasi otomatis di-generate
 *   dan jumlah tiap barang tetap dapat diatur secara manual.
 */
@Composable
fun PackagePlannerScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onSubmitted: (Int?) -> Unit,
) {
    val vm = appViewModel(key = "package_planner_$isAdmin") {
        PackagePlannerViewModel(it.equipmentRepository, it.customerRepository, it.rentalRepository, isAdmin)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showConfirmSubmit by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onSubmitted(state.savedRentalId) }

    SubScreen(
        title = "Paket Otomatis",
        onBack = onBack,
        snackbarHostState = snackbar,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> ErrorState(state.loadError!!, vm::load, Modifier.align(Alignment.Center))
                else -> {
                    val selectedItems = state.equipments.mapNotNull { eq ->
                        val qty = state.quantities[eq.id]?.toIntOrNull() ?: 0
                        if (qty > 0) eq to qty else null
                    }
                    val totalUnits = selectedItems.sumOf { it.second }
                    val grandTotal = if (state.days > 0) {
                        selectedItems.sumOf { (eq, qty) -> eq.dailyPrice * qty * state.days }
                    } else 0

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Form Input Ringkas (Hemat Ruang — Input Angka Manual)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AppTextField(
                                        value = state.peopleInput,
                                        onValueChange = vm::onPeopleChange,
                                        label = "Jumlah Orang",
                                        keyboardType = KeyboardType.Number,
                                        modifier = Modifier.weight(1f),
                                    )
                                    AppTextField(
                                        value = state.daysInput,
                                        onValueChange = vm::onDaysChange,
                                        label = "Durasi Hari",
                                        keyboardType = KeyboardType.Number,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                DateField(
                                    label = "Tanggal Mulai Sewa",
                                    value = state.rentDate,
                                    onValueChange = vm::setRentDate,
                                )
                                if (state.days > 0) {
                                    Text(
                                        "Kembali: ${formatDisplayDate(state.dueDate)} (${state.days} hari)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (isAdmin) {
                                    DropdownField(
                                        label = "Pelanggan",
                                        options = state.customers.map { it.id to "${it.name} (${it.phone})" },
                                        selectedId = state.customerId,
                                        onSelected = vm::setCustomer,
                                    )
                                }
                            }
                        }

                        if (!state.generated) {
                            // Default kosong: tidak menampilkan katalog sebelum user mengisi jumlah orang & durasi
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp),
                                    )
                                    Text(
                                        "Rekomendasi Paket Otomatis",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Masukkan Jumlah Orang dan Durasi Sewa di atas untuk menghasilkan rekomendasi set alat camping secara otomatis.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        } else {
                            // Header Rekomendasi + Tombol Reset
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Rekomendasi Alat (${selectedItems.size} jenis • $totalUnits unit)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                TextButton(onClick = vm::resetRecommendation) {
                                    Text("Reset", style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            // Daftar Rekomendasi Ringkas & Bisa Diatur Manual
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    state.equipments.forEachIndexed { idx, eq ->
                                        val qtyStr = state.quantities[eq.id].orEmpty()
                                        val qtyInt = qtyStr.toIntOrNull() ?: 0
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            PhotoImage(
                                                kind = PhotoStorage.KIND_EQUIPMENT,
                                                id = eq.id,
                                                serverPath = eq.photoPath,
                                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                Text(
                                                    eq.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    "${formatRupiah(eq.dailyPrice)}/hr • Stok ${eq.stock}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                if (qtyInt > 0) {
                                                    Text(
                                                        formatRupiah(eq.dailyPrice * qtyInt * state.days),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            if (eq.stock <= 0) {
                                                StatusChip("Habis", StatusLate)
                                            } else {
                                                OutlinedTextField(
                                                    value = qtyStr,
                                                    onValueChange = { v -> vm.setItemQuantityInput(eq.id, v) },
                                                    placeholder = { Text("0", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                        textAlign = TextAlign.Center,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = appFieldColors(),
                                                    modifier = Modifier.width(64.dp),
                                                )
                                            }
                                        }
                                        if (idx < state.equipments.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                            )
                                        }
                                    }
                                }
                            }

                            // Ringkasan Biaya Ringkas
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text(
                                            "Estimasi (${state.people} Org • ${state.days} Hr)",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        if (state.people > 1 && grandTotal > 0) {
                                            Text(
                                                "≈ ${formatRupiah(grandTotal / state.people)}/orang",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                    Text(
                                        formatRupiah(grandTotal),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            state.error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }

                            LoadingButton(
                                text = "Ajukan Sewa Paket",
                                onClick = {
                                    if (vm.validateBeforeConfirm()) showConfirmSubmit = true
                                },
                                loading = state.saving,
                                enabled = selectedItems.isNotEmpty(),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (showConfirmSubmit) {
                        val customerName = if (isAdmin) {
                            state.customers.firstOrNull { it.id == state.customerId }?.name
                        } else null
                        AlertDialog(
                            onDismissRequest = { showConfirmSubmit = false },
                            title = { Text("Konfirmasi Pengajuan Paket", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 380.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        "Pastikan detail alat paket dan tanggal sewa berikut sudah sesuai:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    customerName?.let { InfoRow("Pelanggan", it) }
                                    InfoRow("Tanggal Sewa", formatDisplayDate(state.rentDate))
                                    InfoRow("Tanggal Kembali", "${formatDisplayDate(state.dueDate)} (${state.days} hari)")
                                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                                    selectedItems.forEachIndexed { idx, (eq, qty) ->
                                        val sub = eq.dailyPrice * qty * state.days
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("${idx + 1}. ${eq.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                    Text("$qty unit × ${state.days} hari", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Text(formatRupiah(sub), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                                    InfoRow("Total Biaya", formatRupiah(grandTotal), valueColor = MaterialTheme.colorScheme.primary)
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    showConfirmSubmit = false
                                    vm.submitPackage()
                                }) { Text("Ya, Ajukan") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirmSubmit = false }) { Text("Periksa Lagi") }
                            },
                        )
                    }
                }
            }
        }
    }
}

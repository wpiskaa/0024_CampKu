package com.example.campku.ui.planner

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
import com.example.campku.ui.components.DateField
import com.example.campku.ui.components.DropdownField
import com.example.campku.ui.components.ErrorState
import com.example.campku.ui.components.LoadingButton
import com.example.campku.ui.components.PhotoImage
import com.example.campku.ui.components.SectionTitle
import com.example.campku.ui.components.StatusChip
import com.example.campku.ui.components.SubScreen
import com.example.campku.ui.theme.StatusLate
import com.example.campku.ui.theme.StatusReturned
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
    val people: Int = 2,
    val days: Int = 2,
    val rentDate: String = todayIso(),
    /** Peta equipmentId -> jumlah unit yang direkomendasikan/dipilih (0 = tidak dipilih). */
    val quantities: Map<Int, Int> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val dueDate: String get() = addDaysIso(rentDate, days)
}

/** Menghitung rekomendasi jumlah alat otomatis berdasarkan jumlah orang & stok tersedia. */
private fun computeRecommendedQuantities(people: Int, equipments: List<Equipment>): Map<Int, Int> {
    val result = mutableMapOf<Int, Int>()
    val available = equipments.filter { it.stock > 0 }

    // 1. Tenda: cocokkan kapasitas 4 dan kapasitas 2
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

    // 2. Sleeping Bag: 1 per orang
    available.firstOrNull {
        it.name.lowercase().contains("sleeping") || it.categoryName.orEmpty().lowercase().contains("sleeping")
    }?.let { sb ->
        result[sb.id] = people.coerceAtMost(sb.stock)
    }

    // 3. Carrier: 1 per 2 orang (min 1)
    available.firstOrNull {
        it.name.lowercase().contains("carrier") || it.categoryName.orEmpty().lowercase().contains("carrier")
    }?.let { carrier ->
        result[carrier.id] = ceil(people / 2.0).toInt().coerceAtLeast(1).coerceAtMost(carrier.stock)
    }

    // 4. Kompor: 1 per 4 orang (min 1)
    available.firstOrNull {
        it.name.lowercase().contains("kompor") || it.categoryName.orEmpty().lowercase().contains("kompor")
    }?.let { stove ->
        result[stove.id] = ceil(people / 4.0).toInt().coerceAtLeast(1).coerceAtMost(stove.stock)
    }

    // 5. Headlamp / Aksesoris penerangan: 1 per orang
    available.firstOrNull {
        it.name.lowercase().contains("headlamp") || it.name.lowercase().contains("senter") ||
            it.categoryName.orEmpty().lowercase().contains("aksesoris")
    }?.let { lamp ->
        result[lamp.id] = people.coerceAtMost(lamp.stock)
    }

    return result
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

            val initialQty = computeRecommendedQuantities(_state.value.people, eqs)
            _state.update {
                it.copy(
                    loading = false,
                    equipments = eqs,
                    customers = custs,
                    quantities = initialQty,
                )
            }
        }
    }

    fun setPeople(count: Int) {
        val clamped = count.coerceIn(1, 20)
        _state.update { s ->
            s.copy(
                people = clamped,
                quantities = computeRecommendedQuantities(clamped, s.equipments),
                error = null,
            )
        }
    }

    fun setDays(count: Int) {
        _state.update { it.copy(days = count.coerceIn(1, 14), error = null) }
    }

    fun setRentDate(date: String) {
        _state.update { it.copy(rentDate = date, error = null) }
    }

    fun setCustomer(id: Int) {
        _state.update { it.copy(customerId = id, error = null) }
    }

    fun setItemQuantity(equipmentId: Int, qty: Int) {
        val maxStock = _state.value.equipments.firstOrNull { it.id == equipmentId }?.stock ?: 0
        val clamped = qty.coerceIn(0, maxStock)
        _state.update { s ->
            val updated = s.quantities.toMutableMap()
            if (clamped == 0) updated.remove(equipmentId) else updated[equipmentId] = clamped
            s.copy(quantities = updated, error = null)
        }
    }

    fun submitPackage() {
        val s = _state.value
        if (s.saving) return
        if (isAdmin && s.customerId == null) {
            _state.update { it.copy(error = "Silakan pilih pelanggan terlebih dahulu") }
            return
        }
        val selected = s.quantities.filterValues { it > 0 }
        if (selected.isEmpty()) {
            _state.update { it.copy(error = "Pilih minimal 1 barang dalam paket") }
            return
        }

        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val custId = if (isAdmin) s.customerId else null
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
            }
            _state.update { it.copy(saving = false, saved = true) }
        }
    }
}

/** Fitur Unggulan: Rekomendasi Paket Otomatis ("Hitung Kebutuhan Camping") + 1-Klik Ajukan Sewa. */
@Composable
fun PackagePlannerScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
) {
    val vm = appViewModel(key = "package_planner_$isAdmin") {
        PackagePlannerViewModel(it.equipmentRepository, it.customerRepository, it.rentalRepository, isAdmin)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.saved) { if (state.saved) onSubmitted() }

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
                        val qty = state.quantities[eq.id] ?: 0
                        if (qty > 0) eq to qty else null
                    }
                    val totalUnits = selectedItems.sumOf { it.second }
                    val grandTotal = selectedItems.sumOf { (eq, qty) -> eq.dailyPrice * qty * state.days }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Kartu Header Penjelasan
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                                    modifier = Modifier.size(46.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Hitung Kebutuhan Camping",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Text(
                                        "Masukkan jumlah orang & lama sewa, sistem otomatis menyiapkan rekomendasi set alat terbaik.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                    )
                                }
                            }
                        }

                        // Pengaturan Jumlah Orang, Durasi, dan Tanggal
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Jumlah Orang
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Groups, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Jumlah Orang",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    CounterControl(
                                        value = state.people,
                                        suffix = "org",
                                        onMinus = { vm.setPeople(state.people - 1) },
                                        onPlus = { vm.setPeople(state.people + 1) },
                                    )
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(listOf(1, 2, 4, 6, 8)) { p ->
                                        FilterChip(
                                            selected = state.people == p,
                                            onClick = { vm.setPeople(p) },
                                            label = { Text("$p Orang") },
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                // Lama Sewa
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Durasi Sewa",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    CounterControl(
                                        value = state.days,
                                        suffix = "hari",
                                        onMinus = { vm.setDays(state.days - 1) },
                                        onPlus = { vm.setDays(state.days + 1) },
                                    )
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(listOf(1, 2, 3, 4)) { d ->
                                        FilterChip(
                                            selected = state.days == d,
                                            onClick = { vm.setDays(d) },
                                            label = { Text("$d Hari") },
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                DateField(
                                    label = "Tanggal Mulai Sewa",
                                    value = state.rentDate,
                                    onValueChange = vm::setRentDate,
                                )
                                Text(
                                    "Tanggal Kembali: ${formatDisplayDate(state.dueDate)} (${state.days} hari)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                if (isAdmin) {
                                    DropdownField(
                                        label = "Pilih Pelanggan",
                                        options = state.customers.map { it.id to "${it.name} (${it.phone})" },
                                        selectedId = state.customerId,
                                        onSelected = vm::setCustomer,
                                    )
                                }
                            }
                        }

                        // Daftar Rekomendasi Alat
                        SectionTitle("Rekomendasi Set Alat (${selectedItems.size} jenis • $totalUnits unit)")

                        state.equipments.forEach { eq ->
                            val qty = state.quantities[eq.id] ?: 0
                            val isChosen = qty > 0
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isChosen) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                ),
                                border = BorderStroke(
                                    width = if (isChosen) 1.5.dp else 1.dp,
                                    color = if (isChosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PhotoImage(
                                        kind = PhotoStorage.KIND_EQUIPMENT,
                                        id = eq.id,
                                        serverPath = eq.photoPath,
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            eq.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${formatRupiah(eq.dailyPrice)}/hr • Stok ${eq.stock}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (isChosen) {
                                            Text(
                                                "Subtotal: ${formatRupiah(eq.dailyPrice * qty * state.days)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    if (eq.stock <= 0) {
                                        StatusChip("Habis", StatusLate)
                                    } else {
                                        CounterControl(
                                            value = qty,
                                            suffix = "",
                                            onMinus = { vm.setItemQuantity(eq.id, qty - 1) },
                                            onPlus = { vm.setItemQuantity(eq.id, qty + 1) },
                                        )
                                    }
                                }
                            }
                        }

                        // Ringkasan Total Paket
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Estimasi Total Paket (${state.people} Orang • ${state.days} Hari)",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    formatRupiah(grandTotal),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (state.people > 1 && grandTotal > 0) {
                                    Text(
                                        "≈ ${formatRupiah(grandTotal / state.people)} per orang",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }

                        LoadingButton(
                            text = "Ajukan Sewa Paket Ini ($totalUnits Unit)",
                            onClick = vm::submitPackage,
                            loading = state.saving,
                            enabled = selectedItems.isNotEmpty(),
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterControl(
    value: Int,
    suffix: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onMinus,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Kurangi", modifier = Modifier.size(16.dp))
        }
        Text(
            if (suffix.isBlank()) "$value" else "$value $suffix",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        OutlinedButton(
            onClick = onPlus,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Tambah", modifier = Modifier.size(16.dp))
        }
    }
}

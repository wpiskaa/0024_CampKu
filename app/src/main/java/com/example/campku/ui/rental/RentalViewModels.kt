package com.example.campku.ui.rental

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campku.data.model.Customer
import com.example.campku.data.model.Equipment
import com.example.campku.data.model.Rental
import com.example.campku.data.model.RentalRequest
import com.example.campku.data.model.RentalStatus
import com.example.campku.data.repository.CustomerRepository
import com.example.campku.data.repository.EquipmentRepository
import com.example.campku.data.repository.RentalRepository
import com.example.campku.ui.components.ListViewModel
import com.example.campku.ui.components.UiState
import com.example.campku.ui.components.toUiState
import com.example.campku.util.Validators
import com.example.campku.util.addDaysIso
import com.example.campku.util.parseIsoDate
import com.example.campku.util.todayIso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** FR-07: daftar penyewaan, cari (pelanggan/alat/kode booking) dan filter status. null = semua status. */
class RentalListViewModel(private val repo: RentalRepository) : ListViewModel<Rental, String?>(null) {
    override suspend fun fetch(search: String, filter: String?) = repo.list(search, filter)
    override suspend fun deleteById(id: Int) = repo.delete(id)

    fun findByBookingCode(code: String, onFound: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            repo.getByCode(code).fold(
                onSuccess = { onFound(it.id) },
                onFailure = { onError(it.message ?: "Kode booking tidak ditemukan") },
            )
        }
    }
}

/**
 * Detail sewa per pelanggan/transaksi + aksi Admin:
 * menampilkan seluruh barang yang disewa oleh penyewa tersebut, ubah status (FR-10..FR-12),
 * pemberian promo bonus +1 hari oleh Admin, foto sebelum & sesudah sewa, serta hapus.
 */
class RentalDetailViewModel(private val repo: RentalRepository, private val id: Int) : ViewModel() {
    private val _state = MutableStateFlow<UiState<Rental>>(UiState.Loading)
    val state: StateFlow<UiState<Rental>> = _state.asStateFlow()

    /** Seluruh barang sewaan milik pelanggan yang sama agar tampil lengkap dalam satu halaman detail. */
    private val _customerRentals = MutableStateFlow<List<Rental>>(emptyList())
    val customerRentals: StateFlow<List<Rental>> = _customerRentals.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** Bertambah setiap foto lokal diganti, memaksa gambar dimuat ulang. */
    private val _photoVersion = MutableStateFlow(0)
    val photoVersion: StateFlow<Int> = _photoVersion.asStateFlow()

    init {
        load(silent = false)
    }

    fun load(silent: Boolean = true) {
        viewModelScope.launch {
            if (!silent) _state.value = UiState.Loading
            val result = repo.get(id)
            if (silent && result.isFailure && _state.value is UiState.Success) return@launch
            result.onSuccess { primary ->
                val allForCustomer = repo.list(null, null).getOrNull()
                    ?.filter { it.customerId == primary.customerId && it.rentDate == primary.rentDate }
                    ?.sortedWith(compareBy<Rental> { it.id != primary.id }.thenByDescending { it.id })
                    ?: listOf(primary)
                _customerRentals.value = allForCustomer
            }
            _state.value = result.toUiState()
        }
    }

    private fun replaceInList(updated: Rental) {
        _customerRentals.update { list ->
            list.map { if (it.id == updated.id) updated else it }
        }
        val current = (_state.value as? UiState.Success)?.data
        if (current == null || current.id == updated.id) {
            _state.value = UiState.Success(updated)
        }
    }

    /** [next]: [RentalStatus.DISEWA] atau [RentalStatus.DIKEMBALIKAN]. */
    fun changeStatus(targetId: Int, next: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            repo.changeStatus(targetId, next).fold(
                onSuccess = { rental ->
                    replaceInList(rental)
                    _message.value = if (next == RentalStatus.DISEWA) {
                        "Status ${rental.equipmentName} diubah menjadi Disewa, stok berkurang"
                    } else if (rental.lateFee > 0) {
                        "${rental.equipmentName} dikembalikan. Denda keterlambatan dihitung otomatis"
                    } else {
                        "${rental.equipmentName} dikembalikan, stok bertambah"
                    }
                },
                onFailure = { _message.value = it.message },
            )
            _busy.value = false
        }
    }

    /** Promo Admin: sewa minimal 2 hari dapat bonus +1 hari gratis (jadi 3 hari tanpa biaya tambahan). */
    fun applyBonusDay(targetId: Int) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            repo.applyBonusDay(targetId).fold(
                onSuccess = { rental ->
                    replaceInList(rental)
                    _message.value = "Bonus +1 hari gratis berhasil diberikan untuk ${rental.equipmentName} (kembali: ${rental.dueDate})"
                },
                onFailure = { _message.value = it.message },
            )
            _busy.value = false
        }
    }

    /** Sistem Diskon Admin: menerapkan potongan diskon 0-100% pada total biaya sewa. */
    fun applyDiscount(targetId: Int, discountPercent: Int) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            repo.applyDiscount(targetId, discountPercent).fold(
                onSuccess = { rental ->
                    replaceInList(rental)
                    _message.value = if (discountPercent > 0) {
                        "Diskon $discountPercent% berhasil diterapkan untuk ${rental.equipmentName}"
                    } else {
                        "Diskon untuk ${rental.equipmentName} dihapus"
                    }
                },
                onFailure = { _message.value = it.message },
            )
            _busy.value = false
        }
    }

    /** Foto kondisi sebelum disewa: simpan ke filesDir lalu unggah salinannya ke server. */
    fun uploadPickupPhoto(targetId: Int, uri: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val (rental, warning) = repo.savePickupPhoto(targetId, uri)
            _photoVersion.update { it + 1 }
            if (rental != null) replaceInList(rental)
            _message.value = warning ?: "Foto kondisi sebelum disewa disimpan"
            _busy.value = false
        }
    }

    /** FR-14: simpan foto pengembalian ke filesDir lalu unggah salinannya ke server. */
    fun uploadReturnPhoto(targetId: Int, uri: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val (rental, warning) = repo.saveReturnPhoto(targetId, uri)
            _photoVersion.update { it + 1 }
            if (rental != null) replaceInList(rental)
            _message.value = warning ?: "Foto kondisi setelah disewa disimpan"
            _busy.value = false
        }
    }

    fun delete(targetId: Int = id) {
        viewModelScope.launch {
            _busy.value = true
            repo.delete(targetId).fold(
                onSuccess = {
                    val remaining = _customerRentals.value.filterNot { it.id == targetId }
                    _customerRentals.value = remaining
                    if (remaining.isEmpty()) {
                        _deleted.value = true
                    } else {
                        _state.value = UiState.Success(remaining.first())
                        _message.value = "Barang sewaan dihapus"
                    }
                },
                onFailure = { _message.value = it.message },
            )
            _busy.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/** Satu baris pilihan alat + jumlah pada form penyewaan. */
data class RentalItemInput(
    val equipmentId: Int? = null,
    val quantity: String = "1",
)

data class RentalFormState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val customers: List<Customer> = emptyList(),
    val equipments: List<Equipment> = emptyList(),
    val customerId: Int? = null,
    val items: List<RentalItemInput> = listOf(RentalItemInput()),
    val rentDate: String = todayIso(),
    val dueDate: String = addDaysIso(todayIso(), 2),
    val bonusDay: Boolean = false,
    val discountPercent: String = "0",
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

/**
 * Form penyewaan. id = 0 berarti baru (bisa memilih beberapa alat sekaligus).
 * Admin memilih pelanggan; Penyewa otomatis untuk dirinya sendiri.
 * [presetEquipmentId] dipakai saat membuka form dari Detail Alat.
 */
class RentalFormViewModel(
    private val repo: RentalRepository,
    private val equipmentRepo: EquipmentRepository,
    private val customerRepo: CustomerRepository,
    private val id: Int,
    private val presetEquipmentId: Int,
    val isAdmin: Boolean,
) : ViewModel() {

    val isEdit get() = id != 0

    private val _state = MutableStateFlow(
        RentalFormState(items = listOf(RentalItemInput(equipmentId = presetEquipmentId.takeIf { it > 0 }))),
    )
    val state: StateFlow<RentalFormState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val equipments = equipmentRepo.list(null, null).getOrElse { e ->
                _state.update { it.copy(loading = false, loadError = e.message) }
                return@launch
            }
            val customers = if (isAdmin) {
                customerRepo.list().getOrElse { e ->
                    _state.update { it.copy(loading = false, loadError = e.message) }
                    return@launch
                }
            } else {
                emptyList()
            }

            if (!isEdit) {
                _state.update { it.copy(loading = false, equipments = equipments, customers = customers) }
                return@launch
            }
            repo.get(id).fold(
                onSuccess = { r ->
                    _state.update {
                        it.copy(
                            loading = false,
                            equipments = equipments,
                            customers = customers,
                            customerId = r.customerId,
                            items = listOf(RentalItemInput(equipmentId = r.equipmentId, quantity = r.quantity.toString())),
                            rentDate = r.rentDate,
                            dueDate = r.dueDate,
                            bonusDay = r.bonusDays > 0,
                            discountPercent = r.discountPercent.toString(),
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, loadError = e.message) } },
            )
        }
    }

    fun update(transform: RentalFormState.() -> RentalFormState) =
        _state.update { it.transform().copy(errors = emptyMap(), error = null) }

    fun updateItem(index: Int, transform: RentalItemInput.() -> RentalItemInput) = update {
        copy(items = items.mapIndexed { i, item -> if (i == index) item.transform() else item })
    }

    fun addItem() = update {
        copy(items = items + RentalItemInput())
    }

    fun removeItem(index: Int) = update {
        if (items.size <= 1) this else copy(items = items.filterIndexed { i, _ -> i != index })
    }

    /** FR-13: stok cukup, tanggal kembali tidak sebelum tanggal sewa, form tidak kosong. */
    fun save() {
        val s = _state.value
        if (s.saving) return
        val equipmentMap = s.equipments.associateBy { it.id }

        val errors = buildMap {
            if (isAdmin && s.customerId == null) put("customer", "Pelanggan wajib dipilih")
            s.items.forEachIndexed { i, item ->
                val eq = item.equipmentId?.let { equipmentMap[it] }
                if (eq == null) put("equipment_$i", "Alat wajib dipilih")
                Validators.positiveInt(item.quantity, "Jumlah")?.let { put("quantity_$i", it) }
            }
            // Cek total jumlah per alat terhadap stok yang tersedia
            val totalQtyByEq = mutableMapOf<Int, Int>()
            s.items.forEachIndexed { i, item ->
                val eqId = item.equipmentId ?: return@forEachIndexed
                val qty = item.quantity.trim().toIntOrNull() ?: return@forEachIndexed
                if (qty > 0) totalQtyByEq[eqId] = (totalQtyByEq[eqId] ?: 0) + qty
                val eq = equipmentMap[eqId]
                if (eq != null && (totalQtyByEq[eqId] ?: 0) > eq.stock && !containsKey("quantity_$i")) {
                    put("quantity_$i", "Stok ${eq.name} tidak cukup (tersedia ${eq.stock})")
                }
            }
            if (s.rentDate.isBlank()) put("rentDate", "Tanggal sewa wajib diisi")
            if (s.dueDate.isBlank()) {
                put("dueDate", "Tanggal kembali wajib diisi")
            } else {
                val a = parseIsoDate(s.rentDate)
                val b = parseIsoDate(s.dueDate)
                if (a != null && b != null && b < a) put("dueDate", "Tanggal kembali tidak boleh sebelum tanggal sewa")
            }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }

        // Gabungkan baris yang memilih alat yang sama agar tidak duplikat
        val mergedItems = s.items
            .groupBy { it.equipmentId!! }
            .map { (eqId, rows) -> eqId to rows.sumOf { it.quantity.trim().toInt() } }

        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val customerId = if (isAdmin) s.customerId else null
            val applyBonus = isAdmin && s.bonusDay
            val discountPct = if (isAdmin) (s.discountPercent.trim().toIntOrNull()?.coerceIn(0, 100) ?: 0) else 0
            if (isEdit) {
                val (eqId, qty) = mergedItems.first()
                val req = RentalRequest(customerId, eqId, qty, s.rentDate, s.dueDate, bonusDay = applyBonus, discountPercent = discountPct)
                repo.update(id, req).fold(
                    onSuccess = { _state.update { it.copy(saving = false, saved = true) } },
                    onFailure = { e -> _state.update { it.copy(saving = false, error = e.message) } },
                )
            } else {
                for ((eqId, qty) in mergedItems) {
                    val req = RentalRequest(customerId, eqId, qty, s.rentDate, s.dueDate, bonusDay = applyBonus, discountPercent = discountPct)
                    val res = repo.create(req)
                    if (res.isFailure) {
                        _state.update { it.copy(saving = false, error = res.exceptionOrNull()?.message) }
                        return@launch
                    }
                }
                _state.update { it.copy(saving = false, saved = true) }
            }
        }
    }
}


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

/** FR-07: daftar penyewaan, cari (pelanggan/alat) dan filter status. null = semua status. */
class RentalListViewModel(private val repo: RentalRepository) : ListViewModel<Rental, String?>(null) {
    override suspend fun fetch(search: String, filter: String?) = repo.list(search, filter)
    override suspend fun deleteById(id: Int) = repo.delete(id)
}

/** Detail sewa + aksi Admin: ubah status (FR-10..FR-12), foto pengembalian, hapus. */
class RentalDetailViewModel(private val repo: RentalRepository, private val id: Int) : ViewModel() {
    private val _state = MutableStateFlow<UiState<Rental>>(UiState.Loading)
    val state: StateFlow<UiState<Rental>> = _state.asStateFlow()

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
            _state.value = result.toUiState()
        }
    }

    /** [next]: [RentalStatus.DISEWA] atau [RentalStatus.DIKEMBALIKAN]. */
    fun changeStatus(next: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            repo.changeStatus(id, next).fold(
                onSuccess = { rental ->
                    _state.value = UiState.Success(rental)
                    _message.value = if (next == RentalStatus.DISEWA) {
                        "Status diubah menjadi Disewa, stok berkurang"
                    } else if (rental.lateFee > 0) {
                        "Dikembalikan. Denda keterlambatan dihitung otomatis"
                    } else {
                        "Dikembalikan, stok bertambah"
                    }
                },
                onFailure = { _message.value = it.message },
            )
            _busy.value = false
        }
    }

    /** FR-14: simpan foto ke filesDir lalu unggah salinannya ke server. */
    fun uploadReturnPhoto(uri: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val (rental, warning) = repo.saveReturnPhoto(id, uri)
            _photoVersion.update { it + 1 }
            if (rental != null) _state.value = UiState.Success(rental)
            _message.value = warning ?: "Foto pengembalian disimpan"
            _busy.value = false
        }
    }

    fun delete() {
        viewModelScope.launch {
            _busy.value = true
            repo.delete(id).fold(
                onSuccess = { _deleted.value = true },
                onFailure = { _message.value = it.message },
            )
            _busy.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

data class RentalFormState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val customers: List<Customer> = emptyList(),
    val equipments: List<Equipment> = emptyList(),
    val customerId: Int? = null,
    val equipmentId: Int? = null,
    val quantity: String = "1",
    val rentDate: String = todayIso(),
    val dueDate: String = addDaysIso(todayIso(), 1),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val selectedEquipment: Equipment? get() = equipments.firstOrNull { it.id == equipmentId }
}

/**
 * Form penyewaan. id = 0 berarti baru. Admin memilih pelanggan; Penyewa otomatis untuk dirinya sendiri.
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

    private val _state = MutableStateFlow(RentalFormState(equipmentId = presetEquipmentId.takeIf { it > 0 }))
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
                            equipmentId = r.equipmentId,
                            quantity = r.quantity.toString(),
                            rentDate = r.rentDate,
                            dueDate = r.dueDate,
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, loadError = e.message) } },
            )
        }
    }

    fun update(transform: RentalFormState.() -> RentalFormState) =
        _state.update { it.transform().copy(errors = emptyMap(), error = null) }

    /** FR-13: stok cukup, tanggal kembali tidak sebelum tanggal sewa, form tidak kosong. */
    fun save() {
        val s = _state.value
        if (s.saving) return
        val equipment = s.selectedEquipment
        val errors = buildMap {
            if (isAdmin && s.customerId == null) put("customer", "Pelanggan wajib dipilih")
            if (equipment == null) put("equipment", "Alat wajib dipilih")
            Validators.positiveInt(s.quantity, "Jumlah")?.let { put("quantity", it) }
            val qty = s.quantity.trim().toIntOrNull()
            if (!containsKey("quantity") && equipment != null && qty != null && qty > equipment.stock) {
                put("quantity", "Stok tidak cukup (tersedia ${equipment.stock})")
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

        val request = RentalRequest(
            customerId = if (isAdmin) s.customerId else null,
            equipmentId = equipment!!.id,
            quantity = s.quantity.trim().toInt(),
            rentDate = s.rentDate,
            dueDate = s.dueDate,
        )
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = if (isEdit) repo.update(id, request) else repo.create(request)
            result.fold(
                onSuccess = { _state.update { it.copy(saving = false, saved = true) } },
                onFailure = { e -> _state.update { it.copy(saving = false, error = e.message) } },
            )
        }
    }
}


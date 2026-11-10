package com.example.campku.ui.equipment

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campku.data.model.Category
import com.example.campku.data.model.Equipment
import com.example.campku.data.model.EquipmentRequest
import com.example.campku.data.repository.CategoryRepository
import com.example.campku.data.repository.EquipmentRepository
import com.example.campku.ui.components.ListViewModel
import com.example.campku.ui.components.UiState
import com.example.campku.ui.components.toUiState
import com.example.campku.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** FR-04: daftar alat, cari berdasarkan nama, filter kategori. */
class EquipmentListViewModel(
    private val repo: EquipmentRepository,
    private val categoryRepo: CategoryRepository,
) : ListViewModel<Equipment, Int?>(null) {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch { categoryRepo.list().onSuccess { _categories.value = it } }
    }

    override suspend fun fetch(search: String, filter: Int?) = repo.list(search, filter)
    override suspend fun deleteById(id: Int) = repo.delete(id)
}

class EquipmentDetailViewModel(private val repo: EquipmentRepository, private val id: Int) : ViewModel() {
    private val _state = MutableStateFlow<UiState<Equipment>>(UiState.Loading)
    val state: StateFlow<UiState<Equipment>> = _state.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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

    fun delete() {
        viewModelScope.launch {
            repo.delete(id).fold(
                onSuccess = { _deleted.value = true },
                onFailure = { _message.value = it.message },
            )
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

data class EquipmentFormState(
    val loading: Boolean = false,
    val loadError: String? = null,
    val categories: List<Category> = emptyList(),
    val name: String = "",
    val categoryId: Int? = null,
    val price: String = "",
    val stock: String = "",
    val description: String = "",
    val existingPhotoPath: String? = null,
    val photoUri: Uri? = null,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val savedWarning: String? = null,
    val saved: Boolean = false,
)

/** FR-04 + FR-13 + FR-14: tambah/ubah alat dengan validasi dan foto. id = 0 berarti alat baru. */
class EquipmentFormViewModel(
    private val repo: EquipmentRepository,
    private val categoryRepo: CategoryRepository,
    private val id: Int,
) : ViewModel() {

    val isEdit get() = id != 0

    private val _state = MutableStateFlow(EquipmentFormState(loading = true))
    val state: StateFlow<EquipmentFormState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val categories = categoryRepo.list().getOrElse {
                _state.update { s -> s.copy(loading = false, loadError = it.message) }
                return@launch
            }
            if (!isEdit) {
                _state.update { it.copy(loading = false, categories = categories) }
                return@launch
            }
            repo.get(id).fold(
                onSuccess = { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            categories = categories,
                            name = e.name,
                            categoryId = e.categoryId,
                            price = e.dailyPrice.toString(),
                            stock = e.stock.toString(),
                            description = e.description.orEmpty(),
                            existingPhotoPath = e.photoPath,
                        )
                    }
                },
                onFailure = { err -> _state.update { it.copy(loading = false, loadError = err.message) } },
            )
        }
    }

    fun update(transform: EquipmentFormState.() -> EquipmentFormState) =
        _state.update { it.transform().copy(errors = emptyMap(), error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val errors = buildMap {
            Validators.required(s.name, "Nama alat")?.let { put("name", it) }
            if (s.categoryId == null) put("category", "Kategori wajib dipilih")
            Validators.nonNegativeInt(s.price, "Harga sewa")?.let { put("price", it) }
            Validators.nonNegativeInt(s.stock, "Stok")?.let { put("stock", it) }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }

        val request = EquipmentRequest(
            categoryId = s.categoryId!!,
            name = s.name.trim(),
            dailyPrice = s.price.trim().toInt(),
            stock = s.stock.trim().toInt(),
            description = s.description.trim().ifEmpty { null },
        )
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val saved = if (isEdit) repo.update(id, request) else repo.create(request)
            saved.fold(
                onSuccess = { equipment ->
                    var warning: String? = null
                    s.photoUri?.let { uri -> warning = repo.savePhoto(equipment.id, uri).second }
                    _state.update { it.copy(saving = false, saved = true, savedWarning = warning) }
                },
                onFailure = { e -> _state.update { it.copy(saving = false, error = e.message) } },
            )
        }
    }
}


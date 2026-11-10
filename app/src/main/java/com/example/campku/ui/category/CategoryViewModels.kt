package com.example.campku.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campku.data.model.Category
import com.example.campku.data.model.CategoryRequest
import com.example.campku.data.repository.CategoryRepository
import com.example.campku.ui.components.ListViewModel
import com.example.campku.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** FR-05: daftar kategori + cari nama. */
class CategoryListViewModel(private val repo: CategoryRepository) : ListViewModel<Category, Unit>(Unit) {
    override suspend fun fetch(search: String, filter: Unit) = repo.list(search)
    override suspend fun deleteById(id: Int) = repo.delete(id)
}

data class CategoryFormState(
    val loading: Boolean = false,
    val loadError: String? = null,
    val name: String = "",
    val description: String = "",
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

/** Tambah/ubah kategori. id = 0 berarti baru. */
class CategoryFormViewModel(private val repo: CategoryRepository, private val id: Int) : ViewModel() {
    val isEdit get() = id != 0

    private val _state = MutableStateFlow(CategoryFormState(loading = isEdit))
    val state: StateFlow<CategoryFormState> = _state.asStateFlow()

    init {
        if (isEdit) load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            repo.get(id).fold(
                onSuccess = { c ->
                    _state.update { it.copy(loading = false, name = c.name, description = c.description.orEmpty()) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, loadError = e.message) } },
            )
        }
    }

    fun update(transform: CategoryFormState.() -> CategoryFormState) =
        _state.update { it.transform().copy(errors = emptyMap(), error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val nameError = Validators.required(s.name, "Nama kategori")
        if (nameError != null) {
            _state.update { it.copy(errors = mapOf("name" to nameError)) }
            return
        }
        val request = CategoryRequest(s.name.trim(), s.description.trim().ifEmpty { null })
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


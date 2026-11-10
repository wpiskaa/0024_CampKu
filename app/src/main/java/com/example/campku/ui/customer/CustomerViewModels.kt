package com.example.campku.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campku.data.model.Customer
import com.example.campku.data.model.CustomerRequest
import com.example.campku.data.repository.CustomerRepository
import com.example.campku.ui.components.ListViewModel
import com.example.campku.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** FR-06: daftar pelanggan + cari nama atau HP. */
class CustomerListViewModel(private val repo: CustomerRepository) : ListViewModel<Customer, Unit>(Unit) {
    override suspend fun fetch(search: String, filter: Unit) = repo.list(search)
    override suspend fun deleteById(id: Int) = repo.delete(id)
}

data class CustomerFormState(
    val loading: Boolean = false,
    val loadError: String? = null,
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val idNumber: String = "",
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class CustomerFormViewModel(private val repo: CustomerRepository, private val id: Int) : ViewModel() {
    val isEdit get() = id != 0

    private val _state = MutableStateFlow(CustomerFormState(loading = isEdit))
    val state: StateFlow<CustomerFormState> = _state.asStateFlow()

    init {
        if (isEdit) load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            repo.get(id).fold(
                onSuccess = { c ->
                    _state.update {
                        it.copy(
                            loading = false,
                            name = c.name,
                            phone = c.phone,
                            address = c.address.orEmpty(),
                            idNumber = c.idNumber.orEmpty(),
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, loadError = e.message) } },
            )
        }
    }

    fun update(transform: CustomerFormState.() -> CustomerFormState) =
        _state.update { it.transform().copy(errors = emptyMap(), error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val errors = buildMap {
            Validators.required(s.name, "Nama")?.let { put("name", it) }
            Validators.phone(s.phone)?.let { put("phone", it) }
            Validators.required(s.address, "Alamat")?.let { put("address", it) }
            Validators.required(s.idNumber, "Nomor identitas")?.let { put("idNumber", it) }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val request = CustomerRequest(s.name.trim(), s.phone.trim(), s.address.trim(), s.idNumber.trim())
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


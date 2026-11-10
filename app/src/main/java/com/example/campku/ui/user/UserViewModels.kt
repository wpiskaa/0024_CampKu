package com.example.campku.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campku.data.model.Role
import com.example.campku.data.model.User
import com.example.campku.data.model.UserRequest
import com.example.campku.data.repository.UserRepository
import com.example.campku.ui.components.ListViewModel
import com.example.campku.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** FR-08: daftar pengguna + cari nama atau email (khusus Admin). */
class UserListViewModel(private val repo: UserRepository) : ListViewModel<User, Unit>(Unit) {
    override suspend fun fetch(search: String, filter: Unit) = repo.list(search)
    override suspend fun deleteById(id: Int) = repo.delete(id)
}

data class UserFormState(
    val loading: Boolean = false,
    val loadError: String? = null,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = Role.PENYEWA,
    val password: String = "",
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class UserFormViewModel(private val repo: UserRepository, private val id: Int) : ViewModel() {
    val isEdit get() = id != 0

    private val _state = MutableStateFlow(UserFormState(loading = isEdit))
    val state: StateFlow<UserFormState> = _state.asStateFlow()

    init {
        if (isEdit) load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            repo.get(id).fold(
                onSuccess = { u ->
                    _state.update {
                        it.copy(loading = false, name = u.name, email = u.email, phone = u.phone.orEmpty(), role = u.role)
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, loadError = e.message) } },
            )
        }
    }

    fun update(transform: UserFormState.() -> UserFormState) =
        _state.update { it.transform().copy(errors = emptyMap(), error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val errors = buildMap {
            Validators.required(s.name, "Nama")?.let { put("name", it) }
            Validators.email(s.email)?.let { put("email", it) }
            if (s.phone.isNotBlank()) Validators.phone(s.phone)?.let { put("phone", it) }
            // Password wajib saat membuat akun; saat ubah hanya divalidasi bila diisi
            if (!isEdit || s.password.isNotEmpty()) Validators.password(s.password)?.let { put("password", it) }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val request = UserRequest(
            name = s.name.trim(),
            email = s.email.trim(),
            role = s.role,
            phone = s.phone.trim().ifEmpty { null },
            password = s.password.ifEmpty { null },
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


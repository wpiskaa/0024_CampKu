package com.example.campku.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campku.data.repository.AuthRepository
import com.example.campku.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Login. Bila berhasil, sesi tersimpan di DataStore dan navigasi berpindah otomatis. */
class LoginViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, emailError = null, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, passwordError = null, error = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val emailError = Validators.email(s.email)
        val passwordError = if (s.password.isEmpty()) "Password wajib diisi" else null
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.login(s.email, s.password).fold(
                onSuccess = { _state.update { it.copy(loading = false) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }
}

data class RegisterState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val errors: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

/** FR-01: Register. */
class RegisterViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    fun update(transform: RegisterState.() -> RegisterState) =
        _state.update { it.transform().copy(errors = emptyMap(), error = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val errors = buildMap {
            Validators.required(s.name, "Nama")?.let { put("name", it) }
            Validators.email(s.email)?.let { put("email", it) }
            Validators.phone(s.phone)?.let { put("phone", it) }
            Validators.password(s.password)?.let { put("password", it) }
            if (!containsKey("password") && s.password != s.confirmPassword) {
                put("confirm", "Konfirmasi password tidak sama")
            }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.register(s.name, s.email, s.phone, s.password).fold(
                onSuccess = { msg -> _state.update { it.copy(loading = false, successMessage = msg) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }
}


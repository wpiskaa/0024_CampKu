package com.example.campku.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Dasar ViewModel untuk layar daftar dengan fitur Search (+ satu filter opsional) dan hapus.
 * [T] = tipe item, [F] = tipe filter (mis. id kategori atau status sewa).
 */
@OptIn(FlowPreview::class)
abstract class ListViewModel<T, F>(initialFilter: F) : ViewModel() {

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _filter = MutableStateFlow(initialFilter)
    val filter: StateFlow<F> = _filter.asStateFlow()

    private val _items = MutableStateFlow<UiState<List<T>>>(UiState.Loading)
    val items: StateFlow<UiState<List<T>>> = _items.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    protected abstract suspend fun fetch(search: String, filter: F): Result<List<T>>
    protected abstract suspend fun deleteById(id: Int): Result<*>

    init {
        // Pencarian ditunda 300 ms setelah berhenti mengetik agar tidak membebani server.
        viewModelScope.launch {
            combine(_search.debounce(300), _filter) { s, f -> s to f }
                .collectLatest { (s, f) -> load(s, f, silent = false) }
        }
    }

    fun onSearchChange(value: String) {
        _search.value = value
    }

    fun onFilterChange(value: F) {
        _filter.value = value
    }

    fun retry() {
        viewModelScope.launch { load(_search.value, _filter.value, silent = false) }
    }

    /** Dipanggil saat layar kembali tampil (mis. setelah menyimpan form) agar data segar. */
    fun refresh() {
        if (_items.value !is UiState.Success) return
        viewModelScope.launch { load(_search.value, _filter.value, silent = true) }
    }

    fun delete(id: Int) {
        viewModelScope.launch {
            deleteById(id).fold(
                onSuccess = {
                    _message.value = "Data berhasil dihapus"
                    load(_search.value, _filter.value, silent = true)
                },
                onFailure = { _message.value = it.message },
            )
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private suspend fun load(search: String, filter: F, silent: Boolean) {
        if (!silent) _items.value = UiState.Loading
        val result = fetch(search, filter)
        if (silent && result.isFailure) return // pertahankan data lama bila refresh senyap gagal
        _items.value = result.toUiState()
    }
}


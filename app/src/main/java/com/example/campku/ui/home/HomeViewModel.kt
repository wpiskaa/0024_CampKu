package com.example.campku.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campku.data.model.Dashboard
import com.example.campku.data.model.Equipment
import com.example.campku.data.repository.DashboardRepository
import com.example.campku.data.repository.EquipmentRepository
import com.example.campku.data.repository.RentalRepository
import com.example.campku.data.model.RentalStatus
import com.example.campku.ui.components.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Data ringkasan Beranda. Admin memakai dashboard server; Penyewa dihitung dari sewanya sendiri. */
data class HomeData(
    val summary: Dashboard,
    val popular: List<Equipment>,
)

/** Beranda: ringkasan + alat populer (SRS bagian 7 no. 2) dan Dashboard laporan Admin (FR dashboard). */
class HomeViewModel(
    private val dashboardRepo: DashboardRepository,
    private val equipmentRepo: EquipmentRepository,
    private val rentalRepo: RentalRepository,
    private val isAdmin: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val state: StateFlow<UiState<HomeData>> = _state.asStateFlow()

    init {
        load(silent = false)
    }

    fun load(silent: Boolean = true) {
        viewModelScope.launch {
            if (!silent) _state.value = UiState.Loading
            val result = runCatching { fetch() }
            result.fold(
                onSuccess = { _state.value = UiState.Success(it) },
                onFailure = { e ->
                    if (!(silent && _state.value is UiState.Success)) {
                        _state.value = UiState.Error(e.message ?: "Terjadi kesalahan")
                    }
                },
            )
        }
    }

    private suspend fun fetch(): HomeData = coroutineScope {
        val popularDeferred = async { equipmentRepo.popular() }
        val summaryDeferred = async {
            if (isAdmin) {
                dashboardRepo.get()
            } else {
                rentalRepo.list(null, null).map { rentals ->
                    Dashboard(
                        pendingRentals = rentals.count { it.status == RentalStatus.DIPESAN },
                        activeRentals = rentals.count { it.status == RentalStatus.DISEWA },
                        lateRentals = rentals.count { it.isLate },
                        returnedRentals = rentals.count { it.status == RentalStatus.DIKEMBALIKAN },
                    )
                }
            }
        }
        val popular = popularDeferred.await().getOrThrow()
        val summary = summaryDeferred.await().getOrThrow()
        HomeData(summary, popular)
    }
}


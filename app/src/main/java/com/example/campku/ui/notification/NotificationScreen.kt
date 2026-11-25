package com.example.campku.ui.notification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.campku.appViewModel
import com.example.campku.data.model.AppNotification
import com.example.campku.data.repository.DashboardRepository
import com.example.campku.ui.components.EmptyState
import com.example.campku.ui.components.OnResume
import com.example.campku.ui.components.StateContent
import com.example.campku.ui.components.SubScreen
import com.example.campku.ui.components.UiState
import com.example.campku.ui.components.toUiState
import com.example.campku.ui.theme.StatusLate
import com.example.campku.ui.theme.StatusOrdered
import com.example.campku.ui.theme.StatusRented
import com.example.campku.ui.theme.StatusReturned
import com.example.campku.util.formatDisplayDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationViewModel(private val repo: DashboardRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<AppNotification>>>(UiState.Loading)
    val state: StateFlow<UiState<List<AppNotification>>> = _state.asStateFlow()

    init {
        load(silent = false)
    }

    fun load(silent: Boolean = true) {
        viewModelScope.launch {
            if (!silent) _state.value = UiState.Loading
            val res = repo.notifications()
            if (silent && res.isFailure && _state.value is UiState.Success) return@launch
            _state.value = res.toUiState()
        }
    }
}

/** Pusat Notifikasi untuk Admin & Penyewa terkait validasi pesanan, status sewa, keterlambatan, dan stok. */
@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onOpenRental: (Int) -> Unit,
    onOpenEquipment: (Int) -> Unit,
) {
    val vm = appViewModel { NotificationViewModel(it.dashboardRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    OnResume { vm.load() }

    SubScreen(title = "Notifikasi", onBack = onBack) { padding ->
        StateContent(state, onRetry = { vm.load(silent = false) }, modifier = Modifier.padding(padding)) { list ->
            if (list.isEmpty()) {
                EmptyState(
                    message = "Belum ada notifikasi baru.\nSemua informasi pesanan dan validasi akan tampil di sini.",
                    icon = Icons.Filled.NotificationsNone,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { item ->
                        NotificationCard(item) {
                            when {
                                item.rentalId != null -> onOpenRental(item.rentalId)
                                item.equipmentId != null -> onOpenEquipment(item.equipmentId)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: AppNotification, onClick: () -> Unit) {
    val (icon, color) = when (item.type) {
        "late" -> Icons.Filled.Warning to StatusLate
        "pending" -> Icons.Filled.Schedule to StatusOrdered
        "stock" -> Icons.Filled.Inventory2 to StatusLate
        "rented" -> Icons.Filled.ShoppingBag to StatusRented
        else -> Icons.Filled.CheckCircle to StatusReturned
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.urgent) color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.urgent) color.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (item.urgent) color else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.date.isNotBlank()) {
                    Text(
                        formatDisplayDate(item.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

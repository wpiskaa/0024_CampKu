package com.example.campku.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.campku.appViewModel
import com.example.campku.data.local.PhotoStorage
import com.example.campku.data.model.Dashboard
import com.example.campku.data.model.User
import com.example.campku.ui.components.LoadingButton
import com.example.campku.ui.components.OnResume
import com.example.campku.ui.components.PhotoImage
import com.example.campku.ui.components.SectionTitle
import com.example.campku.ui.components.StateContent
import com.example.campku.ui.navigation.MainScreen
import com.example.campku.ui.navigation.Routes
import com.example.campku.ui.theme.StatusLate
import com.example.campku.ui.theme.StatusOrdered
import com.example.campku.ui.theme.StatusRented
import com.example.campku.ui.theme.StatusReturned
import com.example.campku.util.formatRupiah

@Composable
fun HomeScreen(navController: NavHostController, user: User) {
    val isAdmin = user.isAdmin
    val vm = appViewModel(key = "home_${user.id}") {
        HomeViewModel(it.dashboardRepository, it.equipmentRepository, it.rentalRepository, isAdmin)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    OnResume { vm.load() }

    MainScreen(navController = navController, isAdmin = isAdmin, title = "CampKu") { padding ->
        StateContent(state, onRetry = { vm.load(silent = false) }, modifier = Modifier.padding(padding)) { data ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Greeting(user)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(if (isAdmin) "Ringkasan Dashboard" else "Ringkasan Sewa Saya")
                    SummaryGrid(data.summary, isAdmin)
                    Spacer(Modifier.height(4.dp))
                    LoadingButton(
                        text = "Buat Penyewaan Baru",
                        onClick = { navController.navigate(Routes.rentalForm()) },
                        loading = false,
                    )
                    Spacer(Modifier.height(8.dp))
                    SectionTitle("Alat Populer")
                }
                if (data.popular.isEmpty()) {
                    Text(
                        "Belum ada alat.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(data.popular, key = { it.id }) { e ->
                            Card(
                                onClick = { navController.navigate(Routes.equipmentDetail(e.id)) },
                                modifier = Modifier.width(160.dp),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(2.dp),
                            ) {
                                PhotoImage(
                                    kind = PhotoStorage.KIND_EQUIPMENT,
                                    id = e.id,
                                    serverPath = e.photoPath,
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                )
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        e.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${formatRupiah(e.dailyPrice)}/hari",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        if (e.rentCount > 0) "Disewa ${e.rentCount}×" else "Belum pernah disewa",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Greeting(user: User) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 8.dp),
    ) {
        Column {
            Text(
                "Halo, ${user.name}!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                if (user.isAdmin) "Kelola alat dan penyewaan camping hari ini."
                else "Siap berpetualang? Pilih alat camping favoritmu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun SummaryGrid(summary: Dashboard, isAdmin: Boolean) {
    val items = if (isAdmin) {
        listOf(
            StatItem("Alat Tersedia", "${summary.availableEquipment}", "dari ${summary.totalEquipment} jenis", Icons.Filled.Inventory2, StatusReturned),
            StatItem("Sewa Aktif", "${summary.activeRentals}", "sedang disewa", Icons.Filled.ShoppingBag, StatusRented),
            StatItem("Terlambat", "${summary.lateRentals}", "lewat jatuh tempo", Icons.Filled.Warning, StatusLate),
            StatItem("Dipesan", "${summary.pendingRentals}", "menunggu diambil", Icons.Filled.Schedule, StatusOrdered),
        )
    } else {
        listOf(
            StatItem("Dipesan", "${summary.pendingRentals}", "menunggu diambil", Icons.Filled.Schedule, StatusOrdered),
            StatItem("Sedang Disewa", "${summary.activeRentals}", "aktif", Icons.Filled.ShoppingBag, StatusRented),
            StatItem("Terlambat", "${summary.lateRentals}", "segera kembalikan", Icons.Filled.Warning, StatusLate),
            StatItem("Selesai", "${summary.returnedRentals}", "dikembalikan", Icons.Filled.CheckCircle, StatusReturned),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { StatCard(it, Modifier.weight(1f)) }
            }
        }
    }
}

private data class StatItem(
    val title: String,
    val value: String,
    val caption: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
private fun StatCard(item: StatItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = item.color.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(item.icon, null, tint = item.color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(item.title, style = MaterialTheme.typography.labelLarge, color = item.color)
            }
            Text(
                item.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = item.color,
            )
            Text(item.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


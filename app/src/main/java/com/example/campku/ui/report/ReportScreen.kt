package com.example.campku.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.campku.appViewModel
import com.example.campku.data.model.MonthlyRevenue
import com.example.campku.data.model.RevenueReport
import com.example.campku.data.repository.DashboardRepository
import com.example.campku.ui.components.InfoRow
import com.example.campku.ui.components.OnResume
import com.example.campku.ui.components.SectionTitle
import com.example.campku.ui.components.StateContent
import com.example.campku.ui.components.SubScreen
import com.example.campku.ui.components.UiState
import com.example.campku.ui.components.sharePlainText
import com.example.campku.ui.components.toUiState
import com.example.campku.ui.theme.StatusLate
import com.example.campku.ui.theme.StatusOrdered
import com.example.campku.ui.theme.StatusRented
import com.example.campku.ui.theme.StatusReturned
import com.example.campku.util.formatRupiah
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReportViewModel(private val repo: DashboardRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<RevenueReport>>(UiState.Loading)
    val state: StateFlow<UiState<RevenueReport>> = _state.asStateFlow()

    init {
        load(silent = false)
    }

    fun load(silent: Boolean = true) {
        viewModelScope.launch {
            if (!silent) _state.value = UiState.Loading
            val res = repo.report()
            if (silent && res.isFailure && _state.value is UiState.Success) return@launch
            _state.value = res.toUiState()
        }
    }
}

private fun formatCompactRp(value: Int): String = when {
    value >= 1_000_000 -> String.format("%.1fjt", value / 1_000_000f).replace(".0jt", "jt")
    value >= 1_000 -> "${value / 1_000}rb"
    else -> value.toString()
}

private fun buildReportShareText(r: RevenueReport): String = buildString {
    appendLine("=== LAPORAN PENDAPATAN CAMPKU ===")
    appendLine("Total Pemasukan : ${formatRupiah(r.grandTotal)}")
    appendLine("- Omzet Sewa    : ${formatRupiah(r.totalRevenue)}")
    appendLine("- Total Denda   : ${formatRupiah(r.totalLateFees)}")
    appendLine("- Total Diskon  : -${formatRupiah(r.totalDiscounts)}")
    appendLine("Total Transaksi : ${r.totalTransactions}")
    appendLine("")
    appendLine("Pendapatan 6 Bulan Terakhir:")
    r.monthlyRevenue.forEach { m ->
        appendLine("• ${m.label}: ${formatRupiah(m.total)} (${m.count} transaksi)")
    }
    if (r.topEquipments.isNotEmpty()) {
        appendLine("")
        appendLine("Alat Paling Menguntungkan:")
        r.topEquipments.forEachIndexed { i, e ->
            appendLine("${i + 1}. ${e.name} — ${formatRupiah(e.totalRevenue)} (${e.totalUnits} unit)")
        }
    }
}

/** Laporan Pendapatan & Grafik Statistik Bisnis 6 Bulan Terakhir (Khusus Admin). */
@Composable
fun ReportScreen(onBack: () -> Unit) {
    val vm = appViewModel { ReportViewModel(it.dashboardRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentReport = (state as? UiState.Success)?.data

    OnResume { vm.load() }

    SubScreen(
        title = "Laporan & Statistik",
        onBack = onBack,
        actions = {
            if (currentReport != null) {
                IconButton(onClick = {
                    sharePlainText(context, "Laporan Pendapatan CampKu", buildReportShareText(currentReport))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Bagikan laporan")
                }
            }
        },
    ) { padding ->
        StateContent(state, onRetry = { vm.load(silent = false) }, modifier = Modifier.padding(padding)) { report ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 1. Kartu Hero Total Pemasukan
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TrendingUp, null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Total Pemasukan Keseluruhan",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            )
                        }
                        Text(
                            formatRupiah(report.grandTotal),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            "${report.totalTransactions} transaksi tercatat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                        HorizontalDivider(
                            Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Omzet Sewa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                Text(formatRupiah(report.totalRevenue), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Pemasukan Denda", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                Text(formatRupiah(report.totalLateFees), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }

                // 2. Rincian Status Keuangan
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniRevenueCard("Selesai", report.completedRevenue, StatusReturned, Modifier.weight(1f))
                    MiniRevenueCard("Aktif", report.activeRevenue, StatusRented, Modifier.weight(1f))
                    MiniRevenueCard("Dipesan", report.pendingRevenue, StatusOrdered, Modifier.weight(1f))
                }

                if (report.totalDiscounts > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            InfoRow("Total Diskon Diberikan", "-${formatRupiah(report.totalDiscounts)}", valueColor = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                // 3. Grafik Batang Pendapatan 6 Bulan Terakhir
                SectionTitle("Grafik Pendapatan (6 Bulan Terakhir)")
                MonthlyBarChartCard(report.monthlyRevenue)

                // 4. Top 5 Alat Paling Menguntungkan
                SectionTitle("Alat Paling Menguntungkan")
                if (report.topEquipments.isEmpty()) {
                    Text(
                        "Belum ada data transaksi penyewaan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val maxRev = report.topEquipments.maxOf { it.totalRevenue }.coerceAtLeast(1)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            report.topEquipments.forEachIndexed { idx, eq ->
                                val progress = (eq.totalRevenue.toFloat() / maxRev.toFloat()).coerceIn(0.05f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(26.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    "${idx + 1}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                eq.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "${eq.categoryName} • ${eq.rentCount}× disewa (${eq.totalUnits} unit)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            formatRupiah(eq.totalRevenue),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                }
                                if (idx < report.topEquipments.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MiniRevenueCard(label: String, amount: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Text(
                formatCompactRp(amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MonthlyBarChartCard(data: List<MonthlyRevenue>) {
    val maxVal = data.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BarChart, null, tint = primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Tren Omzet + Denda Bulanan",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(170.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                data.forEachIndexed { idx, item ->
                    val ratio = if (item.total <= 0) 0.04f else (item.total.toFloat() / maxVal.toFloat()).coerceIn(0.1f, 1f)
                    val isLatest = idx == data.lastIndex
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            if (item.total > 0) formatCompactRp(item.total) else "-",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLatest) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .fillMaxHeight(ratio)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .background(
                                        if (item.total <= 0) {
                                            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))
                                        } else if (isLatest) {
                                            Brush.verticalGradient(listOf(secondary, primary))
                                        } else {
                                            Brush.verticalGradient(listOf(primary.copy(alpha = 0.75f), primary))
                                        },
                                    ),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.label,
                            fontSize = 10.sp,
                            fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Medium,
                            color = if (isLatest) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

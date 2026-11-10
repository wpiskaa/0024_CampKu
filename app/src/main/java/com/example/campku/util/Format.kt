package com.example.campku.util

import com.example.campku.data.model.Rental
import com.example.campku.data.model.RentalStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val localeId = Locale.forLanguageTag("id-ID")
private const val DAY_MS = 24L * 60 * 60 * 1000

/** 150000 -> "Rp 150.000" */
fun formatRupiah(value: Int): String = "Rp " + NumberFormat.getIntegerInstance(localeId).format(value)

private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
    isLenient = false
}

/** Parse "yyyy-MM-dd" menjadi milidetik UTC; null jika tidak valid. */
fun parseIsoDate(value: String): Long? = runCatching { isoFormat().parse(value)?.time }.getOrNull()

/** Milidetik UTC -> "yyyy-MM-dd". */
fun toIsoDate(millisUtc: Long): String = isoFormat().format(Date(millisUtc))

/** Tanggal hari ini (zona waktu perangkat) dalam format "yyyy-MM-dd". */
fun todayIso(): String {
    val c = Calendar.getInstance()
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/** "2026-11-10" -> "10 Nov 2026" */
fun formatDisplayDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    val millis = parseIsoDate(iso) ?: return iso
    return SimpleDateFormat("d MMM yyyy", localeId).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
}

/** Menambah [days] hari pada tanggal "yyyy-MM-dd". */
fun addDaysIso(iso: String, days: Int): String =
    toIsoDate((parseIsoDate(iso) ?: 0L) + days * DAY_MS)

/** FR-09: jumlah hari sewa (minimal 1 hari), null jika tanggal belum valid / terbalik. */
fun rentalDays(rentIso: String, dueIso: String): Int? {
    val a = parseIsoDate(rentIso) ?: return null
    val b = parseIsoDate(dueIso) ?: return null
    if (b < a) return null
    return maxOf(1L, (b - a) / DAY_MS).toInt()
}

/** FR-09: total = hari x harga per hari x jumlah. Dipakai untuk pratinjau di form. */
fun estimateTotal(rentIso: String, dueIso: String, dailyPrice: Int, quantity: Int): Int? =
    rentalDays(rentIso, dueIso)?.let { it * dailyPrice * quantity }

/** Label status untuk ditampilkan. */
fun Rental.statusLabel(): String = when {
    isLate -> "Terlambat"
    status == RentalStatus.DIPESAN -> "Dipesan"
    status == RentalStatus.DISEWA -> "Disewa"
    status == RentalStatus.DIKEMBALIKAN -> "Dikembalikan"
    else -> status
}

fun statusFilterLabel(status: String): String = when (status) {
    RentalStatus.DIPESAN -> "Dipesan"
    RentalStatus.DISEWA -> "Disewa"
    RentalStatus.DIKEMBALIKAN -> "Dikembalikan"
    RentalStatus.TERLAMBAT -> "Terlambat"
    else -> "Semua"
}

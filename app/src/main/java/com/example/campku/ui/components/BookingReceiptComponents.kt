package com.example.campku.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.campku.R
import com.example.campku.data.model.Rental
import com.example.campku.ui.theme.StatusLate
import com.example.campku.util.formatDisplayDate
import com.example.campku.util.formatRupiah
import com.example.campku.util.statusLabel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Menghasilkan Bitmap QR Code standar yang dapat dipindai dari teks (mis. Kode Booking). */
fun generateQrBitmap(content: String, sizePx: Int = 384): Bitmap? = runCatching {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val offset = y * sizePx
        for (x in 0 until sizePx) {
            pixels[offset + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    }
}.getOrNull()

@Composable
fun QrCodeImage(content: String, size: Dp = 112.dp, modifier: Modifier = Modifier) {
    val bmp = remember(content) { generateQrBitmap(content) }
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "QR Code $content",
                modifier = Modifier.padding(6.dp),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
    }
}

/** Menyusun teks Nota / Struk Digital Sewa yang rapi untuk WhatsApp dan Share. */
fun buildReceiptText(primary: Rental, items: List<Rental>): String = buildString {
    val grandTotal = items.sumOf { it.totalPrice + it.lateFee }
    val totalDiscount = items.sumOf { it.discountAmount }
    val totalLate = items.sumOf { it.lateFee }
    appendLine("==============================")
    appendLine("     CAMPKU - NOTA SEWA")
    appendLine("  Sistem Rental Alat Camping")
    appendLine("==============================")
    appendLine("Kode Booking : ${primary.displayBookingCode}")
    appendLine("Penyewa      : ${primary.customerName}")
    primary.customerPhone?.takeIf { it.isNotBlank() && it != "-" }?.let {
        appendLine("No. HP       : $it")
    }
    appendLine("Tgl Sewa     : ${formatDisplayDate(primary.rentDate)}")
    appendLine("------------------------------")
    appendLine("RINCIAN BARANG:")
    items.forEachIndexed { idx, r ->
        appendLine("${idx + 1}. ${r.equipmentName}")
        appendLine("   ${r.quantity} unit x ${r.days} hari (${formatDisplayDate(r.rentDate)} - ${formatDisplayDate(r.dueDate)})")
        appendLine("   Status: ${r.statusLabel()} | Subtotal: ${formatRupiah(r.totalPrice)}")
        if (r.discountPercent > 0) {
            appendLine("   Diskon (${r.discountPercent}%): -${formatRupiah(r.discountAmount)}")
        }
        if (r.bonusDays > 0) {
            appendLine("   Bonus Promo: +${r.bonusDays} hari gratis")
        }
        if (r.lateFee > 0) {
            appendLine("   Denda (${r.lateDays} hari): ${formatRupiah(r.lateFee)}")
        }
    }
    appendLine("------------------------------")
    if (totalDiscount > 0) appendLine("Total Diskon : -${formatRupiah(totalDiscount)}")
    if (totalLate > 0) appendLine("Total Denda  : ${formatRupiah(totalLate)}")
    appendLine("TOTAL BAYAR  : ${formatRupiah(grandTotal)}")
    appendLine("==============================")
    appendLine("Tunjukkan Kode Booking ini saat pengambilan/pengembalian alat di basecamp CampKu.")
    appendLine("Terima kasih & selamat berpetualang!")
}

/** Membuka WhatsApp langsung ke nomor pelanggan dengan pesan nota yang sudah terisi. */
fun openWhatsAppReceipt(context: Context, rawPhone: String?, message: String) {
    val digits = rawPhone.orEmpty().filter { it.isDigit() }
    val waPhone = when {
        digits.startsWith("0") -> "62" + digits.drop(1)
        digits.startsWith("62") -> digits
        digits.length >= 8 -> "62$digits"
        else -> ""
    }
    val encoded = Uri.encode(message)
    val url = if (waPhone.isNotEmpty()) "https://wa.me/$waPhone?text=$encoded" else "https://wa.me/?text=$encoded"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }.onFailure {
        sharePlainText(context, "Nota Sewa CampKu", message)
    }
}

/** Membagikan teks nota/laporan melalui Android Share Sheet. */
fun sharePlainText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, subject))
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label disalin: $text", Toast.LENGTH_SHORT).show()
}

/** Kartu Tiket Booking Pass (QR Code untuk Penyewa; Arsip Kode & Nota untuk Admin setelah verifikasi). */
@Composable
fun BookingPassCard(
    primary: Rental,
    items: List<Rental>,
    onOpenReceipt: () -> Unit,
    showQrCode: Boolean = true,
) {
    val context = LocalContext.current
    val code = primary.displayBookingCode

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showQrCode) {
                    QrCodeImage(content = code, size = 96.dp)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (showQrCode) "KARTU TIKET / BOOKING PASS" else "KODE BOOKING TERVERIFIKASI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            code,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { copyToClipboard(context, "Kode Booking", code) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Salin kode",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        if (showQrCode) {
                            "Tunjukkan QR / kode ini saat pengambilan & pengembalian alat."
                        } else {
                            "Kode booking telah terverifikasi. Nota digital siap dilihat atau dikirim ke penyewa."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenReceipt,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nota Digital", maxLines = 1)
                }
                Button(
                    onClick = {
                        val receipt = buildReceiptText(primary, items)
                        openWhatsAppReceipt(context, primary.customerPhone, receipt)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F8E49),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kirim WA", maxLines = 1)
                }
            }
        }
    }
}

/** Dialog Pratinjau Nota Digital Sewa + Tombol Kirim WhatsApp & Bagikan. */
@Composable
fun DigitalReceiptDialog(
    primary: Rental,
    items: List<Rental>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val receiptText = remember(primary, items) { buildReceiptText(primary, items) }
    val grandTotal = items.sumOf { it.totalPrice + it.lateFee }
    val totalDiscount = items.sumOf { it.discountAmount }
    val totalLate = items.sumOf { it.lateFee }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_campku_logo),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Nota Digital CampKu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        primary.displayBookingCode,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoRow("Penyewa", primary.customerName)
                InfoRow("No. HP", primary.customerPhone?.takeIf { it.isNotBlank() } ?: "-")
                InfoRow("Tgl Sewa", formatDisplayDate(primary.rentDate))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                items.forEachIndexed { index, r ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("${index + 1}. ${r.equipmentName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${r.quantity} unit × ${r.days} hari (${formatDisplayDate(r.rentDate)} - ${formatDisplayDate(r.dueDate)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Status: ${r.statusLabel()}", style = MaterialTheme.typography.labelSmall)
                            Text(
                                formatRupiah(r.totalPrice + r.lateFee),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                if (totalDiscount > 0) {
                    InfoRow("Total Diskon", "-${formatRupiah(totalDiscount)}", valueColor = MaterialTheme.colorScheme.secondary)
                }
                if (totalLate > 0) {
                    InfoRow("Total Denda", formatRupiah(totalLate), valueColor = StatusLate)
                }
                InfoRow("TOTAL BAYAR", formatRupiah(grandTotal), valueColor = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { sharePlainText(context, "Nota Sewa ${primary.displayBookingCode}", receiptText) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Bagikan", maxLines = 1)
                    }
                    Button(
                        onClick = { openWhatsAppReceipt(context, primary.customerPhone, receiptText) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F8E49), contentColor = Color.White),
                    ) {
                        Text("WhatsApp", maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
    )
}

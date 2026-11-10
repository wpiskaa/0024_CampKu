package com.example.campku.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayInputStream
import java.io.File

/**
 * FR-14: foto dari kamera/galeri disimpan di penyimpanan internal aplikasi
 * (context.filesDir/photos) dan dibaca kembali dengan File API.
 */
class PhotoStorage(private val context: Context) {

    private val photoDir: File get() = File(context.filesDir, "photos").apply { mkdirs() }
    private val cameraDir: File get() = File(context.cacheDir, "camera").apply { mkdirs() }

    /** Membuat URI sementara (FileProvider) tujuan hasil jepretan kamera. */
    fun newCameraUri(): Uri {
        val file = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Menyalin gambar [source] (kamera/galeri) ke filesDir sebagai JPEG yang sudah
     * diperkecil (maks 1280 px) dan diputar sesuai EXIF.
     * @param kind  "equipment" atau "return"
     */
    fun saveToInternal(kind: String, id: Int, source: Uri): File {
        val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
            ?: throw IllegalStateException("Tidak dapat membaca foto yang dipilih")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("File yang dipilih bukan gambar yang valid")
        }

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw IllegalStateException("Gagal membaca gambar")

        val rotated = rotateByExif(decoded, bytes)
        val target = localFile(kind, id)
        target.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return target
    }

    /** File foto lokal jika ada (null jika belum pernah disimpan di perangkat ini). */
    fun localFileOrNull(kind: String, id: Int): File? = localFile(kind, id).takeIf { it.exists() && it.length() > 0 }

    fun deleteLocal(kind: String, id: Int) {
        localFile(kind, id).delete()
    }

    private fun localFile(kind: String, id: Int) = File(photoDir, "${kind}_$id.jpg")

    private fun rotateByExif(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        const val KIND_EQUIPMENT = "equipment"
        const val KIND_PICKUP = "pickup"
        const val KIND_RETURN = "return"
        private const val MAX_SIDE = 1280
    }
}


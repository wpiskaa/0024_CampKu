package com.example.campku.util

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val PHONE_REGEX = Regex("^[0-9+\\-\\s]{8,25}$")

/** Aturan validasi form (FR-01, FR-13). Mengembalikan pesan error, atau null jika valid. */
object Validators {

    fun required(value: String, label: String): String? =
        if (value.isBlank()) "$label wajib diisi" else null

    fun email(value: String): String? = when {
        value.isBlank() -> "Email wajib diisi"
        !EMAIL_REGEX.matches(value.trim()) -> "Format email tidak valid"
        else -> null
    }

    fun phone(value: String): String? = when {
        value.isBlank() -> "Nomor HP wajib diisi"
        !PHONE_REGEX.matches(value.trim()) -> "Nomor HP tidak valid (8-25 digit)"
        else -> null
    }

    /** FR-01: password minimal 6 karakter. */
    fun password(value: String): String? = when {
        value.isEmpty() -> "Password wajib diisi"
        value.length < 6 -> "Password minimal 6 karakter"
        else -> null
    }

    /** Bilangan bulat tidak negatif (harga, stok). */
    fun nonNegativeInt(value: String, label: String): String? {
        if (value.isBlank()) return "$label wajib diisi"
        val n = value.trim().toIntOrNull() ?: return "$label harus berupa angka bulat"
        return if (n < 0) "$label tidak boleh negatif" else null
    }

    fun positiveInt(value: String, label: String): String? {
        if (value.isBlank()) return "$label wajib diisi"
        val n = value.trim().toIntOrNull() ?: return "$label harus berupa angka bulat"
        return if (n < 1) "$label minimal 1" else null
    }
}


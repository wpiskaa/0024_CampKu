package com.example.campku.data.model

import com.google.gson.annotations.SerializedName

/** Peran pengguna (SRS bagian 2). */
object Role {
    const val ADMIN = "admin"
    const val PENYEWA = "penyewa"
}

/** Status penyewaan (FR-12). "Terlambat" bukan status tersimpan, melainkan penanda otomatis. */
object RentalStatus {
    const val DIPESAN = "dipesan"
    const val DISEWA = "disewa"
    const val DIKEMBALIKAN = "dikembalikan"
    const val TERLAMBAT = "terlambat" // hanya untuk filter
}

// ---------- Autentikasi ----------

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    val phone: String? = null,
) {
    val isAdmin: Boolean get() = role == Role.ADMIN
}

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
)

data class RefreshRequest(@SerializedName("refresh_token") val refreshToken: String)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    val user: User,
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
)

data class MessageResponse(val message: String? = null)

// ---------- Kategori ----------

data class Category(
    val id: Int,
    val name: String,
    val description: String? = null,
    @SerializedName("equipment_count") val equipmentCount: Int = 0,
)

data class CategoryRequest(val name: String, val description: String?)

// ---------- Alat ----------

data class Equipment(
    val id: Int,
    @SerializedName("category_id") val categoryId: Int,
    @SerializedName("category_name") val categoryName: String?,
    val name: String,
    @SerializedName("daily_price") val dailyPrice: Int,
    val stock: Int,
    val description: String?,
    @SerializedName("photo_path") val photoPath: String?,
    @SerializedName("rent_count") val rentCount: Int = 0,
)

data class EquipmentRequest(
    @SerializedName("category_id") val categoryId: Int,
    val name: String,
    @SerializedName("daily_price") val dailyPrice: Int,
    val stock: Int,
    val description: String?,
)

// ---------- Pelanggan ----------

data class Customer(
    val id: Int,
    @SerializedName("user_id") val userId: Int?,
    val name: String,
    val phone: String,
    val address: String?,
    @SerializedName("id_number") val idNumber: String?,
)

data class CustomerRequest(
    val name: String,
    val phone: String,
    val address: String?,
    @SerializedName("id_number") val idNumber: String?,
)

// ---------- Penyewaan ----------

data class Rental(
    val id: Int,
    @SerializedName("customer_id") val customerId: Int,
    @SerializedName("customer_name") val customerName: String,
    @SerializedName("customer_phone") val customerPhone: String?,
    @SerializedName("equipment_id") val equipmentId: Int,
    @SerializedName("equipment_name") val equipmentName: String,
    @SerializedName("daily_price") val dailyPrice: Int,
    @SerializedName("equipment_photo") val equipmentPhoto: String?,
    @SerializedName("processed_by_name") val processedByName: String?,
    val quantity: Int,
    @SerializedName("rent_date") val rentDate: String,
    @SerializedName("due_date") val dueDate: String,
    @SerializedName("return_date") val returnDate: String?,
    @SerializedName("total_price") val totalPrice: Int,
    @SerializedName("late_fee") val lateFee: Int,
    val status: String,
    @SerializedName("return_photo_path") val returnPhotoPath: String?,
    val days: Int = 1,
    @SerializedName("is_late") val isLate: Boolean = false,
    @SerializedName("late_days") val lateDays: Int = 0,
)

data class RentalRequest(
    @SerializedName("customer_id") val customerId: Int?,
    @SerializedName("equipment_id") val equipmentId: Int,
    val quantity: Int,
    @SerializedName("rent_date") val rentDate: String,
    @SerializedName("due_date") val dueDate: String,
)

data class StatusRequest(val status: String)

// ---------- Pengguna ----------

data class UserRequest(
    val name: String,
    val email: String,
    val role: String,
    val phone: String?,
    val password: String?,
)

// ---------- Dashboard ----------

data class Dashboard(
    @SerializedName("total_equipment") val totalEquipment: Int = 0,
    @SerializedName("available_equipment") val availableEquipment: Int = 0,
    @SerializedName("available_stock") val availableStock: Int = 0,
    @SerializedName("pending_rentals") val pendingRentals: Int = 0,
    @SerializedName("active_rentals") val activeRentals: Int = 0,
    @SerializedName("late_rentals") val lateRentals: Int = 0,
    @SerializedName("returned_rentals") val returnedRentals: Int = 0,
    @SerializedName("total_customers") val totalCustomers: Int = 0,
)


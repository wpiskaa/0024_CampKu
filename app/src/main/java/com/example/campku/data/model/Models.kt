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
    val address: String? = null,
    @SerializedName("id_number") val idNumber: String? = null,
) {
    val isAdmin: Boolean get() = role == Role.ADMIN
}

data class ProfileUpdateRequest(
    val name: String,
    val email: String,
    val phone: String?,
    val address: String?,
    @SerializedName("id_number") val idNumber: String?,
    val password: String? = null,
)

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
    @SerializedName("pickup_photo_path") val pickupPhotoPath: String? = null,
    @SerializedName("return_photo_path") val returnPhotoPath: String?,
    val days: Int = 1,
    @SerializedName("bonus_days") val bonusDays: Int = 0,
    @SerializedName("discount_percent") val discountPercent: Int = 0,
    @SerializedName("discount_amount") val discountAmount: Int = 0,
    @SerializedName("is_late") val isLate: Boolean = false,
    @SerializedName("late_days") val lateDays: Int = 0,
    @SerializedName("booking_code") val bookingCode: String? = null,
    @SerializedName("booking_verified") val bookingVerified: Boolean = false,
) {
    val displayBookingCode: String
        get() = bookingCode.orEmpty().ifBlank { "CK-${rentDate.replace("-", "")}-${id.toString().padStart(4, '0')}" }
}

data class RentalRequest(
    @SerializedName("customer_id") val customerId: Int?,
    @SerializedName("equipment_id") val equipmentId: Int,
    val quantity: Int,
    @SerializedName("rent_date") val rentDate: String,
    @SerializedName("due_date") val dueDate: String,
    @SerializedName("bonus_day") val bonusDay: Boolean = false,
    @SerializedName("discount_percent") val discountPercent: Int = 0,
)

data class StatusRequest(val status: String)

data class DiscountRequest(@SerializedName("discount_percent") val discountPercent: Int)

data class VerifyBookingRequest(@SerializedName("booking_code") val bookingCode: String)

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

data class MonthlyRevenue(
    val month: String = "",
    val label: String = "",
    val revenue: Int = 0,
    @SerializedName("late_fee") val lateFee: Int = 0,
    val total: Int = 0,
    val count: Int = 0,
)

data class TopEquipmentReport(
    val id: Int = 0,
    val name: String = "",
    @SerializedName("category_name") val categoryName: String = "",
    @SerializedName("rent_count") val rentCount: Int = 0,
    @SerializedName("total_units") val totalUnits: Int = 0,
    @SerializedName("total_revenue") val totalRevenue: Int = 0,
)

data class RevenueReport(
    @SerializedName("total_revenue") val totalRevenue: Int = 0,
    @SerializedName("total_late_fees") val totalLateFees: Int = 0,
    @SerializedName("grand_total") val grandTotal: Int = 0,
    @SerializedName("completed_revenue") val completedRevenue: Int = 0,
    @SerializedName("active_revenue") val activeRevenue: Int = 0,
    @SerializedName("pending_revenue") val pendingRevenue: Int = 0,
    @SerializedName("total_discounts") val totalDiscounts: Int = 0,
    @SerializedName("total_transactions") val totalTransactions: Int = 0,
    @SerializedName("monthly_revenue") val monthlyRevenue: List<MonthlyRevenue> = emptyList(),
    @SerializedName("top_equipments") val topEquipments: List<TopEquipmentReport> = emptyList(),
)

data class AppNotification(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val message: String = "",
    val date: String = "",
    @SerializedName("rental_id") val rentalId: Int? = null,
    @SerializedName("equipment_id") val equipmentId: Int? = null,
    val urgent: Boolean = false,
)


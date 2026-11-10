package com.example.campku.data.repository

import com.example.campku.data.local.PhotoStorage
import com.example.campku.data.local.TokenStore
import com.example.campku.data.model.Category
import com.example.campku.data.model.CategoryRequest
import com.example.campku.data.model.Customer
import com.example.campku.data.model.CustomerRequest
import com.example.campku.data.model.Dashboard
import com.example.campku.data.model.Equipment
import com.example.campku.data.model.EquipmentRequest
import com.example.campku.data.model.LoginRequest
import com.example.campku.data.model.RegisterRequest
import com.example.campku.data.model.Rental
import com.example.campku.data.model.RentalRequest
import com.example.campku.data.model.StatusRequest
import com.example.campku.data.model.User
import com.example.campku.data.model.UserRequest
import com.example.campku.data.remote.ApiService
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException

/** Mengubah exception jaringan/HTTP menjadi pesan yang mudah dipahami pengguna. */
fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val serverMessage = runCatching { JsonParser.parseString(body).asJsonObject.get("message").asString }.getOrNull()
        serverMessage ?: when (code()) {
            401 -> "Sesi berakhir, silakan login kembali"
            403 -> "Anda tidak memiliki akses"
            404 -> "Data tidak ditemukan"
            else -> "Terjadi kesalahan server (${code()})"
        }
    }
    is IOException -> "Tidak dapat terhubung ke server. Periksa koneksi dan alamat server."
    else -> message ?: "Terjadi kesalahan"
}

/** Menjalankan pemanggilan API dan membungkus hasil/galatnya sebagai [Result]. */
suspend inline fun <T> apiCall(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(IllegalStateException(e.toUserMessage(), e))
    }

private fun photoPart(file: File): MultipartBody.Part =
    MultipartBody.Part.createFormData("photo", file.name, file.asRequestBody("image/jpeg".toMediaType()))

class AuthRepository(private val api: ApiService, private val tokenStore: TokenStore) {
    val sessionFlow = tokenStore.sessionFlow

    suspend fun login(email: String, password: String): Result<User> = apiCall {
        val res = api.login(LoginRequest(email.trim(), password))
        tokenStore.saveSession(res.accessToken, res.refreshToken, res.user)
        res.user
    }

    suspend fun register(name: String, email: String, phone: String, password: String): Result<String> = apiCall {
        api.register(RegisterRequest(name.trim(), email.trim(), phone.trim(), password)).message
            ?: "Registrasi berhasil"
    }

    suspend fun me(): Result<User> = apiCall {
        val user = api.me()
        tokenStore.updateUser(user)
        user
    }

    /** Logout selalu menghapus sesi lokal, walau server tidak terjangkau. */
    suspend fun logout() {
        runCatching { api.logout() }
        tokenStore.clear()
    }
}

class EquipmentRepository(private val api: ApiService, private val photos: PhotoStorage) {
    suspend fun list(search: String?, categoryId: Int?) =
        apiCall { api.getEquipments(search?.trim()?.ifEmpty { null }, categoryId) }

    suspend fun popular() = apiCall { api.getPopularEquipments() }
    suspend fun get(id: Int) = apiCall { api.getEquipment(id) }
    suspend fun create(r: EquipmentRequest) = apiCall { api.createEquipment(r) }
    suspend fun update(id: Int, r: EquipmentRequest) = apiCall { api.updateEquipment(id, r) }

    suspend fun delete(id: Int) = apiCall {
        api.deleteEquipment(id)
        photos.deleteLocal(PhotoStorage.KIND_EQUIPMENT, id)
    }

    /**
     * FR-14: simpan foto ke filesDir terlebih dahulu (selalu berhasil secara lokal),
     * lalu unggah salinannya ke server sebagai tambahan.
     * @return pasangan (alat terbaru atau null jika unggah gagal, pesan peringatan atau null)
     */
    suspend fun savePhoto(id: Int, source: android.net.Uri): Pair<Equipment?, String?> =
        withContext(Dispatchers.IO) {
            val file = try {
                photos.saveToInternal(PhotoStorage.KIND_EQUIPMENT, id, source)
            } catch (e: Exception) {
                return@withContext null to (e.message ?: "Gagal menyimpan foto")
            }
            val upload = apiCall { api.uploadEquipmentPhoto(id, photoPart(file)) }
            upload.getOrNull() to upload.exceptionOrNull()?.message?.let { "Foto tersimpan di perangkat, tetapi gagal diunggah: $it" }
        }
}

class CategoryRepository(private val api: ApiService) {
    suspend fun list(search: String? = null) = apiCall { api.getCategories(search?.trim()?.ifEmpty { null }) }
    suspend fun get(id: Int) = apiCall { api.getCategory(id) }
    suspend fun create(r: CategoryRequest) = apiCall { api.createCategory(r) }
    suspend fun update(id: Int, r: CategoryRequest) = apiCall { api.updateCategory(id, r) }
    suspend fun delete(id: Int) = apiCall { api.deleteCategory(id) }
}

class CustomerRepository(private val api: ApiService) {
    suspend fun list(search: String? = null) = apiCall { api.getCustomers(search?.trim()?.ifEmpty { null }) }
    suspend fun get(id: Int) = apiCall { api.getCustomer(id) }
    suspend fun create(r: CustomerRequest) = apiCall { api.createCustomer(r) }
    suspend fun update(id: Int, r: CustomerRequest) = apiCall { api.updateCustomer(id, r) }
    suspend fun delete(id: Int) = apiCall { api.deleteCustomer(id) }
}

class RentalRepository(private val api: ApiService, private val photos: PhotoStorage) {
    suspend fun list(search: String?, status: String?) =
        apiCall { api.getRentals(search?.trim()?.ifEmpty { null }, status?.ifEmpty { null }) }

    suspend fun get(id: Int) = apiCall { api.getRental(id) }
    suspend fun create(r: RentalRequest) = apiCall { api.createRental(r) }
    suspend fun update(id: Int, r: RentalRequest) = apiCall { api.updateRental(id, r) }
    suspend fun changeStatus(id: Int, status: String) = apiCall { api.updateRentalStatus(id, StatusRequest(status)) }

    suspend fun delete(id: Int) = apiCall {
        api.deleteRental(id)
        photos.deleteLocal(PhotoStorage.KIND_RETURN, id)
    }

    /** Sama seperti foto alat: simpan lokal dulu, lalu unggah ke server. */
    suspend fun saveReturnPhoto(id: Int, source: android.net.Uri): Pair<Rental?, String?> =
        withContext(Dispatchers.IO) {
            val file = try {
                photos.saveToInternal(PhotoStorage.KIND_RETURN, id, source)
            } catch (e: Exception) {
                return@withContext null to (e.message ?: "Gagal menyimpan foto")
            }
            val upload = apiCall { api.uploadReturnPhoto(id, photoPart(file)) }
            upload.getOrNull() to upload.exceptionOrNull()?.message?.let { "Foto tersimpan di perangkat, tetapi gagal diunggah: $it" }
        }
}

class UserRepository(private val api: ApiService) {
    suspend fun list(search: String? = null) = apiCall { api.getUsers(search?.trim()?.ifEmpty { null }) }
    suspend fun get(id: Int) = apiCall { api.getUser(id) }
    suspend fun create(r: UserRequest) = apiCall { api.createUser(r) }
    suspend fun update(id: Int, r: UserRequest) = apiCall { api.updateUser(id, r) }
    suspend fun delete(id: Int) = apiCall { api.deleteUser(id) }
}

class DashboardRepository(private val api: ApiService) {
    suspend fun get(): Result<Dashboard> = apiCall { api.getDashboard() }
}


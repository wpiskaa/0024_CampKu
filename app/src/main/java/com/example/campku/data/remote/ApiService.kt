package com.example.campku.data.remote

import com.example.campku.data.model.Category
import com.example.campku.data.model.CategoryRequest
import com.example.campku.data.model.Customer
import com.example.campku.data.model.CustomerRequest
import com.example.campku.data.model.Dashboard
import com.example.campku.data.model.Equipment
import com.example.campku.data.model.EquipmentRequest
import com.example.campku.data.model.LoginRequest
import com.example.campku.data.model.LoginResponse
import com.example.campku.data.model.MessageResponse
import com.example.campku.data.model.RegisterRequest
import com.example.campku.data.model.Rental
import com.example.campku.data.model.RentalRequest
import com.example.campku.data.model.StatusRequest
import com.example.campku.data.model.User
import com.example.campku.data.model.UserRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Seluruh endpoint REST API CampKu (SRS bagian 6). */
interface ApiService {

    // ----- Auth -----
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): MessageResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/auth/logout")
    suspend fun logout(): MessageResponse

    @GET("api/auth/me")
    suspend fun me(): User

    // ----- Alat -----
    @GET("api/equipments")
    suspend fun getEquipments(
        @Query("search") search: String? = null,
        @Query("category_id") categoryId: Int? = null,
    ): List<Equipment>

    @GET("api/equipments/popular")
    suspend fun getPopularEquipments(): List<Equipment>

    @GET("api/equipments/{id}")
    suspend fun getEquipment(@Path("id") id: Int): Equipment

    @POST("api/equipments")
    suspend fun createEquipment(@Body body: EquipmentRequest): Equipment

    @PUT("api/equipments/{id}")
    suspend fun updateEquipment(@Path("id") id: Int, @Body body: EquipmentRequest): Equipment

    @DELETE("api/equipments/{id}")
    suspend fun deleteEquipment(@Path("id") id: Int): MessageResponse

    @Multipart
    @POST("api/equipments/{id}/photo")
    suspend fun uploadEquipmentPhoto(@Path("id") id: Int, @Part photo: MultipartBody.Part): Equipment

    // ----- Kategori -----
    @GET("api/categories")
    suspend fun getCategories(@Query("search") search: String? = null): List<Category>

    @GET("api/categories/{id}")
    suspend fun getCategory(@Path("id") id: Int): Category

    @POST("api/categories")
    suspend fun createCategory(@Body body: CategoryRequest): Category

    @PUT("api/categories/{id}")
    suspend fun updateCategory(@Path("id") id: Int, @Body body: CategoryRequest): Category

    @DELETE("api/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: Int): MessageResponse

    // ----- Pelanggan -----
    @GET("api/customers")
    suspend fun getCustomers(@Query("search") search: String? = null): List<Customer>

    @GET("api/customers/{id}")
    suspend fun getCustomer(@Path("id") id: Int): Customer

    @POST("api/customers")
    suspend fun createCustomer(@Body body: CustomerRequest): Customer

    @PUT("api/customers/{id}")
    suspend fun updateCustomer(@Path("id") id: Int, @Body body: CustomerRequest): Customer

    @DELETE("api/customers/{id}")
    suspend fun deleteCustomer(@Path("id") id: Int): MessageResponse

    // ----- Penyewaan -----
    @GET("api/rentals")
    suspend fun getRentals(
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
    ): List<Rental>

    @GET("api/rentals/{id}")
    suspend fun getRental(@Path("id") id: Int): Rental

    @POST("api/rentals")
    suspend fun createRental(@Body body: RentalRequest): Rental

    @PUT("api/rentals/{id}")
    suspend fun updateRental(@Path("id") id: Int, @Body body: RentalRequest): Rental

    @PATCH("api/rentals/{id}/status")
    suspend fun updateRentalStatus(@Path("id") id: Int, @Body body: StatusRequest): Rental

    @Multipart
    @POST("api/rentals/{id}/return-photo")
    suspend fun uploadReturnPhoto(@Path("id") id: Int, @Part photo: MultipartBody.Part): Rental

    @DELETE("api/rentals/{id}")
    suspend fun deleteRental(@Path("id") id: Int): MessageResponse

    // ----- Pengguna -----
    @GET("api/users")
    suspend fun getUsers(@Query("search") search: String? = null): List<User>

    @GET("api/users/{id}")
    suspend fun getUser(@Path("id") id: Int): User

    @POST("api/users")
    suspend fun createUser(@Body body: UserRequest): User

    @PUT("api/users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body body: UserRequest): User

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): MessageResponse

    // ----- Dashboard -----
    @GET("api/dashboard")
    suspend fun getDashboard(): Dashboard
}


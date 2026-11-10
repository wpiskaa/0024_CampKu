package com.example.campku

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.campku.data.local.PhotoStorage
import com.example.campku.data.local.TokenStore
import com.example.campku.data.remote.NetworkModule
import com.example.campku.data.repository.AuthRepository
import com.example.campku.data.repository.CategoryRepository
import com.example.campku.data.repository.CustomerRepository
import com.example.campku.data.repository.DashboardRepository
import com.example.campku.data.repository.EquipmentRepository
import com.example.campku.data.repository.RentalRepository
import com.example.campku.data.repository.UserRepository

/** Wadah dependensi sederhana (tanpa library DI). */
class AppContainer(application: Application) {
    val tokenStore = TokenStore(application)
    val photoStorage = PhotoStorage(application)
    private val api = NetworkModule.createApi(tokenStore)

    val authRepository = AuthRepository(api, tokenStore)
    val equipmentRepository = EquipmentRepository(api, photoStorage)
    val categoryRepository = CategoryRepository(api)
    val customerRepository = CustomerRepository(api)
    val rentalRepository = RentalRepository(api, photoStorage)
    val userRepository = UserRepository(api)
    val dashboardRepository = DashboardRepository(api)
}

class CampKuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Membuat ViewModel dengan akses ke [AppContainer]. [key] membedakan instance (mis. per id). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as CampKuApp).container
    val factory: ViewModelProvider.Factory = viewModelFactory { initializer { create(container) } }
    return viewModel(key = key, factory = factory)
}


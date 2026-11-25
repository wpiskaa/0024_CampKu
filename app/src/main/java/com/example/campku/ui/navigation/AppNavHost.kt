package com.example.campku.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.campku.data.model.User
import com.example.campku.ui.auth.LoginScreen
import com.example.campku.ui.auth.RegisterScreen
import com.example.campku.ui.category.CategoryFormScreen
import com.example.campku.ui.category.CategoryListScreen
import com.example.campku.ui.customer.CustomerFormScreen
import com.example.campku.ui.customer.CustomerListScreen
import com.example.campku.ui.equipment.EquipmentDetailScreen
import com.example.campku.ui.equipment.EquipmentFormScreen
import com.example.campku.ui.equipment.EquipmentListScreen
import com.example.campku.ui.home.HomeScreen
import com.example.campku.ui.notification.NotificationScreen
import com.example.campku.ui.planner.PackagePlannerScreen
import com.example.campku.ui.profile.AccountSettingsScreen
import com.example.campku.ui.profile.ProfileScreen
import com.example.campku.ui.rental.RentalDetailScreen
import com.example.campku.ui.rental.RentalFormScreen
import com.example.campku.ui.rental.RentalListScreen
import com.example.campku.ui.report.ReportScreen
import com.example.campku.ui.user.UserFormScreen
import com.example.campku.ui.user.UserListScreen

/** Keadaan sesi login yang menentukan layar awal. */
sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: User) : SessionState
}

private val idArg = listOf(navArgument("id") { type = NavType.IntType })

private fun NavBackStackEntry.intArg(name: String): Int = arguments?.getInt(name) ?: 0

/**
 * Seluruh navigasi aplikasi (Navigation Compose).
 * startDestination ditentukan oleh peran: Admin -> home_admin, Penyewa -> home_user, belum login -> login.
 * Perubahan sesi (login / logout / sesi berakhir) otomatis memindahkan layar.
 */
@Composable
fun AppNavHost(sessionState: SessionState) {
    if (sessionState is SessionState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val navController = rememberNavController()
    val user = (sessionState as? SessionState.LoggedIn)?.user
    val startDestination = remember { user?.let { Routes.home(it.isAdmin) } ?: Routes.LOGIN }

    // Sinkronkan layar dengan status sesi.
    LaunchedEffect(user?.id) {
        val route = navController.currentDestination?.route ?: return@LaunchedEffect
        val onAuthScreen = route == Routes.LOGIN || route == Routes.REGISTER
        if (user == null && !onAuthScreen) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        } else if (user != null && onAuthScreen) {
            navController.navigate(Routes.home(user.isAdmin)) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(180)) },
        exitTransition = { fadeOut(animationSpec = tween(140)) },
        popEnterTransition = { fadeIn(animationSpec = tween(180)) },
        popExitTransition = { fadeOut(animationSpec = tween(140)) },
    ) {
        composable(Routes.LOGIN) { entry ->
            val info by entry.savedStateHandle.getStateFlow<String?>("info", null).collectAsStateWithLifecycle()
            LoginScreen(
                onNavigateRegister = { navController.navigate(Routes.REGISTER) { launchSingleTop = true } },
                infoMessage = info,
                onInfoShown = { entry.savedStateHandle["info"] = null },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onBack = { navController.safePopBack() },
                onRegistered = { message ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("info", message)
                    navController.safePopBack()
                },
            )
        }

        // Layar setelah login (membutuhkan data pengguna)
        composable(Routes.HOME_ADMIN) { user?.let { HomeScreen(navController, it) } }
        composable(Routes.HOME_USER) { user?.let { HomeScreen(navController, it) } }
        composable(Routes.PROFILE) { user?.let { ProfileScreen(navController, it) } }
        composable(Routes.SETTINGS) { user?.let { AccountSettingsScreen(navController, it) } }

        // Alat
        composable(Routes.EQUIPMENTS) { user?.let { EquipmentListScreen(navController, it.isAdmin) } }
        composable(Routes.EQUIPMENT_DETAIL, idArg) { entry ->
            user?.let {
                val id = entry.intArg("id")
                EquipmentDetailScreen(
                    id = id,
                    isAdmin = it.isAdmin,
                    onBack = { navController.safePopBack() },
                    onEdit = { navController.navigate(Routes.equipmentForm(id)) { launchSingleTop = true } },
                    onRent = { navController.navigate(Routes.rentalForm(equipmentId = id)) { launchSingleTop = true } },
                )
            }
        }
        composable(Routes.EQUIPMENT_FORM, idArg) { entry ->
            if (user?.isAdmin == true) EquipmentFormScreen(entry.intArg("id")) { navController.safePopBack() }
        }

        // Kategori (tab untuk Penyewa; layar penuh dengan tombol Kembali untuk Admin)
        composable(Routes.CATEGORIES) { user?.let { CategoryListScreen(navController, it.isAdmin) } }
        composable(Routes.CATEGORY_FORM, idArg) { entry ->
            if (user?.isAdmin == true) CategoryFormScreen(entry.intArg("id")) { navController.safePopBack() }
        }

        // Pelanggan (Admin)
        composable(Routes.CUSTOMERS) { user?.let { CustomerListScreen(navController, it.isAdmin) } }
        composable(Routes.CUSTOMER_FORM, idArg) { entry ->
            if (user?.isAdmin == true) CustomerFormScreen(entry.intArg("id")) { navController.safePopBack() }
        }

        // Penyewaan
        composable(Routes.RENTALS) { user?.let { RentalListScreen(navController, it.isAdmin) } }
        composable(Routes.RENTAL_DETAIL, idArg) { entry ->
            user?.let {
                val id = entry.intArg("id")
                RentalDetailScreen(
                    id = id,
                    isAdmin = it.isAdmin,
                    onBack = { navController.safePopBack() },
                    onEdit = { targetId -> navController.navigate(Routes.rentalForm(targetId)) { launchSingleTop = true } },
                )
            }
        }
        composable(
            Routes.RENTAL_FORM,
            listOf(
                navArgument("id") { type = NavType.IntType },
                navArgument("equipmentId") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { entry ->
            user?.let {
                RentalFormScreen(
                    id = entry.intArg("id"),
                    presetEquipmentId = entry.intArg("equipmentId"),
                    isAdmin = it.isAdmin,
                    onBack = { navController.safePopBack() },
                )
            }
        }

        // Pengguna & Laporan (Admin)
        composable(Routes.USERS) { if (user?.isAdmin == true) UserListScreen(navController, user) }
        composable(Routes.USER_FORM, idArg) { entry ->
            if (user?.isAdmin == true) UserFormScreen(entry.intArg("id"), user) { navController.safePopBack() }
        }
        composable(Routes.REPORTS) {
            if (user?.isAdmin == true) ReportScreen(onBack = { navController.safePopBack() })
        }
        composable(Routes.PACKAGE_PLANNER) {
            user?.let {
                PackagePlannerScreen(
                    isAdmin = it.isAdmin,
                    onBack = { navController.safePopBack() },
                    onSubmitted = { navController.navigateToTab(Routes.RENTALS, it.isAdmin) },
                )
            }
        }
        composable(Routes.NOTIFICATIONS) {
            user?.let {
                NotificationScreen(
                    onBack = { navController.safePopBack() },
                    onOpenRental = { rentalId ->
                        navController.navigate(Routes.rentalDetail(rentalId)) { launchSingleTop = true }
                    },
                    onOpenEquipment = { eqId ->
                        navController.navigate(Routes.equipmentDetail(eqId)) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}


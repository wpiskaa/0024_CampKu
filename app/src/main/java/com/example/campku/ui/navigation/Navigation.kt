package com.example.campku.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.campku.R

/** Seluruh rute Navigation Compose. */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"

    // startDestination ditentukan oleh peran (SRS bagian 7)
    const val HOME_ADMIN = "home_admin"
    const val HOME_USER = "home_user"

    const val EQUIPMENTS = "equipments"
    const val EQUIPMENT_DETAIL = "equipments/{id}"
    const val EQUIPMENT_FORM = "equipment_form/{id}"

    const val CATEGORIES = "categories"
    const val CATEGORY_FORM = "category_form/{id}"

    const val CUSTOMERS = "customers"
    const val CUSTOMER_FORM = "customer_form/{id}"

    const val RENTALS = "rentals"
    const val RENTAL_DETAIL = "rentals/{id}"
    const val RENTAL_FORM = "rental_form/{id}?equipmentId={equipmentId}"

    const val USERS = "users"
    const val USER_FORM = "user_form/{id}"

    const val PROFILE = "profile"

    fun home(isAdmin: Boolean) = if (isAdmin) HOME_ADMIN else HOME_USER

    fun equipmentDetail(id: Int) = "equipments/$id"
    fun equipmentForm(id: Int = 0) = "equipment_form/$id"
    fun categoryForm(id: Int = 0) = "category_form/$id"
    fun customerForm(id: Int = 0) = "customer_form/$id"
    fun rentalDetail(id: Int) = "rentals/$id"
    fun rentalForm(id: Int = 0, equipmentId: Int = 0) = "rental_form/$id?equipmentId=$equipmentId"
    fun userForm(id: Int = 0) = "user_form/$id"
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val isCenter: Boolean = false,
)

/**
 * Menu bawah Penyewa: 5 tab dengan Beranda tepat di tengah (posisi ke-3).
 */
private val userTabs = listOf(
    Tab(Routes.EQUIPMENTS, "Alat", Icons.Filled.Terrain),
    Tab(Routes.CATEGORIES, "Kategori", Icons.Filled.Category),
    Tab(Routes.HOME_USER, "Beranda", Icons.Filled.Home, isCenter = true),
    Tab(Routes.RENTALS, "Sewa", Icons.Filled.Receipt),
    Tab(Routes.PROFILE, "Profil", Icons.Filled.Person),
)

/**
 * Menu bawah Admin: 6 tab dengan Beranda di tengah (posisi ke-3).
 * Menu Pengguna juga tetap dapat diakses melalui halaman Profil & Pengaturan atau Beranda Admin.
 */
private val adminTabs = listOf(
    Tab(Routes.EQUIPMENTS, "Alat", Icons.Filled.Terrain),
    Tab(Routes.CATEGORIES, "Kategori", Icons.Filled.Category),
    Tab(Routes.HOME_ADMIN, "Beranda", Icons.Filled.Home, isCenter = true),
    Tab(Routes.RENTALS, "Sewa", Icons.Filled.Receipt),
    Tab(Routes.CUSTOMERS, "Pelanggan", Icons.Filled.Group),
    Tab(Routes.PROFILE, "Profil", Icons.Filled.Person),
)

/**
 * Kerangka layar utama (tab): top bar + menu bawah.
 * Admin memiliki menu tambahan (Kategori, Pelanggan, Pengguna), sehingga menu bawahnya dapat digeser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    isAdmin: Boolean,
    title: String,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_campku_logo),
                            contentDescription = "Logo CampKu",
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = { AppBottomBar(navController, isAdmin) },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Composable
private fun AppBottomBar(navController: NavHostController, isAdmin: Boolean) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = if (isAdmin) adminTabs else userTabs
    val homeRoute = Routes.home(isAdmin)

    Surface(tonalElevation = 3.dp, shadowElevation = 10.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(74.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                BottomItem(
                    selected = currentRoute == tab.route,
                    label = tab.label,
                    icon = tab.icon,
                    isCenter = tab.isCenter,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (currentRoute != tab.route) {
                            navController.navigate(tab.route) {
                                popUpTo(homeRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    isCenter: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = when {
        isCenter && selected -> MaterialTheme.colorScheme.onPrimary
        isCenter -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val pillColor = when {
        isCenter && selected -> MaterialTheme.colorScheme.primary
        isCenter -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(if (isCenter) 20.dp else 16.dp),
            color = pillColor,
            shadowElevation = if (isCenter) 3.dp else 0.dp,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier
                    .padding(horizontal = if (isCenter) 18.dp else 12.dp, vertical = if (isCenter) 6.dp else 4.dp)
                    .size(if (isCenter) 24.dp else 21.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected || isCenter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

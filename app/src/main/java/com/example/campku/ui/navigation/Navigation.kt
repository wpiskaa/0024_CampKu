package com.example.campku.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    const val SETTINGS = "settings"

    fun home(isAdmin: Boolean) = if (isAdmin) HOME_ADMIN else HOME_USER

    fun equipmentDetail(id: Int) = "equipments/$id"
    fun equipmentForm(id: Int = 0) = "equipment_form/$id"
    fun categoryForm(id: Int = 0) = "category_form/$id"
    fun customerForm(id: Int = 0) = "customer_form/$id"
    fun rentalDetail(id: Int) = "rentals/$id"
    fun rentalForm(id: Int = 0, equipmentId: Int = 0) = "rental_form/$id?equipmentId=$equipmentId"
    fun userForm(id: Int = 0) = "user_form/$id"
}

/**
 * Navigasi tab yang bersih dan konsisten:
 * - Menuju Beranda selalu mengosongkan tumpukan di atas Beranda agar kembali ke tampilan awal.
 * - Menuju tab lain selalu membersihkan tumpukan tab sebelumnya (tanpa saveState/restoreState)
 *   sehingga setiap tab dibuka dari tampilan awal dan tombol Kembali selalu mengarah ke Beranda.
 */
fun NavHostController.navigateToTab(route: String, isAdmin: Boolean) {
    val homeRoute = Routes.home(isAdmin)
    if (route == homeRoute) {
        val popped = popBackStack(homeRoute, inclusive = false)
        if (!popped) {
            navigate(homeRoute) {
                popUpTo(graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        return
    }
    navigate(route) {
        popUpTo(homeRoute) {
            inclusive = false
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}

/** Kembali dengan aman agar tap ganda pada tombol Back tidak pernah mengosongkan layar utama. */
fun NavHostController.safePopBack() {
    if (previousBackStackEntry != null) popBackStack()
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val isCenter: Boolean = false,
)

/** Menu bawah Penyewa: 5 tab, Beranda tepat di tengah. */
private val userTabs = listOf(
    Tab(Routes.EQUIPMENTS, "Alat", Icons.Filled.Terrain),
    Tab(Routes.CATEGORIES, "Kategori", Icons.Filled.Category),
    Tab(Routes.HOME_USER, "Beranda", Icons.Filled.Home, isCenter = true),
    Tab(Routes.RENTALS, "Sewa", Icons.Filled.Receipt),
    Tab(Routes.PROFILE, "Profil", Icons.Filled.Person),
)

/**
 * Menu bawah Admin: 5 tab, Beranda tepat di tengah.
 * Kategori dan Pengguna tetap bisa dibuka lewat pintasan di Beranda dan menu Profil.
 */
private val adminTabs = listOf(
    Tab(Routes.EQUIPMENTS, "Alat", Icons.Filled.Terrain),
    Tab(Routes.RENTALS, "Sewa", Icons.Filled.Receipt),
    Tab(Routes.HOME_ADMIN, "Beranda", Icons.Filled.Home, isCenter = true),
    Tab(Routes.CUSTOMERS, "Pelanggan", Icons.Filled.Group),
    Tab(Routes.PROFILE, "Profil", Icons.Filled.Person),
)

/**
 * Kerangka layar utama (tab): top bar terang + dock menu bawah dengan tombol Beranda melayang di tengah.
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
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shadowElevation = 1.dp,
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_campku_logo),
                                    contentDescription = "Logo CampKu",
                                    modifier = Modifier.padding(3.dp).size(32.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = { AppBottomBar(navController, isAdmin) },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/**
 * Dock menu bawah: latar putih, garis tipis di atas, 5 tab,
 * dan tombol Beranda bundar di tengah yang menonjol ke atas (digambar di luar dock agar tidak terpotong).
 */
@Composable
private fun AppBottomBar(navController: NavHostController, isAdmin: Boolean) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = if (isAdmin) adminTabs else userTabs
    val primary = MaterialTheme.colorScheme.primary

    fun go(route: String) {
        if (currentRoute != route) {
            navController.navigateToTab(route, isAdmin)
        }
    }

    Box(Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 14.dp,
        ) {
            Column(Modifier.navigationBarsPadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        if (tab.isCenter) {
                            // Slot tengah: hanya label (tombol bundar digambar di atas dock)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { go(tab.route) }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Spacer(Modifier.height(24.dp))
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primary,
                                    maxLines = 1,
                                )
                            }
                        } else {
                            NavItem(tab, selected, { go(tab.route) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Tombol Beranda bundar yang menonjol ke atas dengan cincin putih
        val centerTab = tabs.first { it.isCenter }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .size(60.dp)
                .shadow(10.dp, CircleShape, ambientColor = primary, spotColor = primary)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .padding(4.dp)
                .background(
                    Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.78f))),
                    CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { go(centerTab.route) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                centerTab.icon,
                contentDescription = centerTab.label,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
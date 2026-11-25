package com.example.campku.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.campku.CampKuApp
import com.example.campku.R

/** Seluruh rute Navigation Compose. */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"

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
    const val REPORTS = "reports"
    const val PACKAGE_PLANNER = "package_planner"
    const val NOTIFICATIONS = "notifications"

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
 * - Menuju tab lain selalu membersihkan tumpukan tab sebelumnya sehingga setiap tab dibuka dari tampilan awal.
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
    val icon: ImageVector? = null,
    val drawableRes: Int? = null,
)

/** Menu bawah Penyewa: 5 tab, Beranda (ikon tenda) di paling kiri/utama. */
private val userTabs = listOf(
    Tab(Routes.HOME_USER, "Beranda", drawableRes = R.drawable.ic_tent),
    Tab(Routes.EQUIPMENTS, "Alat", icon = Icons.Filled.Terrain),
    Tab(Routes.CATEGORIES, "Kategori", icon = Icons.Filled.Category),
    Tab(Routes.RENTALS, "Sewa", icon = Icons.Filled.Receipt),
    Tab(Routes.PROFILE, "Profil", icon = Icons.Filled.Person),
)

/** Menu bawah Admin: 5 tab, Beranda (ikon tenda) di paling kiri/utama. */
private val adminTabs = listOf(
    Tab(Routes.HOME_ADMIN, "Beranda", drawableRes = R.drawable.ic_tent),
    Tab(Routes.EQUIPMENTS, "Alat", icon = Icons.Filled.Terrain),
    Tab(Routes.RENTALS, "Sewa", icon = Icons.Filled.Receipt),
    Tab(Routes.CUSTOMERS, "Pelanggan", icon = Icons.Filled.Group),
    Tab(Routes.PROFILE, "Profil", icon = Icons.Filled.Person),
)

/**
 * Kerangka layar utama (tab): top bar terang + ikon notifikasi + dock menu bawah beranimasi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    isAdmin: Boolean,
    title: String,
    snackbarHostState: SnackbarHostState? = null,
    showNotificationBell: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val context = LocalContext.current
    var urgentCount by remember { mutableIntStateOf(0) }

    if (showNotificationBell) {
        LaunchedEffect(title) {
            val repo = (context.applicationContext as CampKuApp).container.dashboardRepository
            repo.notifications().onSuccess { list ->
                urgentCount = list.count { it.urgent }.takeIf { it > 0 } ?: list.size
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
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
                    actions = {
                        actions()
                        if (showNotificationBell) {
                            IconButton(onClick = {
                                navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
                            }) {
                                Box {
                                    Icon(Icons.Filled.Notifications, contentDescription = "Notifikasi")
                                    if (urgentCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 2.dp, y = (-2).dp)
                                                .size(9.dp)
                                                .background(Color(0xFFD32F2F), CircleShape),
                                        )
                                    }
                                }
                            }
                        }
                    },
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
 * Dock menu bawah beranimasi:
 * - Beranda (ikon tenda) di paling kiri/utama.
 * - Indikator pil aktif meluncur halus (spring animation) mengikuti tab yang sedang dibuka.
 */
@Composable
private fun AppBottomBar(navController: NavHostController, isAdmin: Boolean) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = if (isAdmin) adminTabs else userTabs
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                val tabWidth = maxWidth / tabs.size
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = 0.74f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "tabIndicatorOffset",
                )

                // Latar pil aktif yang bergeser halus antar tab
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 3.dp)
                        .background(primaryContainer.copy(alpha = 0.78f), RoundedCornerShape(16.dp)),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = index == selectedIndex
                        val tint by animateColorAsState(
                            targetValue = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(220),
                            label = "tabTint",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.14f else 0.96f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
                            label = "tabScale",
                        )
                        val iconLift by animateDpAsState(
                            targetValue = if (selected) (-1).dp else 0.dp,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                            label = "tabLift",
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (currentRoute != tab.route) {
                                        navController.navigateToTab(tab.route, isAdmin)
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .offset(y = iconLift)
                                    .scale(iconScale),
                            ) {
                                if (tab.drawableRes != null) {
                                    Icon(
                                        painter = painterResource(id = tab.drawableRes),
                                        contentDescription = tab.label,
                                        tint = tint,
                                        modifier = Modifier.size(22.dp),
                                    )
                                } else if (tab.icon != null) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = tint,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

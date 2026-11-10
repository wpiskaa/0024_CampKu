package com.example.campku.ui.equipment

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.campku.appViewModel
import com.example.campku.data.local.PhotoStorage
import com.example.campku.data.model.Equipment
import com.example.campku.ui.components.AppTextField
import com.example.campku.ui.components.ConfirmDialog
import com.example.campku.ui.components.DropdownField
import com.example.campku.ui.components.EmptyState
import com.example.campku.ui.components.ErrorState
import com.example.campku.ui.components.ListCard
import com.example.campku.ui.components.LoadingButton
import com.example.campku.ui.components.MessageEffect
import com.example.campku.ui.components.OnResume
import com.example.campku.ui.components.PhotoImage
import com.example.campku.ui.components.PhotoPickerButtons
import com.example.campku.ui.components.SearchField
import com.example.campku.ui.components.SectionTitle
import com.example.campku.ui.components.StateContent
import com.example.campku.ui.components.StatusChip
import com.example.campku.ui.components.SubScreen
import com.example.campku.ui.navigation.MainScreen
import com.example.campku.ui.navigation.Routes
import com.example.campku.ui.theme.StatusLate
import com.example.campku.ui.theme.StatusReturned
import com.example.campku.util.formatRupiah

@Composable
fun StockChip(stock: Int, modifier: Modifier = Modifier) {
    if (stock > 0) StatusChip("Stok $stock", StatusReturned, modifier) else StatusChip("Habis", StatusLate, modifier)
}

/** Kartu alat dipakai di daftar alat dan alat populer (Beranda). */
@Composable
fun EquipmentCard(
    equipment: Equipment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    ListCard(onClick = onClick, modifier = modifier) {
        PhotoImage(
            kind = PhotoStorage.KIND_EQUIPMENT,
            id = equipment.id,
            serverPath = equipment.photoPath,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                equipment.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                equipment.categoryName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${formatRupiah(equipment.dailyPrice)} / hari",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            StockChip(equipment.stock)
        }
        trailing()
    }
}

/** Daftar Alat (cari + filter kategori). */
@Composable
fun EquipmentListScreen(navController: NavHostController, isAdmin: Boolean) {
    val vm = appViewModel { EquipmentListViewModel(it.equipmentRepository, it.categoryRepository) }
    val items by vm.items.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<Equipment?>(null) }

    OnResume { vm.refresh(); vm.loadCategories() }
    MessageEffect(message, snackbar, vm::consumeMessage)

    MainScreen(
        navController = navController,
        isAdmin = isAdmin,
        title = "Katalog Alat",
        snackbarHostState = snackbar,
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = { navController.navigate(Routes.equipmentForm()) }) {
                    Icon(Icons.Filled.Add, "Tambah alat")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = search,
                onValueChange = vm::onSearchChange,
                placeholder = "Cari nama alat",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = filter == null, onClick = { vm.onFilterChange(null) }, label = { Text("Semua") })
                }
                items(categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = filter == c.id,
                        onClick = { vm.onFilterChange(c.id) },
                        label = { Text(c.name) },
                    )
                }
            }
            StateContent(items, onRetry = vm::retry) { list ->
                if (list.isEmpty()) {
                    EmptyState(if (search.isBlank() && filter == null) "Belum ada alat" else "Alat tidak ditemukan")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(list, key = { it.id }) { e ->
                            EquipmentCard(e, onClick = { navController.navigate(Routes.equipmentDetail(e.id)) }) {
                                if (isAdmin) {
                                    Column {
                                        IconButton(onClick = { navController.navigate(Routes.equipmentForm(e.id)) }) {
                                            Icon(Icons.Filled.Edit, "Ubah", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { deleteTarget = e }) {
                                            Icon(Icons.Filled.Delete, "Hapus", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Hapus alat?",
            message = "\"${target.name}\" akan dihapus permanen.",
            confirmText = "Hapus",
            onConfirm = { vm.delete(target.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** Detail Alat: foto, harga, stok, deskripsi. */
@Composable
fun EquipmentDetailScreen(
    id: Int,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRent: () -> Unit,
) {
    val vm = appViewModel(key = "equipment_detail_$id") { EquipmentDetailViewModel(it.equipmentRepository, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    OnResume { vm.load() }
    MessageEffect(message, snackbar, vm::consumeMessage)
    LaunchedEffect(deleted) { if (deleted) onBack() }

    SubScreen(
        title = "Detail Alat",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (isAdmin) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Ubah") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Hapus") }
            }
        },
    ) { padding ->
        StateContent(state, onRetry = { vm.load(silent = false) }, modifier = Modifier.padding(padding)) { e ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                PhotoImage(
                    kind = PhotoStorage.KIND_EQUIPMENT,
                    id = e.id,
                    serverPath = e.photoPath,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(e.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(e.categoryName.orEmpty(), MaterialTheme.colorScheme.secondary)
                        StockChip(e.stock)
                    }
                    Text(
                        "${formatRupiah(e.dailyPrice)} / hari",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    SectionTitle("Deskripsi")
                    Text(
                        e.description?.takeIf { it.isNotBlank() } ?: "Tidak ada deskripsi.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    LoadingButton(
                        text = if (e.stock > 0) "Sewa Alat Ini" else "Stok Habis",
                        onClick = onRent,
                        loading = false,
                        enabled = e.stock > 0,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus alat?",
            message = "Alat ini akan dihapus permanen.",
            confirmText = "Hapus",
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Form tambah/ubah alat (Admin). id = 0 untuk alat baru. */
@Composable
fun EquipmentFormScreen(id: Int, onBack: () -> Unit) {
    val vm = appViewModel(key = "equipment_form_$id") {
        EquipmentFormViewModel(it.equipmentRepository, it.categoryRepository, id)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.saved) {
        if (state.saved) {
            state.savedWarning?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            onBack()
        }
    }

    SubScreen(title = if (vm.isEdit) "Ubah Alat" else "Tambah Alat", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> ErrorState(state.loadError!!, vm::load, Modifier.align(Alignment.Center))
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SectionTitle("Foto Alat")
                    PhotoImage(
                        kind = PhotoStorage.KIND_EQUIPMENT,
                        id = id,
                        serverPath = state.existingPhotoPath,
                        previewUri = state.photoUri,
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                    PhotoPickerButtons(onPicked = { uri -> vm.update { copy(photoUri = uri) } }, enabled = !state.saving)
                    Spacer(Modifier.height(8.dp))

                    SectionTitle("Data Alat")
                    AppTextField(state.name, { vm.update { copy(name = it) } }, "Nama alat", error = state.errors["name"])
                    DropdownField(
                        label = "Kategori",
                        options = state.categories.map { it.id to it.name },
                        selectedId = state.categoryId,
                        onSelected = { c -> vm.update { copy(categoryId = c) } },
                        error = state.errors["category"],
                    )
                    if (state.categories.isEmpty()) {
                        Text(
                            "Belum ada kategori. Buat kategori terlebih dahulu di menu Kategori.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    AppTextField(
                        state.price, { vm.update { copy(price = it.filter(Char::isDigit)) } }, "Harga sewa per hari (Rp)",
                        error = state.errors["price"], keyboardType = KeyboardType.Number,
                    )
                    AppTextField(
                        state.stock, { vm.update { copy(stock = it.filter(Char::isDigit)) } }, "Stok",
                        error = state.errors["stock"], keyboardType = KeyboardType.Number,
                    )
                    AppTextField(
                        state.description, { vm.update { copy(description = it) } }, "Deskripsi (opsional)",
                        singleLine = false, minLines = 3,
                    )
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    LoadingButton("Simpan", onClick = vm::save, loading = state.saving)
                }
            }
        }
    }
}


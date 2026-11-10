package com.example.campku.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import coil.compose.AsyncImage
import com.example.campku.CampKuApp
import com.example.campku.data.remote.NetworkModule

/**
 * Menampilkan foto. Sesuai FR-14 foto dibaca dari penyimpanan internal (filesDir) lebih dulu;
 * bila tidak ada di perangkat ini, dimuat dari server.
 */
@Composable
fun PhotoImage(
    kind: String,
    id: Int,
    serverPath: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    previewUri: Uri? = null,
    /** Ubah nilainya setelah foto lokal diganti agar gambar dimuat ulang. */
    @Suppress("UNUSED_PARAMETER") version: Int = 0,
) {
    val context = LocalContext.current
    val storage = (context.applicationContext as CampKuApp).container.photoStorage
    val model: Any? = previewUri
        ?: storage.localFileOrNull(kind, id)
        ?: NetworkModule.photoUrl(serverPath)

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(
                Icons.Filled.Terrain,
                contentDescription = "Belum ada foto",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = "Foto",
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Tombol "Kamera" dan "Galeri". Hasil dikirim sebagai [Uri] lewat [onPicked]. */
@Composable
fun PhotoPickerButtons(
    onPicked: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val storage = (context.applicationContext as CampKuApp).container.photoStorage
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (ok && uri != null) onPicked(uri)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = {
                val uri = storage.newCameraUri()
                pendingCameraUri = uri
                try {
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    pendingCameraUri = null
                    Toast.makeText(context, "Aplikasi kamera tidak tersedia", Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Icon(Icons.Filled.CameraAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Kamera")
        }
        OutlinedButton(
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        ) {
            Icon(Icons.Filled.Collections, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Galeri")
        }
    }
}

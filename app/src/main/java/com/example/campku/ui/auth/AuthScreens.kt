package com.example.campku.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campku.R
import com.example.campku.appViewModel
import com.example.campku.ui.components.AppTextField
import com.example.campku.ui.components.LoadingButton
import com.example.campku.ui.components.MessageEffect
import com.example.campku.ui.components.SubScreen

@Composable
private fun BrandHeader(subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                ),
            )
            .statusBarsPadding()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_campku_logo),
                contentDescription = "Logo CampKu",
                modifier = Modifier.size(92.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "CampKu",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
fun LoginScreen(
    onNavigateRegister: () -> Unit,
    infoMessage: String?,
    onInfoShown: () -> Unit,
) {
    val vm = appViewModel { LoginViewModel(it.authRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(infoMessage, snackbar, onInfoShown)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            BrandHeader("Sewa alat camping jadi mudah")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Masuk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    AppTextField(
                        value = state.email,
                        onValueChange = vm::onEmailChange,
                        label = "Email",
                        error = state.emailError,
                        keyboardType = KeyboardType.Email,
                    )
                    AppTextField(
                        value = state.password,
                        onValueChange = vm::onPasswordChange,
                        label = "Password",
                        error = state.passwordError,
                        isPassword = true,
                    )
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    LoadingButton("Masuk", onClick = vm::submit, loading = state.loading)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Belum punya akun?", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onNavigateRegister) { Text("Daftar") }
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(onBack: () -> Unit, onRegistered: (String) -> Unit) {
    val vm = appViewModel { RegisterViewModel(it.authRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let(onRegistered)
    }

    SubScreen(title = "Daftar Akun", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Buat akun penyewa untuk melihat alat dan memesan sewa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(8.dp))
            AppTextField(state.name, { vm.update { copy(name = it) } }, "Nama lengkap", error = state.errors["name"])
            AppTextField(
                state.email, { vm.update { copy(email = it) } }, "Email",
                error = state.errors["email"], keyboardType = KeyboardType.Email,
            )
            AppTextField(
                state.phone, { vm.update { copy(phone = it) } }, "Nomor HP",
                error = state.errors["phone"], keyboardType = KeyboardType.Phone,
            )
            AppTextField(
                state.password, { vm.update { copy(password = it) } }, "Password (min. 6 karakter)",
                error = state.errors["password"], isPassword = true,
            )
            AppTextField(
                state.confirmPassword, { vm.update { copy(confirmPassword = it) } }, "Ulangi password",
                error = state.errors["confirm"], isPassword = true,
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            LoadingButton("Daftar", onClick = vm::submit, loading = state.loading)
        }
    }
}


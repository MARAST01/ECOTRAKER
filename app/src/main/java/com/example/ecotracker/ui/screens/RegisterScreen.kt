package com.example.ecotracker.ui.screens

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ecotracker.R
import com.example.ecotracker.ui.viewmodel.RegisterViewModel
import com.example.ecotracker.ui.viewmodel.RegisterUiState
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes

@Composable
fun RegisterScreen(onBack: () -> Unit, onSuccess: () -> Unit) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val vm: RegisterViewModel = viewModel()
    val ui by vm.uiState.collectAsState(initial = RegisterUiState())

    LaunchedEffect(ui.success) {
        if (ui.success != null) {
            onSuccess()
        }
    }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d("RegisterScreen", "GoogleSignIn result code=${result.resultCode}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (!idToken.isNullOrBlank()) {
                vm.registerWithGoogle(idToken)
            } else {
                Log.e("RegisterScreen", "GoogleSignIn returned null/blank idToken")
            }
        } catch (e: ApiException) {
            val code = e.statusCode
            val codeStr = GoogleSignInStatusCodes.getStatusCodeString(code)
            Log.e("RegisterScreen", "GoogleSignIn ApiException: $code ($codeStr)", e)
        } catch (e: Exception) {
            Log.e("RegisterScreen", "Error getting Google account", e)
        }
    }

    fun launchGoogle() {
        val webClientId = context.getString(R.string.default_web_client_id)
        if (webClientId.isNullOrBlank() || webClientId == "YOUR_WEB_CLIENT_ID") {
            Toast.makeText(context, "Configura Google Sign-In en Firebase (default_web_client_id)", Toast.LENGTH_LONG).show()
            Log.e("RegisterScreen", "default_web_client_id es placeholder o vacío: '$webClientId'")
            return
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        Log.d("RegisterScreen", "Launching GoogleSignIn for registration")
        launcher.launch(client.signInIntent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Crear cuenta",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Nombre completo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Número de celular") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.register(fullName.ifBlank { null }, phone.ifBlank { null }, email.trim(), password) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Registrarme", color = MaterialTheme.colorScheme.onPrimary)
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { launchGoogle() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Registrarme con Google", color = MaterialTheme.colorScheme.onSecondary)
        }

        Spacer(Modifier.height(12.dp))
        when {
            ui.isLoading -> Text("Creando cuenta...", color = MaterialTheme.colorScheme.primary)
            ui.error != null -> {
                val msg = ui.error
                if (msg != null) {
                    Text(msg, color = MaterialTheme.colorScheme.error)
                }
            }
            ui.success != null -> Text("¡Cuenta creada!", color = MaterialTheme.colorScheme.primary)
        }
    }
}



package com.example.ecotracker.ui.screens

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    // Errores de validación (tiempo real y al presionar Registrar)
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    val vm: RegisterViewModel = viewModel()
    val ui by vm.uiState.collectAsState(initial = RegisterUiState())
    val snackbarHostState = remember { SnackbarHostState() }

    val keyboardController = LocalSoftwareKeyboardController.current
    
    LaunchedEffect(ui.success) {
        if (ui.success != null) {
            // Ocultar el teclado si está abierto
            keyboardController?.hide()
            
            // Pequeña pausa para que el teclado se oculte
            delay(100)
            
            // Mostrar mensaje de éxito con animación suave
            snackbarHostState.showSnackbar(
                message = "Usuario registrado exitosamente",
                duration = SnackbarDuration.Short
            )
            // Esperar solo 1 segundo para navegación más rápida
            delay(1000)
            onSuccess()
        }
    }
    
    LaunchedEffect(ui.error) {
        val errorMessage = ui.error
        if (errorMessage != null) {
            // Ocultar el teclado si está abierto
            keyboardController?.hide()
            
            // Pequeña pausa para que el teclado se oculte
            delay(100)
            
            // Mostrar mensaje de error como snackbar flotante
            snackbarHostState.showSnackbar(
                message = errorMessage,
                duration = SnackbarDuration.Short
            )
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

    Scaffold(
        snackbarHost = { 
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    androidx.compose.material3.Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        actionColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(paddingValues)
                .padding(24.dp)
        ) {
        Text(
            text = "Crear cuenta",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(24.dp))

        // Funciones de validación
        fun isValidPhone(value: String): Boolean = value.matches(Regex("^\\d{10}$"))
        fun isValidEmail(value: String): Boolean = value.matches(Regex("^[^\\s@]+@[^\\s@]+\\.com$", RegexOption.IGNORE_CASE))
        fun isValidPassword(value: String): Boolean = value.length >= 6
        fun matchesConfirm(pass: String, confirm: String): Boolean = pass == confirm
        fun isNotBlank(value: String): Boolean = value.trim().isNotEmpty()

        fun validateAll(): Boolean {
            fullNameError = if (!isNotBlank(fullName)) "El nombre es obligatorio" else null
            phoneError = when {
                !isNotBlank(phone) -> "El número de celular es obligatorio"
                !isValidPhone(phone) -> "El número debe tener 10 dígitos"
                else -> null
            }
            emailError = when {
                !isNotBlank(email) -> "El correo es obligatorio"
                !isValidEmail(email.trim()) -> "Correo inválido. Debe contener @ y terminar en .com"
                else -> null
            }
            passwordError = when {
                !isNotBlank(password) -> "La contraseña es obligatoria"
                !isValidPassword(password) -> "La contraseña debe tener al menos 6 caracteres"
                else -> null
            }
            confirmPasswordError = when {
                !isNotBlank(confirmPassword) -> "Confirma tu contraseña"
                !matchesConfirm(password, confirmPassword) -> "Las contraseñas no coinciden"
                else -> null
            }
            return listOf(fullNameError, phoneError, emailError, passwordError, confirmPasswordError).all { it == null }
        }

        OutlinedTextField(
            value = fullName,
            onValueChange = {
                fullName = it
                fullNameError = if (isNotBlank(fullName)) null else "El nombre es obligatorio"
            },
            label = { Text("Nombre completo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = fullNameError != null,
            supportingText = {
                if (fullNameError != null) {
                    Text(fullNameError!!, color = MaterialTheme.colorScheme.error)
                }
            }
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = {
                phone = it.filter { ch -> ch.isDigit() }.take(10)
                phoneError = when {
                    !isNotBlank(phone) -> "El número de celular es obligatorio"
                    !isValidPhone(phone) -> "El número debe tener 10 dígitos"
                    else -> null
                }
            },
            label = { Text("Número de celular") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = phoneError != null,
            supportingText = {
                if (phoneError != null) {
                    Text(phoneError!!, color = MaterialTheme.colorScheme.error)
                }
            }
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = when {
                    !isNotBlank(email) -> "El correo es obligatorio"
                    !isValidEmail(email.trim()) -> "Correo inválido. Debe contener @ y terminar en .com"
                    else -> null
                }
            },
            label = { Text("Correo electrónico") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = emailError != null,
            supportingText = {
                if (emailError != null) {
                    Text(emailError!!, color = MaterialTheme.colorScheme.error)
                }
            }
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = when {
                    !isNotBlank(password) -> "La contraseña es obligatoria"
                    !isValidPassword(password) -> "La contraseña debe tener al menos 6 caracteres"
                    else -> null
                }
                // Revalidar confirmación si ya fue ingresada
                if (confirmPassword.isNotEmpty()) {
                    confirmPasswordError = if (matchesConfirm(password, confirmPassword)) null else "Las contraseñas no coinciden"
                }
            },
            label = { Text("Contraseña") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                androidx.compose.ui.text.input.VisualTransformation.None
            } else {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        text = if (passwordVisible) "👁️" else "👁️‍🗨️",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            isError = passwordError != null,
            supportingText = {
                if (passwordError != null) {
                    Text(passwordError!!, color = MaterialTheme.colorScheme.error)
                }
            }
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                confirmPasswordError = when {
                    !isNotBlank(confirmPassword) -> "Confirma tu contraseña"
                    !matchesConfirm(password, confirmPassword) -> "Las contraseñas no coinciden"
                    else -> null
                }
            },
            label = { Text("Confirmar contraseña") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (confirmPasswordVisible) {
                androidx.compose.ui.text.input.VisualTransformation.None
            } else {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Text(
                        text = if (confirmPasswordVisible) "👁️" else "👁️‍🗨️",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            isError = confirmPasswordError != null,
            supportingText = {
                if (confirmPasswordError != null) {
                    Text(confirmPasswordError!!, color = MaterialTheme.colorScheme.error)
                }
            }
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (validateAll()) {
                    vm.register(fullName.ifBlank { null }, phone.ifBlank { null }, email.trim(), password, confirmPassword)
                }
            },
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
            ui.success != null -> Text("¡Cuenta creada!", color = MaterialTheme.colorScheme.primary)
        }
        }
    }
}



package com.example.ecotracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ecotracker.ui.theme.ECOTRACKERTheme
import com.example.ecotracker.navigation.Destinations
import com.example.ecotracker.ui.screens.WelcomeScreen
import com.example.ecotracker.ui.screens.RegisterScreen
import com.example.ecotracker.ui.screens.MapScreen
import com.example.ecotracker.ui.screens.LoginScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import com.example.ecotracker.ui.viewmodel.LoginViewModel
import com.example.ecotracker.ui.viewmodel.AuthViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable development logging
        try {
            FirebaseFirestore.setLoggingEnabled(true)
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            FirebaseFirestore.getInstance().firestoreSettings = settings
            Log.d("ECOTRACKER", "Firestore logging enabled")
        } catch (e: Exception) {
            Log.e("ECOTRACKER", "Failed enabling Firestore logging: ${'$'}{e.message}", e)
        }
        enableEdgeToEdge()
        setContent {
            ECOTRACKERTheme {
                val navController = rememberNavController()
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authVm: AuthViewModel = viewModel()
                    val isSignedIn = authVm.state.collectAsState().value.isSignedIn
                    NavHost(
                        navController = navController,
                        startDestination = if (isSignedIn) Destinations.Map.route else Destinations.Welcome.route
                    ) {
                        composable(Destinations.Welcome.route) {
                            WelcomeScreen(
                                onCreateAccount = { navController.navigate(Destinations.Register.route) },
                                onOpenMap = { navController.navigate(Destinations.Map.route) },
                                onLogin = { navController.navigate(Destinations.Login.route) }
                            )
                        }
                        composable(Destinations.Login.route) {
                            val vm: LoginViewModel = viewModel()
                            val ui = vm.uiState.collectAsState()
                            LaunchedEffect(ui.value.success) {
                                if (ui.value.success != null) {
                                    // Navigate to Map after successful login
                                    navController.navigate(Destinations.Map.route) {
                                        popUpTo(Destinations.Welcome.route) { inclusive = true }
                                    }
                                    authVm.refresh()
                                }
                            }
                            LoginScreen(
                                onLoginEmail = { email, pass -> vm.loginEmail(email, pass) },
                                onLoginGoogle = { token -> vm.loginGoogle(token) },
                                onBack = { navController.popBackStack() },
                                isLoading = ui.value.isLoading,
                                errorMessage = ui.value.error
                            )
                        }
                        composable(Destinations.Register.route) {
                            RegisterScreen(
                                onBack = { navController.popBackStack() },
                                onSuccess = {
                                    navController.navigate(Destinations.Map.route) {
                                        popUpTo(Destinations.Welcome.route) { inclusive = true }
                                    }
                                    authVm.refresh()
                                }
                            )
                        }
                        composable(Destinations.Map.route) {
                            MapScreen(
                                onBack = { navController.popBackStack() },
                                onSignOut = {
                                    authVm.signOut()
                                    navController.navigate(Destinations.Welcome.route) {
                                        popUpTo(Destinations.Map.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
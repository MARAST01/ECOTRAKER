package com.example.ecotracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ecotracker.ui.theme.ECOTRACKERTheme
import com.example.ecotracker.navigation.Destinations
import com.example.ecotracker.ui.screens.WelcomeScreen
import com.example.ecotracker.ui.screens.RegisterScreen
import com.example.ecotracker.ui.screens.DashboardScreen
import com.example.ecotracker.ui.screens.MapScreen
import com.example.ecotracker.ui.screens.LoginScreen
import com.example.ecotracker.ui.screens.TransportSelectionScreen
import com.example.ecotracker.ui.screens.RegistryScreen
import com.example.ecotracker.ui.components.BottomNavigationBar
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import com.example.ecotracker.ui.viewmodel.LoginViewModel
import com.example.ecotracker.ui.viewmodel.AuthViewModel
import com.example.ecotracker.service.TripDetectionService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import android.content.Intent
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

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
            ECOTRACKERTheme(darkTheme = false) {
                val navController = rememberNavController()
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    val authVm: AuthViewModel = viewModel()
                    val isSignedIn = authVm.state.collectAsState().value.isSignedIn
                    
                    // Iniciar servicio de detección automática si el usuario está autenticado
                    LaunchedEffect(isSignedIn) {
                        if (isSignedIn) {
                            val hasLocationPermission = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            val hasBackgroundPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            } else {
                                true // En versiones anteriores a Android 10, se otorga automáticamente
                            }
                            
                            if (hasLocationPermission && hasBackgroundPermission) {
                                val intent = Intent(this@MainActivity, TripDetectionService::class.java).apply {
                                    action = TripDetectionService.ACTION_START_TRACKING
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    startForegroundService(intent)
                                } else {
                                    startService(intent)
                                }
                            }
                        }
                    }
                    
                    NavHost(
                        navController = navController,
                        startDestination = if (isSignedIn) Destinations.Dashboard.route else Destinations.Welcome.route
                    ) {
                        composable(Destinations.Welcome.route) {
                            WelcomeScreen(
                                onCreateAccount = { navController.navigate(Destinations.Register.route) },
                                onLogin = { navController.navigate(Destinations.Login.route) }
                            )
                        }
                        composable(Destinations.Login.route) {
                            val vm: LoginViewModel = viewModel()
                            val ui = vm.uiState.collectAsState()
                            LaunchedEffect(ui.value.success) {
                                if (ui.value.success != null) {
                                    // Navigate to Dashboard after successful login
                                    navController.navigate(Destinations.Dashboard.route) {
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
                                    navController.navigate(Destinations.Welcome.route) {
                                        popUpTo(Destinations.Register.route) { inclusive = true }
                                    }
                                    authVm.refresh()
                                }
                            )
                        }
                        composable(Destinations.Dashboard.route) {
                            DashboardScreen(
                                onTransportClick = { navController.navigate(Destinations.TransportSelection.route) },
                                onRegistryClick = { navController.navigate(Destinations.Registry.route) },
                                onSignOut = {
                                    // Detener el servicio de detección al cerrar sesión
                                    val stopIntent = Intent(this@MainActivity, TripDetectionService::class.java).apply {
                                        action = TripDetectionService.ACTION_STOP_TRACKING
                                    }
                                    stopService(stopIntent)
                                    
                                    authVm.signOut()
                                    navController.navigate(Destinations.Welcome.route) {
                                        popUpTo(Destinations.Dashboard.route) { inclusive = true }
                                    }
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
                                },
                                onTransportSelection = { navController.navigate(Destinations.TransportSelection.route) }
                            )
                        }
                        composable(Destinations.Registry.route) {
                            RegistryScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Destinations.TransportSelection.route) {
                            TransportSelectionScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
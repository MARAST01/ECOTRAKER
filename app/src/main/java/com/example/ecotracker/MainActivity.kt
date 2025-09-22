package com.example.ecotracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ecotracker.ui.theme.ECOTRACKERTheme
import com.example.ecotracker.navigation.Destinations
import com.example.ecotracker.ui.screens.WelcomeScreen
import com.example.ecotracker.ui.screens.RegisterScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ECOTRACKERTheme {
                val navController = rememberNavController()
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = Destinations.Welcome.route
                    ) {
                        composable(Destinations.Welcome.route) {
                            WelcomeScreen(
                                onCreateAccount = { navController.navigate(Destinations.Register.route) }
                            )
                        }
                        composable(Destinations.Register.route) {
                            RegisterScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
package com.example.ecotracker.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ecotracker.ui.viewmodel.TransportViewModel
import com.example.ecotracker.ui.viewmodel.TripDetectionViewModel
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ecotracker.utils.BatteryOptimizationHelper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ecotracker.ui.components.BottomNavigationBar
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import android.util.Log

@Composable
fun SpeedIndicator(
    speedKmh: Float,
    isTracking: Boolean,
    modifier: Modifier = Modifier
) {
    // Redondear a 1 decimal y asegurar que no sea negativo
    val displaySpeed = maxOf(0f, speedKmh)
    
    // Determinar el estado: solo "Trayecto activo" cuando está registrando un trayecto
    val isActiveTrip = isTracking
    
    Card(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveTrip) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Indicador de estado - Verde solo cuando está registrando trayecto activo
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (isActiveTrip) {
                            // Verde/primario cuando está registrando trayecto
                            MaterialTheme.colorScheme.primary
                        } else {
                            // Gris cuando solo está detectando
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
            )
            
            Column {
                Text(
                    text = "${String.format("%.1f", displaySpeed)} km/h",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isActiveTrip) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = if (isActiveTrip) {
                        "Trayecto activo"
                    } else {
                        "Detectando"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActiveTrip) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(
    onProfileClick: () -> Unit,
    onRegistryClick: () -> Unit,
    onSignOut: () -> Unit,
    onFriendshipClick: () -> Unit
) {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(false) }
    var hasBackgroundLocationPermission by remember { mutableStateOf(false) }
    
    val viewModel: TransportViewModel = viewModel()
    val tripDetectionViewModel: TripDetectionViewModel = viewModel()
    val currentUser = FirebaseAuth.getInstance().currentUser
    
    // Launcher para permisos de ubicación en segundo plano (Android 10+)
    val backgroundLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasBackgroundLocationPermission = isGranted
        if (isGranted && currentUser != null) {
            tripDetectionViewModel.startTripDetectionAutomatically(context)
        }
    }
    
    // Launcher para permisos de ubicación
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        // Si se otorgaron permisos de ubicación y es Android 10+, solicitar permiso de segundo plano
        if (hasLocationPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                hasBackgroundLocationPermission = true
            }
        } else if (hasLocationPermission && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            // En versiones anteriores a Android 10, el permiso de segundo plano se otorga automáticamente
            hasBackgroundLocationPermission = true
        }
    }
    
    // Verificar permisos al iniciar
    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = fineGranted || coarseGranted
        
        if (hasLocationPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            hasBackgroundLocationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else if (hasLocationPermission && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            hasBackgroundLocationPermission = true
        }
        
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    
    // Solicitar ignorar optimizaciones de batería (crítico para 24/7)
    LaunchedEffect(currentUser?.uid, hasLocationPermission, hasBackgroundLocationPermission) {
        Log.d("DashboardScreen", "🔍 LaunchedEffect - User: ${currentUser?.uid != null}, Location: $hasLocationPermission, Background: $hasBackgroundLocationPermission")
        if (currentUser != null && hasLocationPermission && hasBackgroundLocationPermission) {
            Log.d("DashboardScreen", "✅ Todas las condiciones cumplidas, iniciando servicio")
            // Verificar si ya está ignorando optimizaciones de batería
            if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                // Solicitar permiso para ignorar optimizaciones de batería
                Log.d("DashboardScreen", "🔋 Solicitando ignorar optimizaciones de batería")
                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
            }
            
            // Iniciar detección automática
            Log.d("DashboardScreen", "▶️ Llamando a startTripDetectionAutomatically")
            tripDetectionViewModel.startTripDetectionAutomatically(context)
        } else {
            Log.d("DashboardScreen", "⏸️ Condiciones no cumplidas - User: ${currentUser != null}, Location: $hasLocationPermission, Background: $hasBackgroundLocationPermission")
        }
    }
    var isLoading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var mapLoaded by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    
    val uiState by viewModel.uiState.collectAsState()
    
    // Emisiones de CO2 del día en kg (sumatoria de distancia_km * factor_g/km / 1000)
    val todayEmissionsKg = remember(uiState.todayRecords) {
        (uiState.todayRecords).sumOf { record ->
            val distanceKm = record.distance ?: 0.0
            val factorGPerKm = record.emissionFactor ?: record.transportType?.emissionFactor ?: 0.0
            distanceKm * factorGPerKm
        } / 1000.0
    }
    
    // Formatear emisiones a la unidad más apropiada (g, kg, t)
    val (emissionsText, emissionsUnit) = remember(todayEmissionsKg) {
        when {
            todayEmissionsKg >= 1000.0 -> {
                // Toneladas (t) - para valores >= 1000 kg
                val tons = todayEmissionsKg / 1000.0
                Pair(String.format("%.2f", tons), "t CO₂")
            }
            todayEmissionsKg >= 1.0 -> {
                // Kilogramos (kg) - para valores entre 1 kg y 1000 kg
                Pair(String.format("%.2f", todayEmissionsKg), "kg CO₂")
            }
            todayEmissionsKg >= 0.001 -> {
                // Gramos (g) - para valores entre 1 g y 1 kg
                val grams = todayEmissionsKg * 1000.0
                Pair(String.format("%.1f", grams), "g CO₂")
            }
            else -> {
                // Menos de 1 gramo - mostrar en gramos con más decimales
                val grams = todayEmissionsKg * 1000.0
                Pair(String.format("%.2f", grams), "g CO₂")
            }
        }
    }
    
    // Cargar los registros del día al iniciar
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { userId ->
            viewModel.loadTodayRecord(userId)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(4.6097, -74.0817), 10f)
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                isLoading = true
                errorText = null
                
                val fused = LocationServices.getFusedLocationProviderClient(context)
                val loc = fused.safeCurrentLocation()
                
                if (loc != null) {
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                } else {
                    // Si no se puede obtener la ubicación, usar una ubicación por defecto
                    val defaultLocation = LatLng(4.6097, -74.0817) // Bogotá, Colombia
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(defaultLocation, 10f)
                }
            } catch (e: SecurityException) {
                errorText = "Permiso de ubicación no concedido"
            } catch (e: Exception) {
                // Si hay error, usar ubicación por defecto
                val defaultLocation = LatLng(4.6097, -74.0817) // Bogotá, Colombia
                cameraPositionState.position = CameraPosition.fromLatLngZoom(defaultLocation, 10f)
            } finally {
                isLoading = false
            }
        } else {
            errorText = "Se requieren permisos de ubicación"
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Mapa de fondo
        if (hasLocationPermission && errorText == null) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false, 
                    myLocationButtonEnabled = false,
                    compassEnabled = true,
                    zoomGesturesEnabled = true,
                    scrollGesturesEnabled = true,
                    tiltGesturesEnabled = true,
                    rotationGesturesEnabled = true
                ),
                onMapLoaded = {
                    mapLoaded = true
                    isLoading = false
                }
            ) {
                // No markers; only show user's location via blue dot
            }
        } else {
            // Show error message or permission request
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                        )
                    } else if (!hasLocationPermission) {
                        Text(
                            text = "Se requieren permisos de ubicación para mostrar el mapa",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }
        }

        // Contenido superpuesto
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Contenido principal con fondo semi-transparente
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header clickeable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .semantics {
                                role = Role.Button
                                contentDescription = if (isExpanded) "Contraer resumen EcoTracker" else "Expandir resumen EcoTracker"
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EcoTracker",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                        
                        Spacer(Modifier.width(8.dp))
                        
                        val rotation by animateFloatAsState(
                            targetValue = if (isExpanded) 180f else 0f,
                            animationSpec = tween(durationMillis = 300),
                            label = "rotation"
                        )
                        
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Contraer" else "Expandir",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                        )
                    }
                    
                    // Contenido expandible
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Tu huella de carbono hoy",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { heading() },
                                textAlign = TextAlign.Center
                            )

                            // Mostrar mensaje si no hay datos o estadísticas si hay datos
                            if (uiState.todayRecords.isEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text(
                                        text = "No hay datos disponibles",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                // Estadísticas rápidas
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = emissionsText,
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = emissionsUnit,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${uiState.todayRecords.size}",
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Viajes",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            
            // Barra de navegación inferior
            BottomNavigationBar(
                onProfileClick = onProfileClick,
                onRegistryClick = onRegistryClick,
                onFriendshipClick = onFriendshipClick
            )
        }
        
        // Indicador de velocidad en la parte inferior izquierda
        val tripDetectionState by tripDetectionViewModel.uiState.collectAsState()
        val speedKmh = tripDetectionState.currentSpeed * 3.6f // Convertir m/s a km/h
        
        // Mostrar siempre el indicador si hay permisos (incluso si está en 0 para ver que funciona)
        if (hasLocationPermission) {
            SpeedIndicator(
                speedKmh = speedKmh,
                isTracking = tripDetectionState.isTracking,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 80.dp) // Más arriba para no sobreponerse con la barra
            )
        }

        // Indicador de carga
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Cargando mapa...",
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun com.google.android.gms.location.FusedLocationProviderClient.safeCurrentLocation() =
    suspendCancellableCoroutine<android.location.Location?> { cont ->
        try {
            val cts = com.google.android.gms.tasks.CancellationTokenSource()
            this.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (!cont.isCompleted) {
                        cont.resume(loc)
                    }
                }
                .addOnFailureListener { exception ->
                    if (!cont.isCompleted) {
                        cont.resume(null)
                    }
                }
            cont.invokeOnCancellation { 
                try {
                    cts.cancel()
                } catch (e: Exception) {
                    // Ignorar errores de cancelación
                }
            }
        } catch (e: Exception) {
            if (!cont.isCompleted) {
                cont.resume(null)
            }
        }
    }

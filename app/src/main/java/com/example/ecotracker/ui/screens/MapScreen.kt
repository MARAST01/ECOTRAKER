package com.example.ecotracker.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

@Composable
fun MapScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        val context = LocalContext.current
        var hasLocationPermission by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(true) }
        var errorText by remember { mutableStateOf<String?>(null) }

        val cameraPositionState = rememberCameraPositionState()

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            hasLocationPermission = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }

        LaunchedEffect(Unit) {
            val fineGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            hasLocationPermission = fineGranted || coarseGranted
            if (!hasLocationPermission) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        LaunchedEffect(hasLocationPermission) {
            if (hasLocationPermission) {
                val fused = LocationServices.getFusedLocationProviderClient(context)
                try {
                    isLoading = true
                    val loc = fused.safeCurrentLocation()
                    if (loc != null) {
                        val latLng = LatLng(loc.latitude, loc.longitude)
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                    } else {
                        errorText = "No se pudo obtener la ubicación actual"
                    }
                } catch (_: SecurityException) {
                    errorText = "Permiso de ubicación no concedido"
                } finally {
                    isLoading = false
                }
            } else {
                errorText = "Se requieren permisos de ubicación"
            }
        }

        Box(Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true)
            ) {
                // No markers; only show user's location via blue dot
            }

            // Sign out button top-left
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopStart
            ) {
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.onSecondary)
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorText?.let { msg ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Text(msg, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun com.google.android.gms.location.FusedLocationProviderClient.safeCurrentLocation() =
    suspendCancellableCoroutine<android.location.Location?> { cont ->
        val cts = com.google.android.gms.tasks.CancellationTokenSource()
        this.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (!cont.isCompleted) cont.resume(loc)
            }
            .addOnFailureListener { _ ->
                if (!cont.isCompleted) cont.resume(null)
            }
        cont.invokeOnCancellation { cts.cancel() }
    }

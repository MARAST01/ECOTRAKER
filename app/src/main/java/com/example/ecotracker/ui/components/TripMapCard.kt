package com.example.ecotracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecotracker.data.model.LocationPoint
import com.example.ecotracker.data.model.TransportRecord
import com.example.ecotracker.data.model.TransportType
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TripMapCard(
    record: TransportRecord,
    isExpanded: Boolean = false,
    onExpandToggle: () -> Unit,
    onConfirmTrip: ((TransportType) -> Unit)? = null,
    onDismissTrip: (() -> Unit)? = null
) {
    val routePoints = record.routePoints ?: emptyList()
    val hasRoute = routePoints.isNotEmpty()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasRoute) Modifier.clickable { onExpandToggle() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (record.isAutoDetected && !record.isConfirmed) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header del card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = record.transportType?.icon ?: if (record.isAutoDetected) "📍" else "🚗",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (record.isAutoDetected && !record.isConfirmed) {
                                "Trayecto Detectado"
                            } else {
                                record.transportType?.displayName ?: "Transporte"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Registrado a las ${record.hour ?: "N/A"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        record.distance?.let { distance ->
                            Spacer(Modifier.height(4.dp))
                            val (formattedDistance, distanceUnit) = when {
                                distance >= 1000000.0 -> {
                                    val millions = distance / 1000000.0
                                    Pair(String.format("%.2f", millions), "m km")
                                }
                                distance >= 1000.0 -> {
                                    val thousands = distance / 1000.0
                                    Pair(String.format("%.2f", thousands), "k km")
                                }
                                else -> {
                                    Pair(String.format("%.2f", distance), "km")
                                }
                            }
                            Text(
                                text = "Distancia: $formattedDistance $distanceUnit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Mostrar duración y velocidad si están disponibles
                        record.duration?.let { duration ->
                            val minutes = (duration / 60000).toInt()
                            val seconds = ((duration % 60000) / 1000).toInt()
                            Text(
                                text = "Duración: ${minutes}m ${seconds}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        record.averageSpeed?.let { speed ->
                            Text(
                                text = "Velocidad promedio: ${String.format("%.1f", speed)} km/h",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                if (hasRoute) {
                    IconButton(onClick = onExpandToggle) {
                        Text(
                            text = if (isExpanded) "▼" else "▶",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                } else {
                    if (record.isConfirmed) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Minimapa expandible
            if (isExpanded && hasRoute) {
                Spacer(Modifier.height(12.dp))
                TripMinimap(routePoints = routePoints)
                
                // Botones de confirmación si es un trayecto detectado automáticamente
                if (record.isAutoDetected && !record.isConfirmed && onConfirmTrip != null) {
                    Spacer(Modifier.height(12.dp))
                    TransportTypeSelector(
                        onTransportSelected = onConfirmTrip,
                        onDismiss = onDismissTrip
                    )
                }
            }
        }
    }
}

@Composable
fun TripMinimap(routePoints: List<LocationPoint>) {
    if (routePoints.isEmpty()) return
    
    // Calcular el centro y los límites del mapa
    val centerLat = routePoints.map { it.latitude }.average()
    val centerLng = routePoints.map { it.longitude }.average()
    val center = LatLng(centerLat, centerLng)
    
    // Calcular el zoom apropiado basado en la extensión de los puntos
    val latRange = routePoints.maxOf { it.latitude } - routePoints.minOf { it.latitude }
    val lngRange = routePoints.maxOf { it.longitude } - routePoints.minOf { it.longitude }
    val maxRange = maxOf(latRange, lngRange)
    val zoom = when {
        maxRange > 0.1 -> 10f
        maxRange > 0.05 -> 12f
        maxRange > 0.01 -> 14f
        else -> 16f
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, zoom)
    }
    
    // Convertir puntos a LatLng para la polilínea
    val routeLatLngs = routePoints.map { LatLng(it.latitude, it.longitude) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = false
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                zoomGesturesEnabled = true,
                scrollGesturesEnabled = true
            )
        ) {
            // Dibujar la ruta
            if (routeLatLngs.size >= 2) {
                Polyline(
                    points = routeLatLngs,
                    color = MaterialTheme.colorScheme.primary,
                    width = 8f
                )
            }
            
            // Marcador de inicio
            routePoints.firstOrNull()?.let { start ->
                Marker(
                    state = MarkerState(position = LatLng(start.latitude, start.longitude)),
                    title = "Inicio"
                )
            }
            
            // Marcador de fin
            routePoints.lastOrNull()?.let { end ->
                Marker(
                    state = MarkerState(position = LatLng(end.latitude, end.longitude)),
                    title = "Fin"
                )
            }
        }
    }
}

@Composable
fun TransportTypeSelector(
    onTransportSelected: (TransportType) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    Column {
        Text(
            text = "Selecciona el medio de transporte:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransportType.values().forEach { transportType ->
                Button(
                    onClick = { onTransportSelected(transportType) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = transportType.icon,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = transportType.displayName,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        
        onDismiss?.let {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = it,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Descartar trayecto")
            }
        }
    }
}


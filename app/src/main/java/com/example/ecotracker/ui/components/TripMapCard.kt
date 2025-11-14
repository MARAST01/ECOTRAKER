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
                        // Mostrar información del trayecto como en la imagen
                        record.distance?.let { distance ->
                            val distanceText = if (distance >= 1.0) {
                                "${String.format("%.1f", distance)} km"
                            } else {
                                "${String.format("%.0f", distance * 1000)} m"
                            }
                            Text(
                                text = "Trayecto $distanceText",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } ?: run {
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
                        }
                        
                        // Mostrar hora de inicio y fin si están disponibles
                        val timeText = if (record.startTime != null && record.endTime != null) {
                            val startTime = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                                .format(java.util.Date(record.startTime!!))
                            val endTime = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                                .format(java.util.Date(record.endTime!!))
                            val duration = record.duration?.let { 
                                val minutes = (it / 60000).toInt()
                                "($minutes min)"
                            } ?: ""
                            "$startTime - $endTime $duration"
                        } else {
                            "Registrado a las ${record.hour ?: "N/A"}"
                        }
                        
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Mostrar velocidad si está disponible (solo si no es caminar)
                        record.averageSpeed?.let { speed ->
                            if (speed > 3.0) { // Solo mostrar velocidad si es mayor a 3 km/h (no caminar)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Velocidad: ${String.format("%.0f", speed)} km/h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                
                // Selector de transporte si es un trayecto detectado automáticamente
                // La confirmación es automática al seleccionar
                if (record.isAutoDetected && !record.isConfirmed && onConfirmTrip != null) {
                    Spacer(Modifier.height(12.dp))
                    TransportTypeSelector(
                        onTransportSelected = { transportType ->
                            // Confirmar automáticamente al seleccionar
                            onConfirmTrip(transportType)
                        },
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
    
    // Calcular los límites del mapa para incluir toda la ruta con padding
    val minLat = routePoints.minOf { it.latitude }
    val maxLat = routePoints.maxOf { it.latitude }
    val minLng = routePoints.minOf { it.longitude }
    val maxLng = routePoints.maxOf { it.longitude }
    
    // Calcular el centro
    val centerLat = (minLat + maxLat) / 2.0
    val centerLng = (minLng + maxLng) / 2.0
    val center = LatLng(centerLat, centerLng)
    
    // Calcular el zoom apropiado basado en la extensión de los puntos
    // Usar una fórmula más precisa para calcular el zoom que muestre toda la ruta
    val latRange = maxLat - minLat
    val lngRange = maxLng - minLng
    val maxRange = maxOf(latRange, lngRange)
    
    // Calcular zoom dinámico basado en el rango
    // Fórmula: zoom = log2(360 / range) - ajuste para padding
    val zoom = when {
        maxRange > 0.1 -> 11f
        maxRange > 0.05 -> 12.5f
        maxRange > 0.02 -> 14f
        maxRange > 0.01 -> 15f
        maxRange > 0.005 -> 16f
        maxRange > 0.002 -> 17f
        maxRange > 0.001 -> 17.5f
        else -> 18f
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, zoom)
    }
    
    // Convertir puntos a LatLng para la polilínea
    val routeLatLngs = routePoints.map { LatLng(it.latitude, it.longitude) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp) // Aumentado para mejor visualización del trayecto
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = false
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = false,
                zoomGesturesEnabled = true,
                scrollGesturesEnabled = true,
                tiltGesturesEnabled = false,
                rotationGesturesEnabled = false
            )
        ) {
            // Dibujar la ruta con línea más gruesa y color más visible (como en la imagen)
            if (routeLatLngs.size >= 2) {
                Polyline(
                    points = routeLatLngs,
                    color = MaterialTheme.colorScheme.primary,
                    width = 14f // Línea más gruesa para mejor visibilidad
                )
            }
            
            // Marcador de inicio (teardrop púrpura como en la imagen)
            routePoints.firstOrNull()?.let { start ->
                Marker(
                    state = MarkerState(position = LatLng(start.latitude, start.longitude)),
                    title = "Inicio",
                    snippet = "Punto de partida"
                )
            }
            
            // Marcador de fin (teardrop púrpura con círculo blanco como en la imagen)
            routePoints.lastOrNull()?.let { end ->
                Marker(
                    state = MarkerState(position = LatLng(end.latitude, end.longitude)),
                    title = "Fin",
                    snippet = "Destino"
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


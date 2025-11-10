package com.example.ecotracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ecotracker.ui.viewmodel.TransportViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RegistryScreen(
    onBack: () -> Unit
) {
    val viewModel: TransportViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser
    
    // Resumen para el rango actual (últimos currentDaysBack días)
    val totalDistanceKm = remember(uiState.paginatedRecords) {
        uiState.paginatedRecords.sumOf { it.distance ?: 0.0 }
    }
    val totalEmissionsKg = remember(uiState.paginatedRecords) {
        uiState.paginatedRecords.sumOf { record ->
            val distanceKm = record.distance ?: 0.0
            val factor = record.emissionFactor ?: record.transportType?.emissionFactor ?: 0.0
            distanceKm * factor
        } / 1000.0
    }
    
    // Formatear emisiones a la unidad más apropiada (g, kg, t)
    val (emissionsText, emissionsUnit) = remember(totalEmissionsKg) {
        when {
            totalEmissionsKg >= 1000.0 -> {
                // Toneladas (t) - para valores >= 1000 kg
                val tons = totalEmissionsKg / 1000.0
                Pair(String.format("%.2f", tons), "t CO₂")
            }
            totalEmissionsKg >= 1.0 -> {
                // Kilogramos (kg) - para valores entre 1 kg y 1000 kg
                Pair(String.format("%.2f", totalEmissionsKg), "kg CO₂")
            }
            totalEmissionsKg >= 0.001 -> {
                // Gramos (g) - para valores entre 1 g y 1 kg
                val grams = totalEmissionsKg * 1000.0
                Pair(String.format("%.1f", grams), "g CO₂")
            }
            else -> {
                // Menos de 1 gramo - mostrar en gramos con más decimales
                val grams = totalEmissionsKg * 1000.0
                Pair(String.format("%.2f", grams), "g CO₂")
            }
        }
    }
    
    // Formatear distancia con abreviaciones (k para miles, m para millones)
    val (distanceText, distanceUnit) = remember(totalDistanceKm) {
        when {
            totalDistanceKm >= 1000000.0 -> {
                // Millones (m) - para valores >= 1,000,000 km
                val millions = totalDistanceKm / 1000000.0
                Pair(String.format("%.2f", millions), "m km")
            }
            totalDistanceKm >= 1000.0 -> {
                // Miles (k) - para valores entre 1,000 y 1,000,000 km
                val thousands = totalDistanceKm / 1000.0
                Pair(String.format("%.2f", thousands), "k km")
            }
            else -> {
                // Menos de 1000 km - mostrar normalmente
                Pair(String.format("%.2f", totalDistanceKm), "km")
            }
        }
    }
    
    // Cargar los registros paginados al iniciar
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { userId ->
            viewModel.loadPaginatedRecords(userId, 15)
        }
    }
    
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("← Atrás", color = MaterialTheme.colorScheme.onSecondary)
                }
                
                Text(
                    text = "Registros",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = {
                        currentUser?.uid?.let { userId ->
                            viewModel.loadMoreRecords(userId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !uiState.isLoadingMore
                ) {
                    if (uiState.isLoadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("🔄", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Resumen
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Resumen",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Layout responsive: Row en pantallas grandes, Column en pantallas pequeñas
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Primera fila: CO₂ y Viajes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = emissionsText,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = emissionsUnit,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1
                                )
                            }
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${uiState.paginatedRecords.size}",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Viajes (Últimos ${uiState.currentDaysBack} días)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        // Segunda fila: Distancia (siempre en su propia fila para evitar desbordamiento)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = distanceText,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = distanceUnit,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Lista de registros
            Text(
                text = "Registros del día",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.paginatedRecords.isEmpty()) {
                    // Mensaje cuando no hay registros
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "📝",
                                    style = MaterialTheme.typography.headlineLarge
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No hay registros para hoy",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Comienza registrando tu primer viaje",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // Lista de transportes paginados
                    items(uiState.paginatedRecords) { record ->
                        TransportRecordCard(record = record)
                    }
                    
                    // Botón para cargar más registros
                    item {
                        if (uiState.paginatedRecords.isNotEmpty()) {
                            Button(
                                onClick = {
                                    currentUser?.uid?.let { userId ->
                                        viewModel.loadMoreRecords(userId)
                                    }
                                },
                                enabled = !uiState.isLoadingMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                if (uiState.isLoadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (uiState.isLoadingMore) "Cargando..." else "Cargar más registros",
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransportRecordCard(record: com.example.ecotracker.data.model.TransportRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.transportType?.icon ?: "🚗",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = record.transportType?.displayName ?: "Transporte",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Registrado a las ${record.hour ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 🌿 NUEVO: Mostrar distancia si existe con formato abreviado
                    record.distance?.let { distance ->
                        Spacer(Modifier.height(4.dp))
                        val (formattedDistance, distanceUnitCard) = when {
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
                            text = "Distancia: $formattedDistance $distanceUnitCard",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = "✓",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

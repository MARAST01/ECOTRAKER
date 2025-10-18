package com.example.ecotracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ecotracker.data.model.TransportType
import com.example.ecotracker.ui.viewmodel.TransportViewModel
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportSelectionScreen(
    onBack: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val viewModel: TransportViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser
    
    // Cargar el registro del día actual al iniciar
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { userId ->
            viewModel.loadTodayRecord(userId)
        }
    }
    
    // Mostrar mensaje de confirmación
    LaunchedEffect(uiState.showConfirmation) {
        if (uiState.showConfirmation) {
            kotlinx.coroutines.delay(2000)
            viewModel.dismissConfirmation()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Atrás")
            }
            Text(
                text = "Seleccionar Transporte",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(60.dp)) // Para centrar el título
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Información del día actual
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Registro de hoy",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val todayRecord = uiState.todayRecord
                if (todayRecord != null) {
                    Text(
                        text = "Ya registraste: ${todayRecord.transportType?.displayName} ${todayRecord.transportType?.icon}",
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = "Aún no has registrado tu transporte de hoy",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Título de selección
        Text(
            text = "¿Cómo te transportaste hoy?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Opciones de transporte
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(TransportType.values().toList()) { transport ->
                TransportOptionCard(
                    transport = transport,
                    isSelected = uiState.selectedTransport == transport,
                    onSelect = { viewModel.selectTransport(transport) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Botón de guardar
        Button(
            onClick = {
                currentUser?.uid?.let { userId ->
                    viewModel.saveTransportRecord(userId)
                }
            },
            enabled = uiState.selectedTransport != null && !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            } else {
                Text(
                    text = "Guardar Registro",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Botón para ir al mapa
        OutlinedButton(
            onClick = onNavigateToMap,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Ver Mapa",
                fontSize = 16.sp
            )
        }
    }
    
    // Mostrar mensajes de error
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
        ) {
            Text(
                text = error,
                color = Color.Red,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
    
    // Mostrar mensaje de éxito
    uiState.successMessage?.let { message ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
        ) {
            Text(
                text = message,
                color = Color(0xFF2E7D32),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun TransportOptionCard(
    transport: TransportType,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF4CAF50) else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = transport.icon,
                    fontSize = 32.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = transport.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.White else Color.Black
                )
            }
            
            if (isSelected) {
                Text(
                    text = "✓",
                    fontSize = 24.sp,
                    color = Color.White
                )
            }
        }
    }
}

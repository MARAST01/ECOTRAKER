package com.example.ecotracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportSelectionScreen(
    onBack: () -> Unit
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
    
    // Mostrar mensaje flotante de éxito
    LaunchedEffect(uiState.showSuccessSnackbar) {
        if (uiState.showSuccessSnackbar) {
            kotlinx.coroutines.delay(3000)
            viewModel.dismissSuccessSnackbar()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TransportType.values().forEach { transport ->
                TransportOptionCard(
                    transport = transport,
                    isSelected = uiState.selectedTransport == transport,
                    onSelect = { viewModel.selectTransport(transport) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Selector de hora
        if (uiState.selectedTransport != null) {
            TimeSelectorCard(
                selectedTime = uiState.selectedTime,
                onTimeChange = { viewModel.selectTime(it) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🧮 Campo de distancia
        OutlinedTextField(
            value = uiState.distance,
            onValueChange = { viewModel.updateDistance(it) },
            label = { Text("Distancia recorrida (km)") },
            placeholder = { Text("Ejemplo: 2.5") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        // Botón de guardar
        Button(
            onClick = {
                currentUser?.uid?.let { userId ->
                    viewModel.saveTransportRecord(userId)
                }
            },
            enabled = uiState.selectedTransport != null && uiState.selectedTime.isNotEmpty() && !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.selectedTransport != null && uiState.selectedTime.isNotEmpty()) 
                    Color(0xFF27AE60) else Color(0xFFBDC3C7)
            )
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            } else {
                Text(
                    text = if (uiState.selectedTime.isEmpty()) "Selecciona una hora primero" else "Guardar Registro",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
    
    // Modal de éxito (no ocupa toda la pantalla)
    if (uiState.showSuccessSnackbar) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccessSnackbar() },
            title = {
                Text(
                    text = "¡Registro guardado!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50)
                )
            },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.successMessage ?: "Registro guardado exitosamente",
                        fontSize = 16.sp,
                        color = Color(0xFF2C3E50)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSuccessSnackbar() }) {
                    Text("Aceptar")
                }
            }
        )
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

@Composable
fun TimeSelectorCard(
    selectedTime: String,
    onTimeChange: (String) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedHour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hora del transporte",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                }
                
                if (selectedTime.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF27AE60)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Botón principal para seleccionar hora
            Button(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTime.isNotEmpty()) Color(0xFF27AE60) else Color(0xFF3498DB)
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTime.isNotEmpty()) "Hora: $selectedTime" else "Seleccionar",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { 
                Text(
                    text = "🕐 Seleccionar hora",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50)
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Hora actual seleccionada
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFECF0F1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = String.format("%02d:%02d", selectedHour, selectedMinute),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C3E50),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Controles para hora
                    Text(
                        text = "Hora",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF34495E)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { 
                                if (selectedHour > 0) selectedHour-- 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("−", fontSize = 20.sp, color = Color.White)
                        }
                        
                        Text(
                            text = String.format("%02d", selectedHour),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C3E50),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        Button(
                            onClick = { 
                                if (selectedHour < 23) selectedHour++ 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+", fontSize = 20.sp, color = Color.White)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Controles para minutos
                    Text(
                        text = "Minutos",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF34495E)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { 
                                if (selectedMinute > 0) selectedMinute-- 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("−", fontSize = 20.sp, color = Color.White)
                        }
                        
                        Text(
                            text = String.format("%02d", selectedMinute),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C3E50),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        Button(
                            onClick = { 
                                if (selectedMinute < 59) selectedMinute++ 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+", fontSize = 20.sp, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val timeString = String.format("%02d:%02d", selectedHour, selectedMinute)
                        onTimeChange(timeString)
                        showTimePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirmar", color = Color.White, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF7F8C8D))
                ) {
                    Text("Cancelar", fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}

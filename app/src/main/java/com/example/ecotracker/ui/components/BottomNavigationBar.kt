package com.example.ecotracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomNavigationBar(
    onTransportClick: () -> Unit,
    onRegistryClick: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón de Transporte
            Button(
                onClick = onTransportClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Transporte",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            // Botón de Registro
            Button(
                onClick = onRegistryClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Registro",
                    color = MaterialTheme.colorScheme.onSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            // Botón de Cerrar sesión
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Salir",
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}

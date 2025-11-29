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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties

@Composable
fun BottomNavigationBar(
    onProfileClick: () -> Unit,
    onRegistryClick: () -> Unit,
    onFriendshipClick: () -> Unit
) {
    val profileFocus = FocusRequester()
    val registryFocus = FocusRequester()
    val friendshipFocus = FocusRequester()

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
            // Botón de Perfil
            Button(
                onClick = onProfileClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(profileFocus)
                    .focusProperties {
                        next = registryFocus
                        previous = friendshipFocus
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Abrir perfil"
                    }
            ) {
                Text(
                    text = "Perfil",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            // Botón de Registro
            Button(
                onClick = onRegistryClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(registryFocus)
                    .focusProperties {
                        next = friendshipFocus
                        previous = profileFocus
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Abrir registros"
                    }
            ) {
                Text(
                    text = "Registro",
                    color = MaterialTheme.colorScheme.onSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            // Botón de Amistades
            Button(
                onClick = onFriendshipClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(friendshipFocus)
                    .focusProperties {
                        next = profileFocus
                        previous = registryFocus
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Abrir amistades"
                    }
            ) {
                Text(
                    text = "Amistades",
                    color = MaterialTheme.colorScheme.onTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}

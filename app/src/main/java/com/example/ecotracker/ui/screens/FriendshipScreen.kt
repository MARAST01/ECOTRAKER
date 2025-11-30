package com.example.ecotracker.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ecotracker.data.model.FriendshipRequest
import com.example.ecotracker.data.model.FriendshipStatus
import com.example.ecotracker.data.model.UserProfile
import com.example.ecotracker.ui.viewmodel.FriendshipTab
import com.example.ecotracker.ui.viewmodel.FriendshipViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendshipScreen(
    onBack: () -> Unit
) {
    val viewModel: FriendshipViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val currentUserId = currentUser?.uid ?: ""
    
    var searchText by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    
    // Cargar amistades al iniciar
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            viewModel.loadFriendships(currentUserId)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Amistades") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar usuarios")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Barra de búsqueda (si está visible)
            if (showSearch) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { newValue ->
                                searchText = newValue
                                if (newValue.isNotEmpty()) {
                                    viewModel.searchUsers(newValue, currentUserId)
                                }
                            },
                            label = { Text("Buscar por email") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchText.isNotEmpty()) {
                                        viewModel.searchUsers(searchText, currentUserId)
                                    }
                                }
                            ),
                            singleLine = true
                        )
                        
                        // Resultados de búsqueda
                        if (uiState.searchResults.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Resultados:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            uiState.searchResults.forEach { user ->
                                UserSearchResultItem(
                                    user = user,
                                    currentUserId = currentUserId,
                                    onSendRequest = { receiverId ->
                                        viewModel.sendFriendshipRequest(currentUserId, receiverId)
                                        searchText = ""
                                        showSearch = false
                                    },
                                    onCheckStatus = { userId ->
                                        // El estado se maneja automáticamente
                                    }
                                )
                            }
                        } else if (uiState.searchQuery.isNotEmpty() && !uiState.isLoading) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No se encontraron usuarios",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Selector de pestañas
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = uiState.selectedTab == FriendshipTab.PENDING,
                    onClick = { viewModel.selectTab(FriendshipTab.PENDING) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pendientes")
                }
                SegmentedButton(
                    selected = uiState.selectedTab == FriendshipTab.SENT,
                    onClick = { viewModel.selectTab(FriendshipTab.SENT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Enviadas")
                }
                SegmentedButton(
                    selected = uiState.selectedTab == FriendshipTab.FRIENDS,
                    onClick = { viewModel.selectTab(FriendshipTab.FRIENDS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Amigos")
                }
            }
            
            // Contenido según la pestaña seleccionada
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (uiState.selectedTab) {
                    FriendshipTab.PENDING -> {
                        PendingRequestsList(
                            requests = uiState.pendingRequests,
                            currentUserId = currentUserId,
                            onAccept = { friendshipId ->
                                viewModel.acceptFriendshipRequest(friendshipId, currentUserId)
                            },
                            onReject = { friendshipId ->
                                viewModel.rejectFriendshipRequest(friendshipId, currentUserId)
                            },
                            viewModel = viewModel
                        )
                    }
                    FriendshipTab.SENT -> {
                        SentRequestsList(
                            requests = uiState.sentRequests,
                            viewModel = viewModel
                        )
                    }
                    FriendshipTab.FRIENDS -> {
                        FriendsList(
                            friendships = uiState.acceptedFriendships,
                            currentUserId = currentUserId,
                            viewModel = viewModel
                        )
                    }
                }
                
                // Indicador de carga
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                // Mensaje de error
                uiState.error?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("Cerrar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserSearchResultItem(
    user: UserProfile,
    currentUserId: String,
    onSendRequest: (String) -> Unit,
    onCheckStatus: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.fullName ?: "Usuario",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user.email ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { onSendRequest(user.uid ?: "") },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Agregar")
            }
        }
    }
}

@Composable
private fun PendingRequestsList(
    requests: List<FriendshipRequest>,
    currentUserId: String,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    viewModel: FriendshipViewModel
) {
    if (requests.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No hay solicitudes pendientes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(requests) { request ->
            var requesterProfile by remember { mutableStateOf<UserProfile?>(null) }
            val coroutineScope = rememberCoroutineScope()
            
            LaunchedEffect(request.requesterId) {
                request.requesterId?.let { userId ->
                    coroutineScope.launch {
                        requesterProfile = viewModel.getUserProfile(userId)
                    }
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = requesterProfile?.fullName ?: requesterProfile?.email ?: "Usuario: ${request.requesterId ?: "desconocido"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    requesterProfile?.email?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    request.createdAt?.let {
                        Text(
                            text = "Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { request.id?.let { onAccept(it) } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Aceptar")
                        }
                        OutlinedButton(
                            onClick = { request.id?.let { onReject(it) } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Rechazar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SentRequestsList(
    requests: List<FriendshipRequest>,
    viewModel: FriendshipViewModel
) {
    if (requests.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    "➕",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No has enviado solicitudes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(requests) { request ->
            var receiverProfile by remember { mutableStateOf<UserProfile?>(null) }
            val coroutineScope = rememberCoroutineScope()
            
            LaunchedEffect(request.receiverId) {
                request.receiverId?.let { userId ->
                    coroutineScope.launch {
                        receiverProfile = viewModel.getUserProfile(userId)
                    }
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = receiverProfile?.fullName ?: receiverProfile?.email ?: "Usuario: ${request.receiverId ?: "desconocido"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    receiverProfile?.email?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    request.createdAt?.let {
                        Text(
                            text = "Enviada: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Estado: Pendiente",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendsList(
    friendships: List<FriendshipRequest>,
    currentUserId: String,
    viewModel: FriendshipViewModel
) {
    if (friendships.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No tienes amigos aún",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(friendships) { friendship ->
            val friendId = if (friendship.requesterId == currentUserId) {
                friendship.receiverId
            } else {
                friendship.requesterId
            }
            
            var friendProfile by remember { mutableStateOf<UserProfile?>(null) }
            val coroutineScope = rememberCoroutineScope()
            
            LaunchedEffect(friendId) {
                friendId?.let { userId ->
                    coroutineScope.launch {
                        friendProfile = viewModel.getUserProfile(userId)
                    }
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friendProfile?.fullName ?: friendProfile?.email ?: "Usuario: ${friendId ?: "desconocido"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            friendProfile?.email?.let { email ->
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            friendship.updatedAt?.let {
                                Text(
                                    text = "Amigos desde: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Amigo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}


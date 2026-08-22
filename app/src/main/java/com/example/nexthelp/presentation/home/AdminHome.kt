package com.example.nexthelp.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketPriority
import com.example.nexthelp.domain.models.TicketStatus
import com.example.nexthelp.presentation.auth.AuthViewModel
import com.example.nexthelp.presentation.tickets.TicketViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHome(
    onTicketClick: (String) -> Unit,
    onLogout: () -> Unit,
    ticketViewModel: TicketViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val ticketsState by ticketViewModel.ticketsState
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Console") },
                actions = {
                    IconButton(onClick = { 
                        ticketViewModel.createTicket(
                            "System Admin", "000", "admin@nexthelp.com", "Office",
                            "Emergency Fix", "Database connection lost", "System", TicketPriority.CRITICAL
                        )
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Quick Test")
                    }
                    IconButton(onClick = { ticketViewModel.getTickets() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { 
                        authViewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search by name, ID or subject") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = MaterialTheme.shapes.medium
            )

            when (val state = ticketsState) {
                is Resource.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is Resource.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Service Unavailable", style = MaterialTheme.typography.titleMedium)
                            Text(state.message ?: "Check your connection")
                            Button(onClick = { ticketViewModel.getTickets() }) { Text("Retry") }
                        }
                    }
                }
                is Resource.Success -> {
                    val tickets = state.data?.filter {
                        it.subject.contains(searchQuery, ignoreCase = true) ||
                        it.requesterName.contains(searchQuery, ignoreCase = true) ||
                        it.ticketNumber.contains(searchQuery, ignoreCase = true)
                    } ?: emptyList()

                    if (tickets.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No tickets found")
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tickets) { ticket ->
                                AdminTicketItem(
                                    ticket = ticket,
                                    onClick = { onTicketClick(ticket.id) },
                                    onStatusChange = { newStatus ->
                                        ticketViewModel.updateStatus(ticket.id, newStatus)
                                    }
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
fun AdminTicketItem(ticket: Ticket, onClick: () -> Unit, onStatusChange: (TicketStatus) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (ticket.priority == TicketPriority.CRITICAL) 
                MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ID: ${ticket.ticketNumber}", style = MaterialTheme.typography.labelSmall)
                Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.primary) {
                    Text(ticket.status.name, modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Text(ticket.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("From: ${ticket.requesterName}", style = MaterialTheme.typography.bodySmall)
            
            Spacer(Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onStatusChange(TicketStatus.IN_PROGRESS) }, Modifier.weight(1f)) {
                    Text("Take Case", style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = { onStatusChange(TicketStatus.RESOLVED) }, Modifier.weight(1f)) {
                    Text("Resolve", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

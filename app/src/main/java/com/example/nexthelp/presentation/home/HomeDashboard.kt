package com.example.nexthelp.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
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
import com.example.nexthelp.domain.models.TicketStatus
import com.example.nexthelp.presentation.auth.AuthViewModel
import com.example.nexthelp.presentation.tickets.TicketViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    onCreateTicket: () -> Unit,
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
                title = { 
                    Column {
                        Text("NextHelp", style = MaterialTheme.typography.titleLarge)
                        Text("Support Dashboard", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        authViewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateTicket,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Ticket")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SummaryCardsSection(ticketsState)
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search your tickets...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
            }
            
            item {
                Text(
                    text = "My Tickets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            when (val state = ticketsState) {
                is Resource.Loading -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is Resource.Error -> {
                    item {
                        Text(state.message ?: "Error", color = MaterialTheme.colorScheme.error)
                    }
                }
                is Resource.Success -> {
                    val tickets = state.data?.filter { 
                        it.subject.contains(searchQuery, ignoreCase = true) || 
                        it.ticketNumber.contains(searchQuery, ignoreCase = true) 
                    } ?: emptyList()

                    if (tickets.isEmpty()) {
                        item { EmptyTicketsState() }
                    } else {
                        items(tickets, key = { it.id }) { ticket ->
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInHorizontally() + fadeIn(),
                                exit = slideOutHorizontally() + fadeOut()
                            ) {
                                UserTicketItem(ticket, onClick = { onTicketClick(ticket.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCardsSection(ticketsState: Resource<List<Ticket>>) {
    val tickets = (ticketsState as? Resource.Success)?.data ?: emptyList()
    val openCount = tickets.count { it.status == TicketStatus.OPEN }
    val progressCount = tickets.count { it.status == TicketStatus.IN_PROGRESS }
    val resolvedCount = tickets.count { it.status == TicketStatus.RESOLVED }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard(label = "OPEN", count = openCount.toString(), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.weight(1f))
        SummaryCard(label = "ACTIVE", count = progressCount.toString(), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f))
        SummaryCard(label = "DONE", count = resolvedCount.toString(), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.weight(1f))
    }
}

@Composable
fun UserTicketItem(ticket: Ticket, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${ticket.ticketNumber}", style = MaterialTheme.typography.labelSmall)
                Text(ticket.status.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(ticket.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(ticket.description, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
fun SummaryCard(label: String, count: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(text = count, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyTicketsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No tickets found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = "Create your first ticket when you need help.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

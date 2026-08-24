package com.example.nexthelp.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexthelp.core.ui.components.EmptyState
import com.example.nexthelp.core.ui.components.StatusChip
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketPriority
import com.example.nexthelp.domain.models.TicketStatus
import com.example.nexthelp.presentation.tickets.TicketViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHome(
    onTicketClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    ticketViewModel: TicketViewModel = hiltViewModel()
) {
    val ticketsState by ticketViewModel.ticketsState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Console") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { ticketViewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search by name, ID or subject") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
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
                        ErrorState(
                            message = state.message ?: "Service unavailable. Check your connection.",
                            onRetry = { ticketViewModel.refresh() }
                        )
                    }
                }
                is Resource.Success -> {
                    val tickets = state.data.orEmpty().filter {
                        it.subject.contains(searchQuery, ignoreCase = true) ||
                            it.requesterName.contains(searchQuery, ignoreCase = true) ||
                            it.ticketNumber.contains(searchQuery, ignoreCase = true) ||
                            it.requesterEmail.contains(searchQuery, ignoreCase = true)
                    }

                    if (tickets.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (searchQuery.isNotBlank()) {
                                EmptyState(
                                    icon = Icons.Default.FilterListOff,
                                    title = "No matching tickets",
                                    subtitle = "We couldn't find any tickets matching your search."
                                )
                            } else {
                                EmptyState(
                                    icon = Icons.Default.ConfirmationNumber,
                                    title = "No tickets yet",
                                    subtitle = "New tickets from users will appear here in real time."
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tickets, key = { it.id }) { ticket ->
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
    val isActive = ticket.status in setOf(
        TicketStatus.OPEN,
        TicketStatus.REOPENED,
        TicketStatus.ASSIGNED,
        TicketStatus.IN_PROGRESS,
        TicketStatus.WAITING_FOR_USER
    )

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (ticket.priority == TicketPriority.CRITICAL && isActive)
                MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ID: ${ticket.ticketNumber}", style = MaterialTheme.typography.labelSmall)
                StatusChip(ticket.status)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                ticket.subject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "From: ${ticket.requesterName}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (ticket.status) {
                    TicketStatus.OPEN, TicketStatus.REOPENED -> {
                        OutlinedButton(
                            onClick = { onStatusChange(TicketStatus.IN_PROGRESS) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Take Case", style = MaterialTheme.typography.labelSmall) }
                        FilledTonalButton(
                            onClick = { onStatusChange(TicketStatus.RESOLVED) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Resolve", style = MaterialTheme.typography.labelSmall) }
                    }

                    TicketStatus.ASSIGNED, TicketStatus.WAITING_FOR_USER -> {
                        Button(
                            onClick = { onStatusChange(TicketStatus.IN_PROGRESS) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Resume Work", style = MaterialTheme.typography.labelSmall) }
                        FilledTonalButton(
                            onClick = { onStatusChange(TicketStatus.RESOLVED) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Resolve", style = MaterialTheme.typography.labelSmall) }
                    }

                    TicketStatus.IN_PROGRESS -> Button(
                        onClick = { onStatusChange(TicketStatus.RESOLVED) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Mark Resolved", style = MaterialTheme.typography.labelSmall) }

                    else -> {}
                }
            }
        }
    }
}

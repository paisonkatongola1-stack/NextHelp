package com.example.nexthelp.presentation.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexthelp.core.ui.components.EmptyState
import com.example.nexthelp.core.ui.components.ListSkeleton
import com.example.nexthelp.core.ui.components.QuickActionCard
import com.example.nexthelp.core.ui.components.SectionHeader
import com.example.nexthelp.core.ui.components.SkeletonLine
import com.example.nexthelp.core.ui.components.StatCard
import com.example.nexthelp.core.ui.components.TicketCard
import com.example.nexthelp.core.ui.components.TicketCardSkeleton
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.core.util.TimeFormat
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.presentation.auth.AuthViewModel
import com.example.nexthelp.presentation.tickets.TicketStats
import com.example.nexthelp.presentation.tickets.TicketViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    onCreateTicket: () -> Unit,
    onTicketClick: (String) -> Unit,
    onViewAllTickets: () -> Unit,
    onOpenNotifications: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    ticketViewModel: TicketViewModel = hiltViewModel()
) {
    val ticketsState by ticketViewModel.ticketsState.collectAsStateWithLifecycle()
    val stats by ticketViewModel.stats.collectAsStateWithLifecycle()
    val isRefreshing by ticketViewModel.isRefreshing.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle(initialValue = null)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { ticketViewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        when (val state = ticketsState) {
            is Resource.Loading -> LoadingContent()
            is Resource.Error -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                GreetingHeader(
                    userName = currentUser?.fullName,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
                ErrorState(
                    message = state.message ?: "Something went wrong",
                    modifier = Modifier.weight(1f),
                    onRetry = { ticketViewModel.refresh() }
                )
            }
            is Resource.Success -> SuccessContent(
                tickets = state.data.orEmpty(),
                stats = stats,
                userName = currentUser?.fullName,
                onCreateTicket = onCreateTicket,
                onTicketClick = onTicketClick,
                onViewAllTickets = onViewAllTickets,
                onOpenNotifications = onOpenNotifications
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp)
        ) {
            Column {
                SkeletonLine(width = 180.dp, height = 22.dp)
                Spacer(Modifier.height(6.dp))
                SkeletonLine(width = 240.dp, height = 13.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { StatSkeleton(Modifier.weight(1f)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { StatSkeleton(Modifier.weight(1f)) }
        }
        ListSkeleton(itemCount = 3, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun StatSkeleton(modifier: Modifier = Modifier) {
    TicketCardSkeleton(modifier = modifier)
}

@Composable
private fun SuccessContent(
    tickets: List<Ticket>,
    stats: TicketStats,
    userName: String?,
    onCreateTicket: () -> Unit,
    onTicketClick: (String) -> Unit,
    onViewAllTickets: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        GreetingHeader(
            userName = userName,
            modifier = Modifier.padding(top = 20.dp, bottom = 20.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Open",
                value = stats.open.toString(),
                icon = Icons.Outlined.Inbox,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "In progress",
                value = stats.inProgress.toString(),
                icon = Icons.Default.Engineering,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Resolved",
                value = stats.resolved.toString(),
                icon = Icons.Default.CheckCircle,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "High priority",
                value = stats.highPriority.toString(),
                icon = Icons.Default.PriorityHigh,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader(title = "Quick actions")
        Spacer(Modifier.height(10.dp))

        var showHelpDialog by remember { mutableStateOf(false) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard(
                icon = Icons.Default.Add,
                label = "New ticket",
                onClick = onCreateTicket,
                emphasized = true,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Default.ConfirmationNumber,
                label = "My tickets",
                onClick = onViewAllTickets,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard(
                icon = Icons.Default.Notifications,
                label = "Notifications",
                onClick = onOpenNotifications,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                label = "Help center",
                onClick = { showHelpDialog = true },
                modifier = Modifier.weight(1f)
            )
        }

        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                title = { Text("Help Center", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Need a hand? Reach our support team at support@nexthelp.app and we'll get back to you within one business day."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) { Text("Got it") }
                }
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader(
            title = "Recent tickets",
            actionLabel = if (tickets.isNotEmpty()) "View all" else null,
            onAction = onViewAllTickets
        )
        Spacer(Modifier.height(10.dp))

        val recent = tickets.sortedByDescending { it.createdAt }.take(4)
        if (recent.isEmpty()) {
            EmptyState(
                icon = Icons.Default.ConfirmationNumber,
                title = "You're all caught up",
                subtitle = "Create your first ticket and we'll help you track it here.",
                ctaLabel = "Create a ticket",
                onCtaClick = onCreateTicket
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                recent.forEach { ticket ->
                    TicketCard(ticket = ticket, onClick = { onTicketClick(ticket.id) })
                }
            }
        }

        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun GreetingHeader(userName: String?, modifier: Modifier = Modifier) {
    val firstName = userName?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: "there"
    Column(modifier = modifier) {
        Text(
            text = "${TimeFormat.greeting()}, $firstName 👋",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Here's what's happening with your support requests.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

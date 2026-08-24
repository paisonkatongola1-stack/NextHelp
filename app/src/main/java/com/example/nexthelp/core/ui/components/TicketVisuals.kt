package com.example.nexthelp.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nexthelp.domain.models.TicketPriority
import com.example.nexthelp.domain.models.TicketStatus

val TicketStatus.displayLabel: String
    get() = when (this) {
        TicketStatus.OPEN -> "Open"
        TicketStatus.ASSIGNED -> "Assigned"
        TicketStatus.IN_PROGRESS -> "In Progress"
        TicketStatus.WAITING_FOR_USER -> "Waiting"
        TicketStatus.RESOLVED -> "Resolved"
        TicketStatus.CLOSED -> "Closed"
        TicketStatus.REOPENED -> "Reopened"
    }

val TicketPriority.displayLabel: String
    get() = when (this) {
        TicketPriority.LOW -> "Low"
        TicketPriority.MEDIUM -> "Medium"
        TicketPriority.HIGH -> "High"
        TicketPriority.CRITICAL -> "Critical"
    }

@Composable
fun TicketStatus.labelColor(): Color = when (this) {
    TicketStatus.OPEN -> MaterialTheme.colorScheme.primary
    TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
    TicketStatus.WAITING_FOR_USER -> MaterialTheme.colorScheme.secondary
    TicketStatus.RESOLVED, TicketStatus.CLOSED ->
        if (this == TicketStatus.RESOLVED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    TicketStatus.REOPENED -> MaterialTheme.colorScheme.error
}

@Composable
fun TicketPriority.labelColor(): Color = when (this) {
    TicketPriority.LOW -> MaterialTheme.colorScheme.outline
    TicketPriority.MEDIUM -> MaterialTheme.colorScheme.secondary
    TicketPriority.HIGH -> MaterialTheme.colorScheme.tertiary
    TicketPriority.CRITICAL -> MaterialTheme.colorScheme.error
}

@Composable
fun StatusChip(status: TicketStatus, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = status.labelColor().copy(alpha = 0.12f)
    ) {
        Text(
            text = status.displayLabel,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = status.labelColor()
        )
    }
}

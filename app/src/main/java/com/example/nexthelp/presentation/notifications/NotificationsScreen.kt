package com.example.nexthelp.presentation.notifications

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexthelp.core.ui.components.EmptyState
import com.example.nexthelp.core.ui.components.NotificationSkeleton
import com.example.nexthelp.core.ui.components.SectionHeader
import com.example.nexthelp.core.util.TimeFormat
import kotlinx.coroutines.delay

@Composable
fun NotificationsScreen(
    onNotificationClick: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Opening the inbox marks everything as seen (after a short beat so the
    // unread state is visible first).
    LaunchedEffect(Unit) {
        delay(600)
        viewModel.markAllSeen()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
        Text(
            text = if (uiState.unreadIds.isNotEmpty()) {
                "${uiState.unreadIds.size} new update" + if (uiState.unreadIds.size == 1) "" else "s"
            } else {
                "You're all caught up"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(12.dp))

        NotificationPreferencesRow(
            enabledTypes = uiState.enabledTypes,
            onToggle = { type, enabled ->
                when (type) {
                    NotificationType.STATUS_CHANGED -> viewModel.setStatusEnabled(enabled)
                    NotificationType.NEW_COMMENT -> viewModel.setCommentsEnabled(enabled)
                    NotificationType.HIGH_PRIORITY -> viewModel.setPriorityEnabled(enabled)
                    NotificationType.TICKET_RECEIVED -> Unit
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        when {
            uiState.loading && uiState.groups.isEmpty() -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(5) { NotificationSkeleton() }
                }
            }
            uiState.groups.isEmpty() -> EmptyState(
                icon = Icons.Outlined.NotificationsOff,
                title = "No new notifications",
                subtitle = "Updates about your tickets will appear here."
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                uiState.groups.forEach { (groupLabel, items) ->
                    item(key = "header-$groupLabel") {
                        SectionHeader(
                            title = groupLabel,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                        )
                    }
                    items(
                        count = items.size,
                        key = { index -> items[index].id }
                    ) { index ->
                        val notification = items[index]
                        NotificationCard(
                            notification = notification,
                            unread = notification.id in uiState.unreadIds,
                            onClick = { onNotificationClick(notification.ticketId) }
                        )
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationPreferencesRow(
    enabledTypes: Set<NotificationType>,
    onToggle: (NotificationType, Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            PrefToggle(
                label = "Status changes",
                checked = NotificationType.STATUS_CHANGED in enabledTypes,
                onCheckedChange = { onToggle(NotificationType.STATUS_CHANGED, it) }
            )
            PrefToggle(
                label = "New responses",
                checked = NotificationType.NEW_COMMENT in enabledTypes,
                onCheckedChange = { onToggle(NotificationType.NEW_COMMENT, it) }
            )
            PrefToggle(
                label = "High priority alerts",
                checked = NotificationType.HIGH_PRIORITY in enabledTypes,
                onCheckedChange = { onToggle(NotificationType.HIGH_PRIORITY, it) }
            )
        }
    }
}

@Composable
private fun PrefToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun NotificationCard(
    notification: AppNotification,
    unread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (unread) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        label = "notificationColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = notification.type.accentColor().copy(alpha = 0.15f)
            ) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text(text = notification.type.glyph(), style = MaterialTheme.typography.titleMedium)
                }
            }

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (unread) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        text = "#${notification.ticketNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "   ·   ${TimeFormat.relativeTime(notification.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationType.accentColor(): Color = when (this) {
    NotificationType.STATUS_CHANGED -> MaterialTheme.colorScheme.primary
    NotificationType.NEW_COMMENT -> MaterialTheme.colorScheme.secondary
    NotificationType.HIGH_PRIORITY -> MaterialTheme.colorScheme.error
    NotificationType.TICKET_RECEIVED -> MaterialTheme.colorScheme.tertiary
}

private fun NotificationType.glyph(): String = when (this) {
    NotificationType.STATUS_CHANGED -> "🎫"
    NotificationType.NEW_COMMENT -> "💬"
    NotificationType.HIGH_PRIORITY -> "⚠️"
    NotificationType.TICKET_RECEIVED -> "📢"
}

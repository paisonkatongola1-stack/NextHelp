package com.example.nexthelp.presentation.notifications

import com.example.nexthelp.core.ui.components.displayLabel
import com.example.nexthelp.core.util.TimeFormat
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketComment
import com.example.nexthelp.domain.models.TicketPriority
import com.example.nexthelp.domain.models.TicketStatus

enum class NotificationType {
    STATUS_CHANGED,
    NEW_COMMENT,
    HIGH_PRIORITY,
    TICKET_RECEIVED;

    val displayLabel: String
        get() = when (this) {
            STATUS_CHANGED -> "Ticket updated"
            NEW_COMMENT -> "New response"
            HIGH_PRIORITY -> "High priority"
            TICKET_RECEIVED -> "Ticket received"
        }
}

data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long,
    val ticketId: String,
    val ticketNumber: String
)

object NotificationFactory {

    private val activeStatuses = setOf(
        TicketStatus.ASSIGNED,
        TicketStatus.IN_PROGRESS,
        TicketStatus.WAITING_FOR_USER,
        TicketStatus.REOPENED
    )

    /**
     * Derives a feed of notifications from the user's tickets. Pure function so it is
     * easy to reason about and test.
     *
     * [latestComments] maps ticket id -> most recent comment (from the comments
     * collection-group stream); only comments newer than [COMMENT_FRESHNESS_MS] count.
     */
    fun build(
        tickets: List<Ticket>,
        currentUserName: String?,
        enabledTypes: Set<NotificationType>,
        latestComments: Map<String, TicketComment> = emptyMap(),
        now: Long = System.currentTimeMillis(),
        limit: Int = 30
    ): List<AppNotification> {
        val notifications = mutableListOf<AppNotification>()

        for (ticket in tickets) {
            if (enabledTypes.contains(NotificationType.TICKET_RECEIVED) &&
                now - ticket.createdAt < ONE_DAY_MS
            ) {
                notifications += AppNotification(
                    id = "${ticket.id}-received",
                    type = NotificationType.TICKET_RECEIVED,
                    title = "We received your ticket",
                    message = "#${ticket.ticketNumber} · ${ticket.subject}",
                    timestamp = ticket.createdAt,
                    ticketId = ticket.id,
                    ticketNumber = ticket.ticketNumber
                )
            }

            if (enabledTypes.contains(NotificationType.STATUS_CHANGED) &&
                ticket.status != TicketStatus.OPEN
            ) {
                notifications += AppNotification(
                    id = "${ticket.id}-status-${ticket.status}-${ticket.updatedAt}",
                    type = NotificationType.STATUS_CHANGED,
                    title = statusChangeTitle(ticket),
                    message = "#${ticket.ticketNumber} · ${ticket.subject}",
                    timestamp = maxOf(ticket.updatedAt, ticket.createdAt),
                    ticketId = ticket.id,
                    ticketNumber = ticket.ticketNumber
                )
            }

            val lastComment = latestComments[ticket.id]
                ?.takeIf { now - it.timestamp < COMMENT_FRESHNESS_MS }
            if (enabledTypes.contains(NotificationType.NEW_COMMENT) && lastComment != null) {
                val isOwnComment = currentUserName != null &&
                    lastComment.authorName.equals(currentUserName, ignoreCase = true)
                if (!isOwnComment) {
                    notifications += AppNotification(
                        id = "${ticket.id}-comment-${lastComment.id}",
                        type = NotificationType.NEW_COMMENT,
                        title = "${lastComment.authorName} responded",
                        message = lastComment.content.take(120),
                        timestamp = lastComment.timestamp,
                        ticketId = ticket.id,
                        ticketNumber = ticket.ticketNumber
                    )
                }
            }

            if (enabledTypes.contains(NotificationType.HIGH_PRIORITY) &&
                ticket.priority in HIGH_PRIORITY_LEVELS &&
                ticket.status !in TERMINAL_STATUSES
            ) {
                notifications += AppNotification(
                    id = "${ticket.id}-priority",
                    type = NotificationType.HIGH_PRIORITY,
                    title = "${ticket.priority.displayLabel} priority ticket needs attention",
                    message = "#${ticket.ticketNumber} · ${ticket.subject}",
                    timestamp = maxOf(ticket.updatedAt, ticket.createdAt),
                    ticketId = ticket.id,
                    ticketNumber = ticket.ticketNumber
                )
            }
        }

        return notifications
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    fun groupByDay(notifications: List<AppNotification>, now: Long = System.currentTimeMillis()): List<Pair<String, List<AppNotification>>> {
        return notifications
            .groupBy { TimeFormat.dayGroupLabel(it.timestamp, now) }
            .toList()
            .sortedWith(
                compareByDescending<Pair<String, List<AppNotification>>> { group ->
                    when (group.first) {
                        "Today" -> 3
                        "Yesterday" -> 2
                        "This week" -> 1
                        else -> 0
                    }
                }.thenByDescending { it.second.firstOrNull()?.timestamp ?: 0L }
            )
    }

    private fun statusChangeTitle(ticket: Ticket): String = when (ticket.status) {
        TicketStatus.RESOLVED -> "Your ticket was resolved"
        TicketStatus.CLOSED -> "Your ticket was closed"
        TicketStatus.IN_PROGRESS -> "Work started on your ticket"
        TicketStatus.ASSIGNED -> "An agent picked up your ticket"
        TicketStatus.WAITING_FOR_USER -> "We need more information from you"
        TicketStatus.REOPENED -> "Your ticket was reopened"
        else -> "Ticket updated"
    }

    private val HIGH_PRIORITY_LEVELS = setOf(TicketPriority.HIGH, TicketPriority.CRITICAL)

    private val TERMINAL_STATUSES = setOf(TicketStatus.RESOLVED, TicketStatus.CLOSED)

    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    private const val COMMENT_FRESHNESS_MS = 3L * 24 * 60 * 60 * 1000
}

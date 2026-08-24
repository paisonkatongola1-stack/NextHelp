package com.example.nexthelp.domain.models

import androidx.annotation.Keep

enum class TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class TicketStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    WAITING_FOR_USER,
    RESOLVED,
    CLOSED,
    REOPENED
}

@Keep
data class Ticket(
    val id: String = "",
    val ticketNumber: String = "",
    val creatorId: String = "",
    val requesterName: String = "",
    val requesterPhone: String = "",
    val requesterEmail: String = "",
    val requesterLocation: String = "",
    val subject: String = "",
    val description: String = "",
    val category: String = "",
    val priority: TicketPriority = TicketPriority.LOW,
    val status: TicketStatus = TicketStatus.OPEN,
    val assignedAgentId: String? = null,
    val assignedAgentName: String? = null,
    val teamId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val closedAt: Long? = null
) {
    // Empty constructor for Firestore
    constructor() : this("", "", "", "", "", "", "", "", "", "", TicketPriority.LOW, TicketStatus.OPEN)
}

@Keep
data class TicketComment(
    val id: String = "",
    val authorId: String? = null,
    val authorName: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor() : this("", null, "", "", null, 0L)
}

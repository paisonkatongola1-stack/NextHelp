package com.example.nexthelp.domain.models

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
    val teamId: String? = null,
    val comments: List<TicketComment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val closedAt: Long? = null
) {
    // Empty constructor for Firestore
    constructor() : this("", "", "", "", "", "", "", "", "", "", TicketPriority.LOW, TicketStatus.OPEN)
}

data class TicketComment(
    val id: String = "",
    val authorName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", 0L)
}

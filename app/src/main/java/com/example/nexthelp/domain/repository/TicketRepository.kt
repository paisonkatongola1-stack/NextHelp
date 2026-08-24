package com.example.nexthelp.domain.repository

import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketComment
import com.example.nexthelp.domain.models.User
import kotlinx.coroutines.flow.Flow

interface TicketRepository {

    /**
     * Real-time subscription to the newest [pageSize] tickets visible to the
     * current user (their own tickets, or all tickets for agents).
     */
    fun getTickets(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<Resource<List<Ticket>>>

    /**
     * One-shot fetch of the next page of tickets, ordered by createdAt descending,
     * starting strictly after [cursor] (the oldest ticket currently loaded).
     */
    suspend fun getTicketsAfter(cursor: Ticket, pageSize: Int = DEFAULT_PAGE_SIZE): Resource<List<Ticket>>

    fun getTicketById(id: String): Flow<Resource<Ticket>>

    /** Real-time comments for a single ticket, oldest first. */
    fun getComments(ticketId: String): Flow<Resource<List<TicketComment>>>

    /**
     * Real-time stream of the most recent comment per ticket across all visible
     * tickets since [sinceMs], keyed by ticket id. Backed by a collection-group
     * query so it scales without one listener per ticket.
     */
    fun getRecentComments(sinceMs: Long): Flow<Map<String, TicketComment>>

    suspend fun createTicket(ticket: Ticket): Resource<Unit>
    suspend fun updateTicketStatus(ticketId: String, status: String): Resource<Unit>
    suspend fun addComment(ticketId: String, comment: TicketComment): Resource<Unit>

    /**
     * Assigns [agentId] to a ticket (or unassigns when null). The agent's name is
     * denormalized onto the ticket so requesters can see it without extra reads.
     */
    suspend fun assignTicket(
        ticketId: String,
        agentId: String?,
        agentName: String?
    ): Resource<Unit>

    /** Real-time list of users allowed to handle tickets (agents and up). */
    fun getSupportAgents(): Flow<Resource<List<User>>>

    companion object {
        const val DEFAULT_PAGE_SIZE = 25

        /** How far back the in-app notification feed looks for new comments. */
        const val RECENT_COMMENTS_WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}

package com.example.nexthelp.domain.repository

import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketComment
import kotlinx.coroutines.flow.Flow

interface TicketRepository {
    fun getTickets(): Flow<Resource<List<Ticket>>>
    fun getTicketById(id: String): Flow<Resource<Ticket>>
    suspend fun createTicket(ticket: Ticket): Resource<Unit>
    suspend fun updateTicketStatus(ticketId: String, status: String): Resource<Unit>
    suspend fun addComment(ticketId: String, comment: TicketComment): Resource<Unit>
}

package com.example.nexthelp.fake

import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketComment
import com.example.nexthelp.domain.models.User
import com.example.nexthelp.domain.repository.AuthRepository
import com.example.nexthelp.domain.repository.TicketRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTicketRepository : TicketRepository {

    val ticketsFlow = MutableStateFlow<Resource<List<Ticket>>>(Resource.Success(emptyList()))
    val commentsFlow = MutableStateFlow<Resource<List<TicketComment>>>(Resource.Success(emptyList()))
    val recentCommentsFlow = MutableStateFlow<Map<String, TicketComment>>(emptyMap())

    /** Pages returned by successive [getTicketsAfter] calls; empty page = end of list. */
    val olderPages = mutableListOf<List<Ticket>>()
    private var pageRequestCount = 0

    val requestedCursors = mutableListOf<Long>()
    val createdTickets = mutableListOf<Ticket>()
    val statusUpdates = mutableListOf<Pair<String, String>>()
    val addedComments = mutableListOf<Pair<String, TicketComment>>()

    fun emitTickets(tickets: List<Ticket>) {
        ticketsFlow.value = Resource.Success(tickets)
    }

    override fun getTickets(pageSize: Int): Flow<Resource<List<Ticket>>> = ticketsFlow

    override suspend fun getTicketsAfter(
        cursor: Ticket,
        pageSize: Int
    ): Resource<List<Ticket>> {
        requestedCursors += cursor.createdAt
        val page = olderPages.getOrNull(pageRequestCount) ?: emptyList()
        pageRequestCount++
        return Resource.Success(page)
    }

    override fun getTicketById(id: String): Flow<Resource<Ticket>> =
        MutableStateFlow(Resource.Loading())

    override fun getComments(ticketId: String): Flow<Resource<List<TicketComment>>> = commentsFlow

    override fun getRecentComments(sinceMs: Long): Flow<Map<String, TicketComment>> =
        recentCommentsFlow

    override suspend fun createTicket(ticket: Ticket): Resource<Unit> {
        createdTickets += ticket
        return Resource.Success(Unit)
    }

    override suspend fun updateTicketStatus(ticketId: String, status: String): Resource<Unit> {
        statusUpdates += ticketId to status
        return Resource.Success(Unit)
    }

    override suspend fun addComment(ticketId: String, comment: TicketComment): Resource<Unit> {
        addedComments += ticketId to comment
        return Resource.Success(Unit)
    }
}

class FakeAuthRepository : AuthRepository {

    val userFlow = MutableStateFlow<User?>(null)

    fun setUser(user: User?) {
        userFlow.value = user
    }

    override val currentUser: Flow<User?> = userFlow

    override suspend fun loginWithEmail(email: String, password: String): Resource<User> =
        Resource.Error("Not implemented in fake")

    override suspend fun registerWithEmail(
        email: String,
        fullName: String,
        password: String
    ): Resource<User> = Resource.Error("Not implemented in fake")

    override suspend fun loginWithGoogle(idToken: String): Resource<User> =
        Resource.Error("Not implemented in fake")

    override suspend fun sendPasswordResetEmail(email: String): Resource<Unit> =
        Resource.Error("Not implemented in fake")

    override suspend fun updateDisplayName(fullName: String): Resource<Unit> =
        Resource.Error("Not implemented in fake")

    override suspend fun updateProfileDetails(
        bio: String,
        phoneNumber: String,
        location: String
    ): Resource<Unit> = Resource.Error("Not implemented in fake")

    override suspend fun logout() = Unit
}

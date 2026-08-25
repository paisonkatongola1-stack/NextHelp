package com.example.nexthelp.data.repository

import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.data.session.SessionManager
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketComment
import com.example.nexthelp.domain.models.TicketStatus
import com.example.nexthelp.domain.models.User
import com.example.nexthelp.domain.models.canHandleTickets
import com.example.nexthelp.domain.models.UserRole
import com.example.nexthelp.domain.repository.TicketRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager
) : TicketRepository {

    private fun baseTicketQuery(): Query? {
        val session = sessionManager.currentUser.value ?: return null
        val query = firestore.collection(TICKETS)
        return if (session.canHandleTickets) query else query.whereEqualTo(CREATOR_ID, session.id)
    }

    private fun ownTicketsQuery(): Query? {
        val session = sessionManager.currentUser.value ?: return null
        return firestore.collection(TICKETS).whereEqualTo(CREATOR_ID, session.id)
    }

    override fun getMyTickets(pageSize: Int): Flow<Resource<List<Ticket>>> = callbackFlow {
        trySend(Resource.Loading())

        val query = ownTicketsQuery()
        if (query == null) {
            trySend(Resource.Error("You are signed out. Please log in again."))
            awaitClose { }
            return@callbackFlow
        }

        val listener = query
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Failed to fetch tickets"))
                    return@addSnapshotListener
                }
                val tickets = snapshot?.toObjects(Ticket::class.java) ?: emptyList()
                trySend(Resource.Success(tickets))
            }

        awaitClose { listener.remove() }
    }

    override fun getTickets(pageSize: Int): Flow<Resource<List<Ticket>>> = callbackFlow {
        trySend(Resource.Loading())

        val query = baseTicketQuery()
        if (query == null) {
            trySend(Resource.Error("You are signed out. Please log in again."))
            awaitClose { }
            return@callbackFlow
        }

        val listener = query
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Failed to fetch tickets"))
                    return@addSnapshotListener
                }
                val tickets = snapshot?.toObjects(Ticket::class.java) ?: emptyList()
                trySend(Resource.Success(tickets))
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getTicketsAfter(
        cursor: Ticket,
        pageSize: Int
    ): Resource<List<Ticket>> = try {
        val query = baseTicketQuery()
        if (query == null) {
            Resource.Error("You are signed out. Please log in again.")
        } else {
            val snapshot = query
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .startAfter(cursor.createdAt)
                .limit(pageSize.toLong())
                .get()
                .await()
            Resource.Success(snapshot.toObjects(Ticket::class.java))
        }
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to load more tickets")
    }

    override fun getTicketById(id: String): Flow<Resource<Ticket>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = firestore.collection(TICKETS).document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Failed to load ticket"))
                    return@addSnapshotListener
                }
                val ticket = snapshot?.toObject(Ticket::class.java)
                if (ticket != null) {
                    trySend(Resource.Success(ticket))
                } else {
                    trySend(Resource.Error("Ticket not found"))
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getComments(ticketId: String): Flow<Resource<List<TicketComment>>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = firestore.collection(TICKETS).document(ticketId)
            .collection(COMMENTS)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Failed to load comments"))
                    return@addSnapshotListener
                }
                trySend(Resource.Success(snapshot?.toObjects(TicketComment::class.java) ?: emptyList()))
            }
        awaitClose { listener.remove() }
    }

    override fun getRecentComments(sinceMs: Long): Flow<Map<String, TicketComment>> = callbackFlow {
        trySend(emptyMap())
        val listener = firestore.collectionGroup(COMMENTS)
            .whereGreaterThan("timestamp", sinceMs)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) {
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                // Ascending timestamp order means the last hit per ticket is its latest comment.
                val latestByTicket = linkedMapOf<String, TicketComment>()
                for (doc in snapshot.documents) {
                    val ticketId = doc.reference.parent.parent?.id ?: continue
                    doc.toObject(TicketComment::class.java)?.let { latestByTicket[ticketId] = it }
                }
                trySend(latestByTicket)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun createTicket(ticket: Ticket): Resource<Unit> {
        return try {
            firestore.collection(TICKETS).document(ticket.id).set(ticket).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to create ticket")
        }
    }

    override suspend fun updateTicketStatus(ticketId: String, status: String): Resource<Unit> {
        return try {
            firestore.collection(TICKETS).document(ticketId)
                .update(
                    mapOf(
                        "status" to status,
                        "updatedAt" to System.currentTimeMillis(),
                        // Lets the push backend skip notifying whoever made the change.
                        "updatedBy" to sessionManager.currentUser.value?.id
                    )
                )
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to update status")
        }
    }

    override suspend fun addComment(ticketId: String, comment: TicketComment): Resource<Unit> {
        return try {
            val ticketRef = firestore.collection(TICKETS).document(ticketId)
            val commentRef = ticketRef.collection(COMMENTS)
                .document(comment.id.ifBlank { UUID.randomUUID().toString() })
            val stored = comment.copy(
                id = commentRef.id,
                timestamp = comment.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
            firestore.runBatch { batch ->
                batch.set(commentRef, stored)
                batch.update(ticketRef, mapOf("updatedAt" to System.currentTimeMillis()))
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to add comment")
        }
    }

    override suspend fun assignTicket(
        ticketId: String,
        agentId: String?,
        agentName: String?
    ): Resource<Unit> {
        return try {
            val updates = mutableMapOf<String, Any?>(
                ASSIGNED_AGENT_ID to agentId,
                ASSIGNED_AGENT_NAME to agentName,
                "updatedAt" to System.currentTimeMillis(),
                // Lets the push backend skip notifying whoever made the change.
                "updatedBy" to sessionManager.currentUser.value?.id
            )
            // Keep status consistent with the workflow: assigning an untouched
            // ticket moves it forward; unassigning an ASSIGNED ticket reopens it.
            val doc = firestore.collection(TICKETS).document(ticketId).get().await()
            val currentStatus = doc.getString("status")
            if (agentId != null && currentStatus in listOf(TicketStatus.OPEN.name, TicketStatus.REOPENED.name)) {
                updates["status"] = TicketStatus.ASSIGNED.name
            } else if (agentId == null && currentStatus == TicketStatus.ASSIGNED.name) {
                updates["status"] = TicketStatus.OPEN.name
            }
            firestore.collection(TICKETS).document(ticketId).update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to assign ticket")
        }
    }

    override fun getSupportAgents(): Flow<Resource<List<User>>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = firestore.collection(USERS)
            .whereIn("role", listOf(UserRole.SUPPORT_AGENT.name, UserRole.SUPPORT_MANAGER.name, UserRole.ADMIN.name))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Failed to load agents"))
                    return@addSnapshotListener
                }
                val agents = snapshot?.toObjects(User::class.java)
                    ?.sortedBy { it.fullName.lowercase() }
                    ?: emptyList()
                trySend(Resource.Success(agents))
            }
        awaitClose { listener.remove() }
    }

    companion object {
        private const val TICKETS = "tickets"
        private const val COMMENTS = "comments"
        private const val USERS = "users"
        private const val CREATOR_ID = "creatorId"
        private const val ASSIGNED_AGENT_ID = "assignedAgentId"
        private const val ASSIGNED_AGENT_NAME = "assignedAgentName"
    }
}

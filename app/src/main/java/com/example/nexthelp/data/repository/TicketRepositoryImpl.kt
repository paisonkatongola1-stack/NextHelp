package com.example.nexthelp.data.repository

import android.util.Log
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketComment
import com.example.nexthelp.domain.repository.TicketRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TicketRepository"

@Singleton
class TicketRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : TicketRepository {

    override fun getTickets(): Flow<Resource<List<Ticket>>> = callbackFlow {
        trySend(Resource.Loading())
        
        val firebaseUser = auth.currentUser
        val email = firebaseUser?.email ?: ""
        // Hardcoded check for admin email bypass
        val isAdmin = email.equals("paisonkatongola1@gmail.com", ignoreCase = true) || 
                      (firebaseUser == null) 
        
        Log.d(TAG, "Fetching tickets. IsAdmin: $isAdmin")
        
        val userId = firebaseUser?.uid ?: "admin-dev-id"
        
        val query = if (isAdmin) {
            firestore.collection("tickets")
                .orderBy("createdAt", Query.Direction.DESCENDING)
        } else {
            firestore.collection("tickets")
                .whereEqualTo("creatorId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Snapshot error: ${error.message}")
                trySend(Resource.Error(error.localizedMessage ?: "Failed to fetch tickets"))
                return@addSnapshotListener
            }
            
            val tickets = snapshot?.toObjects(Ticket::class.java) ?: emptyList()
            Log.d(TAG, "Fetched ${tickets.size} tickets")
            trySend(Resource.Success(tickets))
        }

        awaitClose { listener.remove() }
    }

    override fun getTicketById(id: String): Flow<Resource<Ticket>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = firestore.collection("tickets").document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Error"))
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

    override suspend fun createTicket(ticket: Ticket): Resource<Unit> {
        return try {
            firestore.collection("tickets").document(ticket.id).set(ticket).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to create ticket")
        }
    }

    override suspend fun updateTicketStatus(ticketId: String, status: String): Resource<Unit> {
        return try {
            firestore.collection("tickets").document(ticketId).update("status", status).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to update status")
        }
    }

    override suspend fun addComment(ticketId: String, comment: TicketComment): Resource<Unit> {
        return try {
            firestore.collection("tickets").document(ticketId)
                .update("comments", FieldValue.arrayUnion(comment))
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to add comment")
        }
    }
}

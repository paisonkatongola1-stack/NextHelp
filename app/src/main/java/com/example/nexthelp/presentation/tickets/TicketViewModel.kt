package com.example.nexthelp.presentation.tickets

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketPriority
import com.example.nexthelp.domain.models.TicketStatus
import com.example.nexthelp.domain.models.TicketComment
import com.example.nexthelp.domain.repository.AuthRepository
import com.example.nexthelp.domain.repository.TicketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TicketViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _ticketsState = mutableStateOf<Resource<List<Ticket>>>(Resource.Loading())
    val ticketsState: State<Resource<List<Ticket>>> = _ticketsState

    private val _ticketDetailsState = mutableStateOf<Resource<Ticket>>(Resource.Loading())
    val ticketDetailsState: State<Resource<Ticket>> = _ticketDetailsState

    private val _createTicketState = mutableStateOf<Resource<Unit>?>(null)
    val createTicketState: State<Resource<Unit>?> = _createTicketState

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        getTickets()
    }

    fun getTickets() {
        viewModelScope.launch {
            ticketRepository.getTickets().collect { result ->
                _ticketsState.value = result
            }
        }
    }

    fun getTicketDetails(id: String) {
        viewModelScope.launch {
            ticketRepository.getTicketById(id).collect { result ->
                _ticketDetailsState.value = result
            }
        }
    }

    fun createTicket(
        name: String,
        phone: String,
        email: String,
        location: String,
        subject: String,
        description: String,
        category: String,
        priority: TicketPriority
    ) {
        viewModelScope.launch {
            _createTicketState.value = Resource.Loading()
            val user = authRepository.currentUser.first()
            
            // Allow creation even if user is null for "Admin Dev" bypass
            val userId = user?.id ?: "admin-dev-id"

            val ticket = Ticket(
                id = UUID.randomUUID().toString(),
                ticketNumber = "NH-${System.currentTimeMillis() % 10000}",
                creatorId = userId,
                requesterName = name,
                requesterPhone = phone,
                requesterEmail = email,
                requesterLocation = location,
                subject = subject,
                description = description,
                category = category,
                priority = priority,
                status = TicketStatus.OPEN
            )

            val result = ticketRepository.createTicket(ticket)
            _createTicketState.value = result
            if (result is Resource.Success) {
                _eventFlow.emit(UiEvent.TicketCreated)
            } else if (result is Resource.Error) {
                _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Failed to create ticket"))
            }
        }
    }

    fun addComment(ticketId: String, content: String) {
        viewModelScope.launch {
            val user = authRepository.currentUser.first()
            val authorName = user?.fullName ?: "Unknown"
            
            val comment = TicketComment(
                id = UUID.randomUUID().toString(),
                authorName = authorName,
                content = content
            )
            
            val result = ticketRepository.addComment(ticketId, comment)
            if (result is Resource.Error) {
                _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Failed to add comment"))
            }
        }
    }

    fun updateStatus(ticketId: String, status: TicketStatus) {
        viewModelScope.launch {
            val result = ticketRepository.updateTicketStatus(ticketId, status.name)
            if (result is Resource.Error) {
                _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Failed to update status"))
            }
        }
    }

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        object TicketCreated : UiEvent()
    }
}

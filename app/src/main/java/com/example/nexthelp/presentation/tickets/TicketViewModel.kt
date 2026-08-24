package com.example.nexthelp.presentation.tickets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketComment
import com.example.nexthelp.domain.models.TicketPriority
import com.example.nexthelp.domain.models.TicketStatus
import com.example.nexthelp.domain.models.User
import com.example.nexthelp.domain.repository.AuthRepository
import com.example.nexthelp.domain.repository.TicketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TicketStats(
    val total: Int = 0,
    val open: Int = 0,
    val inProgress: Int = 0,
    val resolved: Int = 0,
    val highPriority: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TicketViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val refreshKey = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)

    /** True only during an explicit user-triggered refresh (pull-to-refresh, retry). */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val ticketsState: StateFlow<Resource<List<Ticket>>> = refreshKey
        .flatMapLatest { ticketRepository.getTickets() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Resource.Loading()
        )

    // ---- Pagination ---------------------------------------------------------

    private val _olderPages = MutableStateFlow<List<Ticket>>(emptyList())
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMorePages = MutableStateFlow(true)
    val hasMorePages: StateFlow<Boolean> = _hasMorePages.asStateFlow()

    /** First (real-time) page merged with every older page loaded so far. */
    val loadedTickets: StateFlow<List<Ticket>> = combine(
        ticketsState.map { (it as? Resource.Success)?.data.orEmpty() },
        _olderPages
    ) { firstPage, older ->
        (firstPage + older)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    // ---- Search & filters -------------------------------------------------

    val searchQuery = MutableStateFlow("")
    val selectedStatuses = MutableStateFlow<Set<TicketStatus>>(emptySet())
    val selectedPriorities = MutableStateFlow<Set<TicketPriority>>(emptySet())

    val filteredTickets: StateFlow<List<Ticket>> = combine(
        loadedTickets,
        searchQuery,
        selectedStatuses,
        selectedPriorities
    ) { tickets, query, statuses, priorities ->
        tickets.filter { ticket ->
            val matchesQuery = query.isBlank() ||
                ticket.subject.contains(query, ignoreCase = true) ||
                ticket.description.contains(query, ignoreCase = true) ||
                ticket.ticketNumber.contains(query, ignoreCase = true) ||
                ticket.requesterName.contains(query, ignoreCase = true) ||
                ticket.requesterEmail.contains(query, ignoreCase = true)
            val matchesStatus = statuses.isEmpty() || ticket.status in statuses
            val matchesPriority = priorities.isEmpty() || ticket.priority in priorities
            matchesQuery && matchesStatus && matchesPriority
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    // ---- Stats ------------------------------------------------------------

    val stats: StateFlow<TicketStats> = loadedTickets
        .map { tickets ->
            TicketStats(
                total = tickets.size,
                open = tickets.count {
                    it.status == TicketStatus.OPEN || it.status == TicketStatus.REOPENED
                },
                inProgress = tickets.count {
                    it.status in setOf(
                        TicketStatus.ASSIGNED,
                        TicketStatus.IN_PROGRESS,
                        TicketStatus.WAITING_FOR_USER
                    )
                },
                resolved = tickets.count {
                    it.status == TicketStatus.RESOLVED || it.status == TicketStatus.CLOSED
                },
                highPriority = tickets.count {
                    (it.priority == TicketPriority.HIGH || it.priority == TicketPriority.CRITICAL) &&
                        it.status !in setOf(TicketStatus.RESOLVED, TicketStatus.CLOSED)
                }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TicketStats()
        )

    // ---- Agent assignment ---------------------------------------------------

    private val _supportAgents = MutableStateFlow<Resource<List<User>>>(Resource.Loading())
    val supportAgents: StateFlow<Resource<List<User>>> = _supportAgents.asStateFlow()

    /** Starts observing the list of users allowed to handle tickets. */
    fun observeSupportAgents() {
        viewModelScope.launch {
            ticketRepository.getSupportAgents().collect { state ->
                _supportAgents.value = state
            }
        }
    }

    /**
     * Assigns [agentId] to the ticket (or unassigns with null), keeping status
     * consistent: OPEN/REOPENED → ASSIGNED on assign; ASSIGNED → OPEN on unassign.
     */
    fun assignTicket(ticketId: String, agentId: String?, agentName: String?) {
        viewModelScope.launch {
            when (val result = ticketRepository.assignTicket(ticketId, agentId, agentName)) {
                is Resource.Error ->
                    _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Failed to update assignment"))
                else -> Unit
            }
        }
    }


    // ---- Details ----------------------------------------------------------

    private val detailsId = MutableStateFlow<String?>(null)

    val ticketDetailsState: StateFlow<Resource<Ticket>> = detailsId
        .filterNotNull()
        .flatMapLatest { ticketRepository.getTicketById(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Resource.Loading()
        )

    private val commentsTicketId = MutableStateFlow<String?>(null)

    val commentsState: StateFlow<Resource<List<TicketComment>>> = commentsTicketId
        .filterNotNull()
        .flatMapLatest { ticketRepository.getComments(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Resource.Loading()
        )

    fun observeComments(ticketId: String) {
        if (commentsTicketId.value != ticketId) commentsTicketId.value = ticketId
    }

    // ---- Mutations --------------------------------------------------------

    private val _createTicketState = MutableStateFlow<Resource<Unit>?>(null)
    val createTicketState: StateFlow<Resource<Unit>?> = _createTicketState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    val currentUser get() = authRepository.currentUser

    fun refresh(markAsUserAction: Boolean = true) {
        if (markAsUserAction) _isRefreshing.value = true
        refreshKey.value += 1
    }

    fun loadTicketDetails(id: String) {
        if (detailsId.value != id) detailsId.value = id
    }

    /** Loads the next page of older tickets. Safe to call repeatedly (idempotent guards). */
    fun requestMore() {
        if (_isLoadingMore.value || !_hasMorePages.value) return
        val cursor = loadedTickets.value.minByOrNull { it.createdAt } ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            when (val result = ticketRepository.getTicketsAfter(cursor)) {
                is Resource.Success -> {
                    val page = result.data.orEmpty()
                    if (page.size < TicketRepository.DEFAULT_PAGE_SIZE) _hasMorePages.value = false
                    _olderPages.value = (_olderPages.value + page).distinctBy { it.id }
                }
                else -> Unit // Silent failure; pull-to-refresh retries the whole list.
            }
            _isLoadingMore.value = false
        }
    }

    fun toggleStatusFilter(status: TicketStatus) {
        selectedStatuses.value =
            if (status in selectedStatuses.value) selectedStatuses.value - status
            else selectedStatuses.value + status
    }

    fun togglePriorityFilter(priority: TicketPriority) {
        selectedPriorities.value =
            if (priority in selectedPriorities.value) selectedPriorities.value - priority
            else selectedPriorities.value + priority
    }

    fun clearFilters() {
        selectedStatuses.value = emptySet()
        selectedPriorities.value = emptySet()
        searchQuery.value = ""
    }

    init {
        // Stop the manual-refresh indicator whenever a terminal state arrives.
        viewModelScope.launch {
            ticketsState.collect { state ->
                if (state !is Resource.Loading) _isRefreshing.value = false
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

            val ticket = Ticket(
                id = UUID.randomUUID().toString(),
                ticketNumber = generateTicketNumber(),
                creatorId = user?.id.orEmpty(),
                requesterName = name.trim(),
                requesterPhone = phone.trim(),
                requesterEmail = email.trim(),
                requesterLocation = location.trim(),
                subject = subject.trim(),
                description = description.trim(),
                category = category,
                priority = priority,
                status = TicketStatus.OPEN
            )

            when (val result = ticketRepository.createTicket(ticket)) {
                is Resource.Success -> {
                    _createTicketState.value = result
                    refresh(markAsUserAction = false)
                    _eventFlow.emit(UiEvent.TicketCreated)
                }
                is Resource.Error -> {
                    _createTicketState.value = result
                    _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Failed to create ticket"))
                }
                else -> Unit
            }
        }
    }

    fun addComment(ticketId: String, content: String) {
        viewModelScope.launch {
            val user = authRepository.currentUser.first()
            val comment = TicketComment(
                id = UUID.randomUUID().toString(),
                authorName = user?.fullName?.ifBlank { null } ?: "You",
                content = content.trim()
            )
            when (val result = ticketRepository.addComment(ticketId, comment)) {
                is Resource.Error ->
                    _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Failed to add comment"))
                else -> Unit
            }
        }
    }

    fun updateStatus(ticketId: String, status: TicketStatus) {
        viewModelScope.launch {
            when (val result = ticketRepository.updateTicketStatus(ticketId, status.name)) {
                is Resource.Error ->
                    _eventFlow.emit(UiEvent.ShowSnackbar(result.message ?: "Failed to update status"))
                else -> Unit
            }
        }
    }

    private fun generateTicketNumber(): String =
        "NH-" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        object TicketCreated : UiEvent()
    }
}

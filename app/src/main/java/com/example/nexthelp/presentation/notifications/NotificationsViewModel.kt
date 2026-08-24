package com.example.nexthelp.presentation.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexthelp.data.notifications.NotificationsController
import com.example.nexthelp.data.preferences.UserPreferencesRepository
import com.example.nexthelp.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupedNotifications(
    val loading: Boolean = true,
    val groups: List<Pair<String, List<AppNotification>>> = emptyList(),
    val unreadIds: Set<String> = emptySet(),
    val enabledTypes: Set<NotificationType> =
        setOf(
            NotificationType.STATUS_CHANGED,
            NotificationType.NEW_COMMENT,
            NotificationType.HIGH_PRIORITY,
            NotificationType.TICKET_RECEIVED
        )
)

/** Lightweight badge source for the bottom navigation bar. */
@HiltViewModel
class NotificationsBadgeViewModel @Inject constructor(
    controller: NotificationsController
) : ViewModel() {
    val unreadCount: StateFlow<Int> = controller.unreadCount
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    ticketRepository: com.example.nexthelp.domain.repository.TicketRepository,
    sessionManager: SessionManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val feed = sessionManager.currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList<com.example.nexthelp.domain.models.Ticket>())
            else ticketRepository.getTickets().map { resource ->
                (resource as? com.example.nexthelp.core.util.Resource.Success)?.data.orEmpty()
            }
        }

    private val recentComments = sessionManager.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyMap<String, com.example.nexthelp.domain.models.TicketComment>())
            } else {
                ticketRepository.getRecentComments(
                    System.currentTimeMillis() -
                        com.example.nexthelp.domain.repository.TicketRepository.RECENT_COMMENTS_WINDOW_MS
                )
            }
        }

    private val _markingSeen = MutableStateFlow(false)

    val uiState: StateFlow<GroupedNotifications> = combine(
        feed,
        sessionManager.currentUser,
        userPreferencesRepository.lastSeenNotifications,
        userPreferencesRepository.notifyStatusChanges,
        userPreferencesRepository.notifyComments,
        userPreferencesRepository.notifyPriority,
        recentComments
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tickets = values[0] as List<com.example.nexthelp.domain.models.Ticket>
        @Suppress("UNCHECKED_CAST")
        val user = values[1] as? com.example.nexthelp.domain.models.User
        val lastSeen = values[2] as Long
        val statusEnabled = values[3] as Boolean
        val commentsEnabled = values[4] as Boolean
        val priorityEnabled = values[5] as Boolean
        @Suppress("UNCHECKED_CAST")
        val latestComments =
            values[6] as Map<String, com.example.nexthelp.domain.models.TicketComment>

        val enabledTypes = buildSet {
            add(NotificationType.TICKET_RECEIVED)
            if (statusEnabled) add(NotificationType.STATUS_CHANGED)
            if (commentsEnabled) add(NotificationType.NEW_COMMENT)
            if (priorityEnabled) add(NotificationType.HIGH_PRIORITY)
        }

        val notifications = NotificationFactory.build(
            tickets = tickets,
            currentUserName = user?.fullName,
            enabledTypes = enabledTypes,
            latestComments = latestComments
        )

        val unreadCount = notifications.count { it.timestamp > lastSeen }
        val unreadIds = notifications
            .sortedByDescending { it.timestamp }
            .take(unreadCount)
            .map { it.id }
            .toSet()

        GroupedNotifications(
            loading = false,
            groups = NotificationFactory.groupByDay(notifications),
            unreadIds = unreadIds,
            enabledTypes = enabledTypes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupedNotifications()
    )

    /** Marks the feed as seen shortly after the screen becomes visible. */
    fun markAllSeen() {
        if (_markingSeen.value) return
        _markingSeen.value = true
        viewModelScope.launch {
            delay(700)
            userPreferencesRepository.setLastSeenNotifications(System.currentTimeMillis())
            _markingSeen.value = false
        }
    }

    fun setStatusEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setNotifyStatusChanges(enabled) }
    }

    fun setCommentsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setNotifyComments(enabled) }
    }

    fun setPriorityEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setNotifyPriority(enabled) }
    }
}

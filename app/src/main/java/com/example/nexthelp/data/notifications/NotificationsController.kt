package com.example.nexthelp.data.notifications

import com.example.nexthelp.core.util.ApplicationScope
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.data.preferences.UserPreferencesRepository
import com.example.nexthelp.data.session.SessionManager
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketComment
import com.example.nexthelp.domain.repository.TicketRepository
import com.example.nexthelp.presentation.notifications.AppNotification
import com.example.nexthelp.presentation.notifications.NotificationFactory
import com.example.nexthelp.presentation.notifications.NotificationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationPreferences(
    val enabledTypes: Set<NotificationType> = setOf(
        NotificationType.STATUS_CHANGED,
        NotificationType.NEW_COMMENT,
        NotificationType.HIGH_PRIORITY,
        NotificationType.TICKET_RECEIVED
    )
)

/**
 * App-lifetime observer that keeps a live, derived notification feed so the
 * bottom-navigation badge stays up to date even when no notification screen is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class NotificationsController @Inject constructor(
    ticketRepository: TicketRepository,
    private val sessionManager: SessionManager,
    userPreferencesRepository: UserPreferencesRepository,
    @ApplicationScope appScope: CoroutineScope
) {
    private val preferences: StateFlow<NotificationPreferences> = combine(
        userPreferencesRepository.notifyStatusChanges,
        userPreferencesRepository.notifyComments,
        userPreferencesRepository.notifyPriority
    ) { status, comments, priority ->
        NotificationPreferences(
            enabledTypes = buildSet {
                if (status) add(NotificationType.STATUS_CHANGED)
                if (comments) add(NotificationType.NEW_COMMENT)
                if (priority) add(NotificationType.HIGH_PRIORITY)
                // "Ticket received" is always on; it is purely informational.
                add(NotificationType.TICKET_RECEIVED)
            }
        )
    }.stateIn(appScope, SharingStarted.Eagerly, NotificationPreferences())

    private val ticketsFlow = sessionManager.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(Resource.Success(emptyList<Ticket>()))
            } else {
                ticketRepository.getTickets()
            }
        }
        .map { resource ->
            (resource as? Resource.Success)?.data.orEmpty()
        }

    private val recentCommentsFlow = sessionManager.currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyMap<String, TicketComment>())
            else ticketRepository.getRecentComments(
                System.currentTimeMillis() - TicketRepository.RECENT_COMMENTS_WINDOW_MS
            )
        }

    val notifications: StateFlow<List<AppNotification>> = combine(
        ticketsFlow,
        sessionManager.currentUser,
        preferences,
        recentCommentsFlow
    ) { tickets, user, prefs, latestComments ->
        NotificationFactory.build(
            tickets = tickets,
            currentUserName = user?.fullName,
            enabledTypes = prefs.enabledTypes,
            latestComments = latestComments
        )
    }.stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val unreadCount: StateFlow<Int> =
        combine(notifications, userPreferencesRepository.lastSeenNotifications) { items, lastSeen ->
            items.count { it.timestamp > lastSeen }
        }.stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )
}

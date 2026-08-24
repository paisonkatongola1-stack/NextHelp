package com.example.nexthelp.presentation.notifications

import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketComment
import com.example.nexthelp.domain.models.TicketPriority
import com.example.nexthelp.domain.models.TicketStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFactoryTest {

    private val now = 1_700_000_000_000L
    private val allTypes = NotificationType.entries.toSet()

    private fun ticket(
        id: String = "t1",
        status: TicketStatus = TicketStatus.OPEN,
        priority: TicketPriority = TicketPriority.LOW,
        createdAt: Long = now - 3_600_000L,
        updatedAt: Long = createdAt
    ) = Ticket(
        id = id,
        ticketNumber = "NH-$id",
        subject = "Subject",
        requesterName = "Requester",
        status = status,
        priority = priority,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    // ---- TICKET_RECEIVED ------------------------------------------------------

    @Test
    fun `recent ticket produces received notification`() {
        val result = NotificationFactory.build(
            tickets = listOf(ticket()),
            currentUserName = null,
            enabledTypes = allTypes,
            now = now
        )
        assertEquals(1, result.count { it.type == NotificationType.TICKET_RECEIVED })
    }

    @Test
    fun `old ticket does not produce received notification`() {
        val oldTicket = ticket(createdAt = now - 2 * ONE_DAY)
        val result = NotificationFactory.build(listOf(oldTicket), null, allTypes, now = now)
        assertFalse(result.any { it.type == NotificationType.TICKET_RECEIVED })
    }

    // ---- STATUS_CHANGED ---------------------------------------------------------

    @Test
    fun `status change notification carries resolved title`() {
        val resolved = ticket(status = TicketStatus.RESOLVED)
        val result = NotificationFactory.build(listOf(resolved), null, allTypes, now = now)

        val statusNotification = result.first { it.type == NotificationType.STATUS_CHANGED }
        assertEquals("Your ticket was resolved", statusNotification.title)
    }

    @Test
    fun `open ticket produces no status change notification`() {
        val result = NotificationFactory.build(listOf(ticket()), null, allTypes, now = now)
        assertFalse(result.any { it.type == NotificationType.STATUS_CHANGED })
    }

    // ---- NEW_COMMENT --------------------------------------------------------------

    @Test
    fun `latest comment produces new comment notification`() {
        val latest = TicketComment(id = "c2", authorName = "Agent", content = "On it", timestamp = now - 60_000)
        val result = NotificationFactory.build(
            tickets = listOf(ticket()),
            currentUserName = "Ada",
            enabledTypes = allTypes,
            latestComments = mapOf("t1" to latest),
            now = now
        )

        val comment = result.first { it.type == NotificationType.NEW_COMMENT }
        assertEquals("Agent responded", comment.title)
        assertEquals("On it", comment.message)
    }

    @Test
    fun `own latest comment produces no new comment notification`() {
        val own = TicketComment(id = "c1", authorName = "ada", content = "Thanks!", timestamp = now - 60_000)
        val result = NotificationFactory.build(
            listOf(ticket()), currentUserName = "Ada", enabledTypes = allTypes,
            latestComments = mapOf("t1" to own), now = now
        )
        assertFalse(result.any { it.type == NotificationType.NEW_COMMENT })
    }

    @Test
    fun `stale comment produces no new comment notification`() {
        val stale = TicketComment(id = "c9", authorName = "Agent", content = "Old reply", timestamp = now - 5 * ONE_DAY)
        val result = NotificationFactory.build(
            listOf(ticket()), null, allTypes, latestComments = mapOf("t1" to stale), now = now
        )
        assertFalse(result.any { it.type == NotificationType.NEW_COMMENT })
    }

    // ---- HIGH_PRIORITY ---------------------------------------------------------------

    @Test
    fun `critical open ticket produces high priority notification`() {
        val critical = ticket(priority = TicketPriority.CRITICAL)
        assertTrue(
            NotificationFactory.build(listOf(critical), null, allTypes, now = now)
                .any { it.type == NotificationType.HIGH_PRIORITY }
        )
    }

    @Test
    fun `resolved high priority ticket produces no high priority notification`() {
        val resolved = ticket(priority = TicketPriority.HIGH, status = TicketStatus.CLOSED)
        assertFalse(
            NotificationFactory.build(listOf(resolved), null, allTypes, now = now)
                .any { it.type == NotificationType.HIGH_PRIORITY }
        )
    }

    // ---- Preferences / ordering ----------------------------------------------------------

    @Test
    fun `disabled types are excluded`() {
        val noisy = ticket(
            status = TicketStatus.IN_PROGRESS,
            priority = TicketPriority.CRITICAL,
            updatedAt = now - 60_000
        )
        val result = NotificationFactory.build(
            listOf(noisy),
            currentUserName = null,
            enabledTypes = setOf(NotificationType.STATUS_CHANGED),
            now = now
        )
        assertEquals(setOf(NotificationType.STATUS_CHANGED), result.map { it.type }.toSet())
    }

    @Test
    fun `feed is sorted newest first and capped at limit`() {
        val tickets = (1..40).map {
            ticket(id = "t$it", status = TicketStatus.ASSIGNED, updatedAt = now - it * 60_000L)
        }
        val result = NotificationFactory.build(tickets, null, allTypes, now = now)

        assertEquals(30, result.size)
        assertEquals(result.sortedByDescending { it.timestamp }, result)
    }

    private companion object {
        const val ONE_DAY = 24 * 60 * 60 * 1000L
    }
}

package com.example.nexthelp.presentation.tickets

import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketComment
import com.example.nexthelp.domain.models.TicketPriority
import com.example.nexthelp.domain.models.TicketStatus
import com.example.nexthelp.domain.models.User
import com.example.nexthelp.domain.repository.TicketRepository
import com.example.nexthelp.fake.FakeAuthRepository
import com.example.nexthelp.fake.FakeTicketRepository
import com.example.nexthelp.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TicketViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeTicketRepository()
    private val auth = FakeAuthRepository()

    private fun viewModel() = TicketViewModel(repo, auth)

    private fun ticket(
        id: String,
        subject: String = "Subject $id",
        status: TicketStatus = TicketStatus.OPEN,
        priority: TicketPriority = TicketPriority.LOW,
        createdAt: Long = 0L
    ) = Ticket(
        id = id,
        ticketNumber = "NH-$id",
        subject = subject,
        description = "Description for $id",
        status = status,
        priority = priority,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    // ---- Filters ------------------------------------------------------------

    @Test
    fun `search query filters tickets by subject`() = runTest {
        repo.emitTickets(listOf(ticket("1", "Printer broken"), ticket("2", "Wifi down")))
        val vm = viewModel()
        backgroundScope.launch { vm.filteredTickets.collect {} }

        vm.searchQuery.value = "printer"
        advanceUntilIdle()

        assertEquals(listOf("1"), vm.filteredTickets.value.map { it.id })
    }

    @Test
    fun `status and priority filters combine`() = runTest {
        repo.emitTickets(
            listOf(
                ticket("1", status = TicketStatus.OPEN, priority = TicketPriority.HIGH),
                ticket("2", status = TicketStatus.IN_PROGRESS, priority = TicketPriority.HIGH),
                ticket("3", status = TicketStatus.OPEN, priority = TicketPriority.LOW)
            )
        )
        val vm = viewModel()
        backgroundScope.launch { vm.filteredTickets.collect {} }
        advanceUntilIdle()

        vm.toggleStatusFilter(TicketStatus.OPEN)
        vm.togglePriorityFilter(TicketPriority.HIGH)
        advanceUntilIdle()

        assertEquals(listOf("1"), vm.filteredTickets.value.map { it.id })

        vm.clearFilters()
        advanceUntilIdle()
        assertEquals(3, vm.filteredTickets.value.size)
    }

    // ---- Stats ----------------------------------------------------------------

    @Test
    fun `stats reflect loaded tickets`() = runTest {
        repo.emitTickets(
            listOf(
                ticket("1", status = TicketStatus.OPEN, priority = TicketPriority.CRITICAL),
                ticket("2", status = TicketStatus.IN_PROGRESS),
                ticket("3", status = TicketStatus.RESOLVED, priority = TicketPriority.HIGH),
                ticket("4", status = TicketStatus.CLOSED)
            )
        )
        val vm = viewModel()
        backgroundScope.launch { vm.stats.collect {} }
        advanceUntilIdle()

        val stats = vm.stats.value
        assertEquals(4, stats.total)
        assertEquals(1, stats.open)
        assertEquals(1, stats.inProgress)
        assertEquals(2, stats.resolved)
        assertEquals(1, stats.highPriority) // "3" is resolved so excluded
    }

    // ---- Pagination -----------------------------------------------------------

    @Test
    fun `requestMore appends older page and stops at short page`() = runTest {
        val firstPage = (25 downTo 1).map { ticket(it.toString(), createdAt = it.toLong()) }
        val secondPage = (0 downTo -4).map { ticket(it.toString(), createdAt = it.toLong()) }
        repo.olderPages.addAll(listOf(secondPage, emptyList()))
        repo.emitTickets(firstPage)

        val vm = viewModel()
        backgroundScope.launch { vm.loadedTickets.collect {} }
        advanceUntilIdle()

        assertTrue(vm.hasMorePages.value)
        vm.requestMore()
        advanceUntilIdle()

        // 25 from the real-time page + 5 older ones, merged and deduped.
        assertEquals(30, vm.loadedTickets.value.size)

        vm.requestMore() // short page -> no more pages
        advanceUntilIdle()
        assertFalse(vm.hasMorePages.value)
        // First cursor was the oldest ticket of the initial page.
        assertEquals(1L, repo.requestedCursors.first())
    }

    @Test
    fun `requestMore is a no-op while a load is already running or exhausted`() = runTest {
        repo.emitTickets(listOf(ticket("1")))
        repo.olderPages.add(emptyList())
        val vm = viewModel()
        backgroundScope.launch { vm.loadedTickets.collect {} }
        advanceUntilIdle()

        vm.requestMore()
        advanceUntilIdle()
        assertFalse(vm.hasMorePages.value)

        vm.requestMore()
        advanceUntilIdle()
        assertEquals(1, repo.requestedCursors.size)
    }

    // ---- Mutations --------------------------------------------------------------

    @Test
    fun `createTicket stores ticket with current user as creator`() = runTest {
        auth.setUser(User(id = "user-1", fullName = "Ada"))
        val vm = viewModel()

        vm.createTicket("Ada", "555", "ada@example.com", "HQ", "Broken thing", "Details", "Hardware", TicketPriority.HIGH)
        advanceUntilIdle()

        val created = repo.createdTickets.single()
        assertEquals("user-1", created.creatorId)
        assertEquals("Broken thing", created.subject)
        assertEquals(TicketStatus.OPEN, created.status)
        assertTrue(created.ticketNumber.startsWith("NH-"))
    }

    @Test
    fun `addComment delegates to repository with trimmed content`() = runTest {
        auth.setUser(User(id = "user-1", fullName = "Ada"))
        val vm = viewModel()

        vm.addComment("ticket-9", "  Looking into it  ")
        advanceUntilIdle()

        val (ticketId, comment) = repo.addedComments.single()
        assertEquals("ticket-9", ticketId)
        assertEquals("Looking into it", comment.content)
    }

    @Test
    fun `commentsState streams comments for observed ticket`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.commentsState.collect {} }

        vm.observeComments("t-1")
        repo.commentsFlow.value =
            Resource.Success(listOf(TicketComment(id = "c1", content = "hello")))
        advanceUntilIdle()

        val comments = (vm.commentsState.value as Resource.Success).data.orEmpty()
        assertEquals(listOf("c1"), comments.map { it.id })
    }
}

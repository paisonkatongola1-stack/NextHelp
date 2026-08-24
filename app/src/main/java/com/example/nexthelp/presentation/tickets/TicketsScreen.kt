package com.example.nexthelp.presentation.tickets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexthelp.core.ui.components.AppSearchField
import com.example.nexthelp.core.ui.components.EmptyState
import com.example.nexthelp.core.ui.components.ListSkeleton
import com.example.nexthelp.core.ui.components.SectionHeader
import com.example.nexthelp.core.ui.components.TicketCard
import com.example.nexthelp.core.ui.components.displayLabel
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.TicketPriority
import com.example.nexthelp.domain.models.TicketStatus
import com.example.nexthelp.domain.repository.TicketRepository
import com.example.nexthelp.presentation.home.ErrorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(
    onTicketClick: (String) -> Unit,
    onCreateTicket: () -> Unit,
    viewModel: TicketViewModel = hiltViewModel()
) {
    val ticketsState by viewModel.ticketsState.collectAsStateWithLifecycle()
    val filteredTickets by viewModel.filteredTickets.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMorePages by viewModel.hasMorePages.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedStatuses by viewModel.selectedStatuses.collectAsStateWithLifecycle()
    val selectedPriorities by viewModel.selectedPriorities.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-load the next page as the user approaches the end of the list.
    val nearListEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearListEnd) {
        if (nearListEnd) viewModel.requestMore()
    }

    val hasActiveFilters = selectedStatuses.isNotEmpty() || selectedPriorities.isNotEmpty() ||
        searchQuery.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "My Tickets",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
        Text(
            text = "Search, filter and track every request.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(16.dp))

        AppSearchField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            hint = "Search tickets, IDs or names…"
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TicketStatus.entries.forEach { status ->
                FilterChip(
                    selected = status in selectedStatuses,
                    onClick = { viewModel.toggleStatusFilter(status) },
                    label = { Text(status.displayLabel) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TicketPriority.entries.forEach { priority ->
                FilterChip(
                    selected = priority in selectedPriorities,
                    onClick = { viewModel.togglePriorityFilter(priority) },
                    label = { Text(priority.displayLabel) }
                )
            }
            if (hasActiveFilters) {
                TextButton(onClick = { viewModel.clearFilters() }) {
                    Icon(
                        Icons.Default.FilterListOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("  Clear")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.weight(1f)
        ) {
            when (val state = ticketsState) {
                is Resource.Loading -> ListSkeleton(itemCount = 5)
                is Resource.Error -> ErrorState(
                    message = state.message ?: "Couldn't load tickets",
                    onRetry = { viewModel.refresh() }
                )
                is Resource.Success -> {
                    if (filteredTickets.isEmpty()) {
                        if (hasActiveFilters) {
                            EmptyState(
                                icon = Icons.Default.FilterListOff,
                                title = "No matching tickets",
                                subtitle = "We couldn't find any tickets matching your search. Try different filters."
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Default.ConfirmationNumber,
                                title = "No tickets yet",
                                subtitle = "Create your first ticket and we'll help you track it here.",
                                ctaLabel = "Create a ticket",
                                onCtaClick = onCreateTicket
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                SectionHeader(
                                    title = "${filteredTickets.size} ticket" +
                                        if (filteredTickets.size == 1) "" else "s"
                                )
                            }
                            items(
                                count = filteredTickets.size,
                                key = { index -> filteredTickets[index].id }
                            ) { index ->
                                val ticket = filteredTickets[index]
                                TicketCard(ticket = ticket, onClick = { onTicketClick(ticket.id) })
                            }
                            if (isLoadingMore && hasMorePages) {
                                item(key = "loading-more") {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            } else if (!hasMorePages && filteredTickets.size > TicketRepository.DEFAULT_PAGE_SIZE) {
                                item(key = "end-of-list") {
                                    Text(
                                        text = "That's everything",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(96.dp)) }
                        }
                    }
                }
            }
        }
    }
}

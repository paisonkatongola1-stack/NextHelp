package com.example.nexthelp.presentation.tickets.details

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.nexthelp.core.ui.components.StatusChip
import com.example.nexthelp.core.ui.components.TicketTimeline
import com.example.nexthelp.core.ui.components.displayLabel
import com.example.nexthelp.core.ui.components.labelColor
import com.example.nexthelp.core.util.Resource
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.TicketPriority
import com.example.nexthelp.domain.models.User
import com.example.nexthelp.domain.models.canHandleTickets
import com.example.nexthelp.presentation.tickets.TicketViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailsScreen(
    ticketId: String,
    onNavigateBack: () -> Unit,
    viewModel: TicketViewModel = hiltViewModel()
) {
    val state by viewModel.ticketDetailsState.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val supportAgents by viewModel.supportAgents.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle(initialValue = null)
    var commentText by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    val isUploadingAttachment by viewModel.isUploadingAttachment.collectAsStateWithLifecycle()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) pendingImage = uri }

    fun sendComment() {
        viewModel.addComment(ticketId, commentText, pendingImage)
        commentText = ""
        pendingImage = null
    }

    val canManageAssignment = currentUser?.canHandleTickets == true

    LaunchedEffect(ticketId) {
        viewModel.loadTicketDetails(ticketId)
        viewModel.observeComments(ticketId)
        viewModel.observeSupportAgents()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ticket Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val resource = state) {
            is Resource.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Resource.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(resource.message ?: "Error")
                }
            }
            is Resource.Success -> {
                val ticket = resource.data!!
                val comments = (commentsState as? Resource.Success)?.data.orEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { TicketHeader(ticket) }
                        item { TicketTimelineCard(ticket.status) }
                        item {
                            AssignmentCard(
                                ticket = ticket,
                                canManage = canManageAssignment,
                                agentsState = supportAgents,
                                onAssign = { agent ->
                                    viewModel.assignTicket(ticket.id, agent?.id, agent?.fullName)
                                }
                            )
                        }
                        item { Text("Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        when (commentsState) {
                            is Resource.Loading -> item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(
                                        "Loading activity…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            else -> if (comments.isEmpty()) {
                                item {
                                    Text(
                                        "No activity yet. Add a comment below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            } else {
                                items(comments, key = { it.id }) { comment ->
                                    CommentItem(comment.authorName, comment.content, comment.timestamp, comment.imageUrl)
                                }
                            }
                        }
                    }

                    // Comment Input
                    Surface(tonalElevation = 2.dp) {
                        Column {
                            if (pendingImage != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = pendingImage,
                                        contentDescription = "Selected attachment",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(MaterialTheme.shapes.small)
                                    )
                                    Spacer(Modifier.weight(1f))
                                    if (isUploadingAttachment) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Uploading…", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { pendingImage = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isUploadingAttachment
                                ) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Attach image",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                OutlinedTextField(
                                    value = commentText,
                                    onValueChange = { commentText = it },
                                    placeholder = { Text("Add a comment...") },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = { sendComment() },
                                    enabled = (commentText.isNotBlank() || pendingImage != null) && !isUploadingAttachment
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TicketHeader(ticket: Ticket) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#${ticket.ticketNumber}", style = MaterialTheme.typography.labelSmall)
                StatusChip(ticket.status)
            }
            Text(
                ticket.subject,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(ticket.description, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = ticket.priority.labelColor().copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${ticket.priority.displayLabel} priority",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ticket.priority.labelColor()
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            DetailRow("Requester", ticket.requesterName)
            if (ticket.requesterLocation.isNotBlank()) {
                DetailRow("Location", ticket.requesterLocation)
            }
            DetailRow("Created", formatDate(ticket.createdAt))
        }
    }
}

@Composable
fun TicketTimelineCard(status: com.example.nexthelp.domain.models.TicketStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Progress",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            TicketTimeline(status = status)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentCard(
    ticket: Ticket,
    canManage: Boolean,
    agentsState: Resource<List<User>>,
    onAssign: (User?) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val agents = (agentsState as? Resource.Success)?.data.orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Assignment",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            if (!canManage) {
                DetailRow("Assigned to", ticket.assignedAgentName ?: "Unassigned")
                return@Column
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            ticket.assignedAgentName ?: "Unassigned",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (ticket.assignedAgentId == null) "Pick an agent to take this ticket"
                            else "Handling this ticket",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Box {
                    OutlinedButton(onClick = { menuExpanded = true }) {
                        Text(if (ticket.assignedAgentId == null) "Assign" else "Change")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose agent")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        when (agentsState) {
                            is Resource.Loading -> DropdownMenuItem(
                                text = { Text("Loading agents…") },
                                onClick = {}
                            )
                            is Resource.Error -> DropdownMenuItem(
                                text = { Text(agentsState.message ?: "Could not load agents") },
                                onClick = {}
                            )
                            else -> {
                                if (agents.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No agents available") },
                                        onClick = {}
                                    )
                                }
                                agents.forEach { agent ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                agent.fullName +
                                                    if (agent.id == ticket.assignedAgentId) "  ✓" else ""
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            if (agent.id != ticket.assignedAgentId) onAssign(agent)
                                        }
                                    )
                                }
                                if (ticket.assignedAgentId != null) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Unassign", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.PersonOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onAssign(null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun CommentItem(author: String, content: String, timestamp: Long, imageUrl: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(formatDate(timestamp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        if (content.isNotBlank()) {
            Text(content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Attachment from $author",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), thickness = 0.5.dp)
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

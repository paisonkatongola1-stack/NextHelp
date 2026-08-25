package com.example.nexthelp.presentation.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.nexthelp.BuildConfig
import com.example.nexthelp.core.ui.components.ConfirmDialog
import com.example.nexthelp.core.ui.components.InitialsAvatar
import com.example.nexthelp.core.ui.components.TicketCard
import com.example.nexthelp.data.profile.ProfileImageKind
import com.example.nexthelp.data.profile.ProfileImageUploader
import com.example.nexthelp.domain.models.Ticket
import com.example.nexthelp.domain.models.User
import com.example.nexthelp.domain.models.UserRole
import com.example.nexthelp.domain.repository.AuthRepository
import com.example.nexthelp.domain.repository.TicketRepository
import com.example.nexthelp.presentation.auth.AuthViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ProfileStats(
    val created: Int = 0,
    val active: Int = 0,
    val resolved: Int = 0
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    ticketRepository: TicketRepository,
    private val authRepository: AuthRepository,
    private val imageUploader: ProfileImageUploader
) : ViewModel() {

    val user = authRepository.currentUser

    /**
     * Single real-time subscription feeding both stats and the activity feed.
     * Deliberately scoped to tickets the user created themselves — even agents
     * and admins only see their own filings on their profile.
     */
    private val tickets: StateFlow<List<Ticket>> = ticketRepository.getMyTickets()
        .map { resource ->
            (resource as? com.example.nexthelp.core.util.Resource.Success)?.data.orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<ProfileStats> = tickets
        .map { list ->
            ProfileStats(
                created = list.size,
                active = list.count {
                    it.status in setOf(
                        com.example.nexthelp.domain.models.TicketStatus.ASSIGNED,
                        com.example.nexthelp.domain.models.TicketStatus.IN_PROGRESS,
                        com.example.nexthelp.domain.models.TicketStatus.WAITING_FOR_USER,
                        com.example.nexthelp.domain.models.TicketStatus.REOPENED
                    )
                },
                resolved = list.count {
                    it.status in setOf(
                        com.example.nexthelp.domain.models.TicketStatus.RESOLVED,
                        com.example.nexthelp.domain.models.TicketStatus.CLOSED
                    )
                }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileStats())

    val recentTickets: StateFlow<List<Ticket>> = tickets

    // ---- Profile editing ----------------------------------------------------

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _uploadingKind = MutableStateFlow<ProfileImageKind?>(null)
    val uploadingKind: StateFlow<ProfileImageKind?> = _uploadingKind.asStateFlow()

    fun saveName(fullName: String) {
        viewModelScope.launch {
            when (val result = authRepository.updateDisplayName(fullName)) {
                is com.example.nexthelp.core.util.Resource.Success ->
                    _events.emit("Name updated")
                is com.example.nexthelp.core.util.Resource.Error ->
                    _events.emit(result.message ?: "Couldn't update name")
                else -> Unit
            }
        }
    }

    fun saveDetails(bio: String, phone: String, location: String) {
        viewModelScope.launch {
            when (val result = authRepository.updateProfileDetails(bio, phone, location)) {
                is com.example.nexthelp.core.util.Resource.Success ->
                    _events.emit("Profile updated")
                is com.example.nexthelp.core.util.Resource.Error ->
                    _events.emit(result.message ?: "Couldn't update profile")
                else -> Unit
            }
        }
    }

    fun onImagePicked(kind: ProfileImageKind, uri: Uri) {
        if (_uploadingKind.value != null) return
        viewModelScope.launch {
            _uploadingKind.value = kind
            when (val result = imageUploader.upload(kind, uri)) {
                is com.example.nexthelp.core.util.Resource.Success ->
                    _events.emit(if (kind == ProfileImageKind.AVATAR) "Profile photo updated" else "Cover photo updated")
                is com.example.nexthelp.core.util.Resource.Error ->
                    _events.emit(result.message ?: "Upload failed")
                else -> Unit
            }
            _uploadingKind.value = null
        }
    }

    fun removeImage(kind: ProfileImageKind) {
        viewModelScope.launch {
            when (val result = imageUploader.remove(kind)) {
                is com.example.nexthelp.core.util.Resource.Success ->
                    _events.emit("Photo removed")
                is com.example.nexthelp.core.util.Resource.Error ->
                    _events.emit(result.message ?: "Couldn't remove photo")
                else -> Unit
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenAdminConsole: () -> Unit,
    onTicketClick: (String) -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle(initialValue = null)
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val recentTickets by viewModel.recentTickets.collectAsStateWithLifecycle()
    val uploadingKind by viewModel.uploadingKind.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onImagePicked(ProfileImageKind.AVATAR, it) } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ---- Header row with the section menu (hamburger) -----------------
            Box(Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = 4.dp, end = 8.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Sections menu")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(10.dp))
                                Text("Activity")
                            }
                        },
                        onClick = {
                            selectedTab = 0
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(10.dp))
                                Text("About")
                            }
                        },
                        onClick = {
                            selectedTab = 1
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(10.dp))
                                Text("Settings")
                            }
                        },
                        onClick = {
                            selectedTab = 2
                            showMenu = false
                        }
                    )
                }
            }

            // ---- Avatar + identity -------------------------------------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    if (user?.profileImageUrl != null) {
                        AsyncImage(
                            model = user?.profileImageUrl,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(104.dp)
                                .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                .clip(CircleShape)
                        )
                    } else {
                        InitialsAvatar(
                            fullName = user?.fullName.ifBlankOrDefault("?"),
                            size = 104.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }

                    CoverEditChip(
                        uploading = uploadingKind == ProfileImageKind.AVATAR,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp),
                        iconSize = 16,
                        onClick = {
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = user?.fullName.ifBlankOrDefault("Welcome"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = user?.email.ifBlankOrDefault("No email on file"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                user?.role?.let { role ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            if (role == UserRole.ADMIN) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.size(4.dp))
                            }
                            Text(
                                text = role.displayLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (!user?.bio.isNullOrBlank()) {
                    Text(
                        text = user!!.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp, start = 32.dp, end = 32.dp)
                    )
                }

                val locale = LocalConfiguration.current.locales[0]
                val metaLine = buildList {
                    user?.location?.takeIf { it.isNotBlank() }?.let { add(it) }
                    user?.createdAt?.takeIf { it > 0L }?.let {
                        add("Joined " + SimpleDateFormat("MMMM yyyy", locale).format(Date(it)))
                    }
                }.joinToString("  ·  ")
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // FB-style inline stat strip.
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatColumn(stats.created.toString(), "Tickets", Modifier.weight(1f))
                        VerticalStatDivider()
                        StatColumn(stats.active.toString(), "Active", Modifier.weight(1f))
                        VerticalStatDivider()
                        StatColumn(stats.resolved.toString(), "Resolved", Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Edit profile")
                    }
                    if (user?.role == UserRole.ADMIN) {
                        Button(onClick = onOpenAdminConsole) {
                            Icon(
                                Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("Admin console")
                        }
                    }
                }
            }

            // ---- Sections (switched from the hamburger menu) ------------------
            when (selectedTab) {
                0 -> ActivityTab(recentTickets, onOpenTicket = onTicketClick)
                1 -> AboutTab(user = user, onEditClick = { showEditDialog = true })
                else -> SettingsTab(
                    isAdmin = user?.role == UserRole.ADMIN,
                    onEditProfile = { showEditDialog = true },
                    onOpenAdminConsole = onOpenAdminConsole,
                    onHelp = { showHelpDialog = true },
                    onAbout = { showAboutDialog = true },
                    onLogout = { showLogoutConfirm = true }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "Log out?",
            message = "You'll need to sign in again to see your tickets and updates.",
            confirmLabel = "Log out",
            destructive = true,
            onConfirm = {
                showLogoutConfirm = false
                authViewModel.logout()
                onLogout()
            },
            onDismiss = { showLogoutConfirm = false }
        )
    }

    if (showEditDialog && user != null) {
        EditProfileDialog(
            user = user!!,
            onSave = { name, bio, phone, location ->
                showEditDialog = false
                if (name.isNotBlank() && name != user?.fullName) viewModel.saveName(name)
                viewModel.saveDetails(bio, phone, location)
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help & Support", fontWeight = FontWeight.Bold) },
            text = { Text("Our support team is available Monday–Friday, 8am–6pm.\n\nEmail: support@nexthelp.app") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:support@nexthelp.app")
                                putExtra(Intent.EXTRA_SUBJECT, "NextHelp support request")
                            }
                        )
                    } catch (_: Exception) {
                        scope.launch { snackbarHostState.showSnackbar("No email app found") }
                    }
                    showHelpDialog = false
                }) { Text("Email us") }
            },
            dismissButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("Close") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("NextHelp", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Version ${BuildConfig.VERSION_NAME}\n\n" +
                        "Solving problems, together.\n\n" +
                        "A modern support ticketing experience for teams that care."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            }
        )
    }
}

// ---------------------------------------------------------------------------------
// Tab contents
// ---------------------------------------------------------------------------------

@Composable
private fun ActivityTab(tickets: List<Ticket>, onOpenTicket: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        if (tickets.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No activity yet.\nFile a ticket and it will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }
        } else {
            tickets.take(10).forEach { ticket ->
                TicketCard(ticket = ticket, onClick = { onOpenTicket(ticket.id) })
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = "Showing your ${minOf(tickets.size, 10)} most recent tickets",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AboutTab(user: User?, onEditClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        SectionCard(title = "Contact info") {
            AboutRow(Icons.Default.Email, "Email", user?.email.ifBlankOrDefault("—"))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            AboutRow(Icons.Default.PhoneAndroid, "Phone", user?.phoneNumber.ifBlankOrDefault("Not added"))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            AboutRow(Icons.Default.LocationOn, "Location", user?.location.ifBlankOrDefault("Not added"))
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Basic info") {
            AboutRow(Icons.Default.Verified, "Role", user?.role?.displayLabel ?: "Member")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            AboutRow(
                Icons.Default.Info,
                "Member since",
                user?.createdAt?.takeIf { it > 0L }
                    ?.let { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(it)) }
                    ?: "Unknown"
            )
        }
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onEditClick, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("Update your info")
        }
    }
}

@Composable
private fun SettingsTab(
    isAdmin: Boolean,
    onEditProfile: () -> Unit,
    onOpenAdminConsole: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        SectionTitle("Account")
        SettingsCard {
            SettingsRow(Icons.Default.Edit, "Edit profile", "Name, bio, contact details", onClick = onEditProfile)
            if (isAdmin) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsRow(
                    Icons.Default.AdminPanelSettings,
                    "Admin console",
                    "Manage all incoming tickets",
                    onClick = onOpenAdminConsole
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionTitle("Support")
        SettingsCard {
            SettingsRow(Icons.Default.HelpOutline, "Help & support", "Get answers to common questions", onClick = onHelp)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsRow(
                Icons.Default.Info,
                "About NextHelp",
                "Version ${BuildConfig.VERSION_NAME}",
                onClick = onAbout
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsCard {
            SettingsRow(
                Icons.AutoMirrored.Filled.Logout,
                "Log out",
                subtitle = null,
                destructive = true,
                onClick = onLogout
            )
        }
    }
}

// ---------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------

@Composable
private fun CoverEditChip(
    uploading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 18
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shadowElevation = 2.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size((iconSize + 14).dp)) {
            if (uploading) {
                CircularProgressIndicator(modifier = Modifier.size((iconSize - 2).dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = if (iconSize <= 16) "Change profile photo" else "Change cover photo",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(iconSize.dp)
                )
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VerticalStatDivider() {
    Box(
        Modifier
            .height(28.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun AboutRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val contentColor =
        if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    user: User,
    onSave: (name: String, bio: String, phone: String, location: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(user.fullName) }
    var bio by remember { mutableStateOf(user.bio) }
    var phone by remember { mutableStateOf(user.phoneNumber) }
    var location by remember { mutableStateOf(user.location) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(160) },
                    label = { Text("Bio") },
                    placeholder = { Text("A little about you…") },
                    minLines = 2,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), bio.trim(), phone.trim(), location.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ---------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------

private fun String?.ifBlankOrDefault(default: String): String =
    if (this.isNullOrBlank()) default else this

private val UserRole.displayLabel: String
    get() = when (this) {
        UserRole.ADMIN -> "Administrator"
        UserRole.SUPPORT_MANAGER -> "Support manager"
        UserRole.SUPPORT_AGENT -> "Support agent"
        UserRole.USER -> "Member"
    }

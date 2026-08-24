package com.example.nexthelp.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.nexthelp.domain.models.TicketStatus

/**
 * Rounded search field with a clear button.
 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = singleLine,
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

/**
 * Prominent quick-action tile for the dashboard.
 */
@Composable
fun QuickActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    val container = if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val content = if (emphasized) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        onClick = onClick,
        modifier = modifier.height(92.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = content.copy(alpha = 0.15f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

/**
 * Toggleable filter chip row used for status / priority filtering.
 */
@Composable
fun <T> FilterChipRow(
    options: List<T>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    labelOf: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(option) },
                label = { Text(labelOf(option)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Animated status timeline: Created → Assigned → In Progress → Resolved.
 */
@Composable
fun TicketTimeline(status: TicketStatus, modifier: Modifier = Modifier) {
    val currentStep = when (status) {
        TicketStatus.OPEN -> 0
        TicketStatus.REOPENED, TicketStatus.ASSIGNED -> 1
        TicketStatus.IN_PROGRESS, TicketStatus.WAITING_FOR_USER -> 2
        TicketStatus.RESOLVED, TicketStatus.CLOSED -> 3
    }

    data class Step(val label: String, val icon: ImageVector)

    val steps = listOf(
        Step("Created", Icons.Outlined.ConfirmationNumber),
        Step("Assigned", Icons.Default.AssignmentInd),
        Step("In Progress", Icons.Default.Engineering),
        Step("Resolved", Icons.Default.CheckCircle)
    )

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { index, step ->
            val completed = index <= currentStep
            val isCurrent = index == currentStep && status != TicketStatus.CLOSED
            val circleColor by animateColorAsState(
                targetValue = when {
                    completed -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                label = "stepColor$index"
            )
            val contentColor =
                if (completed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = circleColor,
                    shadowElevation = if (isCurrent) 6.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .fillMaxWidth(0.55f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = step.icon,
                            contentDescription = step.label,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (completed) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (index < steps.lastIndex) {
                val nextCompleted = index + 1 <= currentStep
                val lineColor by animateColorAsState(
                    targetValue = if (nextCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    label = "lineColor$index"
                )
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .height(34.dp)
                        .padding(vertical = 16.dp)
                        .fillMaxWidth()
                        .background(lineColor, MaterialTheme.shapes.small)
                )
            }
        }
    }
}

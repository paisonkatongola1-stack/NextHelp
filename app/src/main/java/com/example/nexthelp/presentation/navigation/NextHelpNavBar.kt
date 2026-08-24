package com.example.nexthelp.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Mobile-first bottom navigation with a raised, centered "Create" action.
 * Fixed at the bottom; respects system navigation-bar insets.
 */
@Composable
fun NextHelpBottomBar(
    currentRoute: String?,
    unreadNotifications: Int,
    onTabSelected: (String) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding()
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavBarItem(
                    tab = BottomTab.HOME,
                    selected = currentRoute == BottomTab.HOME.route,
                    badgeCount = 0,
                    onClick = { onTabSelected(BottomTab.HOME.route) },
                    modifier = Modifier.weight(1f)
                )
                NavBarItem(
                    tab = BottomTab.TICKETS,
                    selected = currentRoute == BottomTab.TICKETS.route,
                    badgeCount = 0,
                    onClick = { onTabSelected(BottomTab.TICKETS.route) },
                    modifier = Modifier.weight(1f)
                )
                // Space reserved for the raised Create button.
                Box(Modifier.weight(1f).fillMaxHeight())
                NavBarItem(
                    tab = BottomTab.NOTIFICATIONS,
                    selected = currentRoute == BottomTab.NOTIFICATIONS.route,
                    badgeCount = if (currentRoute != BottomTab.NOTIFICATIONS.route) unreadNotifications else 0,
                    onClick = { onTabSelected(BottomTab.NOTIFICATIONS.route) },
                    modifier = Modifier.weight(1f)
                )
                NavBarItem(
                    tab = BottomTab.PROFILE,
                    selected = currentRoute == BottomTab.PROFILE.route,
                    badgeCount = 0,
                    onClick = { onTabSelected(BottomTab.PROFILE.route) },
                    modifier = Modifier.weight(1f)
                )
            }

            FloatingActionButton(
                onClick = onCreateClick,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-14).dp)
                    .size(56.dp)
                    .shadow(6.dp, CircleShape),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create ticket",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    tab: BottomTab,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "tint${tab.name}"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "scale${tab.name}"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(
                painter = painterResource(id = if (selected) tab.iconSelected else tab.iconUnselected),
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier
                    .size(26.dp)
                    .scale(iconScale)
            )
            AnimatedBadge(
                visible = badgeCount > 0,
                count = badgeCount,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AnimatedBadge(visible: Boolean, count: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier.offset(x = 8.dp, y = (-4).dp)
    ) {
        Badge(containerColor = MaterialTheme.colorScheme.error) {
            Text(text = if (count > 99) "99+" else count.toString())
        }
    }
}

/**
 * Side navigation used on medium/expanded window sizes so the layout does not
 * look like a stretched phone app on tablets and desktops.
 */
@Composable
fun NextHelpNavRail(
    currentRoute: String?,
    unreadNotifications: Int,
    onTabSelected: (String) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            FloatingActionButton(
                onClick = onCreateClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create ticket")
            }
        }
    ) {
        BottomTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = currentRoute == tab.route,
                onClick = { onTabSelected(tab.route) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tab == BottomTab.NOTIFICATIONS && unreadNotifications > 0) {
                                Badge { Text(if (unreadNotifications > 99) "99+" else unreadNotifications.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = if (currentRoute == tab.route) tab.iconSelected else tab.iconUnselected),
                            contentDescription = tab.label
                        )
                    }
                },
                label = { Text(tab.label) }
            )
        }
    }
}

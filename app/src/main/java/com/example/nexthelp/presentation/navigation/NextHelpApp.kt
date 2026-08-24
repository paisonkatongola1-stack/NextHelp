package com.example.nexthelp.presentation.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.nexthelp.core.ui.RememberWindowSizeClass
import com.example.nexthelp.core.ui.WindowSizeClass
import com.example.nexthelp.presentation.notifications.NotificationsBadgeViewModel

@Composable
fun NextHelpApp(pushTicketId: String? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    AskForNotificationPermission()

    LaunchedEffect(pushTicketId) {
        pushTicketId?.takeIf { it.isNotBlank() }?.let { ticketId ->
            navController.navigate(Screen.TicketDetails.createRoute(ticketId)) { launchSingleTop = true }
        }
    }

    val badgeViewModel: NotificationsBadgeViewModel = hiltViewModel()
    val unreadNotifications by badgeViewModel.unreadCount.collectAsStateWithLifecycle()

    RememberWindowSizeClass { sizeClass ->
        if (sizeClass == WindowSizeClass.EXPANDED) {
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = currentRoute in Screen.tabRoutes) {
                    NextHelpNavRail(
                        currentRoute = currentRoute,
                        unreadNotifications = unreadNotifications,
                        onTabSelected = { route -> navController.navigateTab(route) },
                        onCreateClick = { navController.navigateCreate() }
                    )
                }
                NextHelpNavGraph(
                    navController = navController,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Scaffold(
                bottomBar = {
                    AnimatedVisibility(
                        visible = currentRoute in Screen.tabRoutes,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        NextHelpBottomBar(
                            currentRoute = currentRoute,
                            unreadNotifications = unreadNotifications,
                            onTabSelected = { route -> navController.navigateTab(route) },
                            onCreateClick = { navController.navigateCreate() }
                        )
                    }
                }
            ) { innerPadding ->
                NextHelpNavGraph(
                    navController = navController,
                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                )
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        // Pop up to the start of the tab graph so Back behaves predictably.
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateCreate() {
    navigate(Screen.CreateTicket.route) { launchSingleTop = true }
}

@Composable
private fun AskForNotificationPermission() {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Result is optional; notifications simply won't show if denied. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

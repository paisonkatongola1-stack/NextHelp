package com.example.nexthelp.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.nexthelp.core.ui.rememberReducedMotion
import com.example.nexthelp.domain.models.UserRole
import com.example.nexthelp.presentation.auth.AuthViewModel
import com.example.nexthelp.presentation.auth.ForgotPasswordScreen
import com.example.nexthelp.presentation.auth.LoginScreen
import com.example.nexthelp.presentation.auth.RegisterScreen
import com.example.nexthelp.presentation.home.AdminHome
import com.example.nexthelp.presentation.home.HomeDashboard
import com.example.nexthelp.presentation.notifications.NotificationsScreen
import com.example.nexthelp.presentation.profile.ProfileScreen
import com.example.nexthelp.presentation.splash.SplashScreen
import com.example.nexthelp.presentation.tickets.CreateTicketScreen
import com.example.nexthelp.presentation.tickets.TicketsScreen
import com.example.nexthelp.presentation.tickets.details.TicketDetailsScreen

private const val TAB_ANIM = 200

@Composable
fun NextHelpNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val reducedMotion = rememberReducedMotion()

    // Tab transitions: subtle horizontal shift + fade. Honors the system
    // "remove animations" accessibility setting by dropping to hard cuts.
    val enterTab =
        if (reducedMotion) EnterTransition.None
        else fadeIn(tween(TAB_ANIM)) + slideInHorizontally(tween(TAB_ANIM)) { it / 16 }
    val exitTab = if (reducedMotion) ExitTransition.None else fadeOut(tween(TAB_ANIM))
    val popEnterTab = if (reducedMotion) EnterTransition.None else fadeIn(tween(TAB_ANIM))
    val popExitTab =
        if (reducedMotion) ExitTransition.None
        else fadeOut(tween(TAB_ANIM)) + slideOutHorizontally(tween(TAB_ANIM)) { -it / 16 }

    val enterSheet: EnterTransition =
        if (reducedMotion) EnterTransition.None
        else slideInVertically(tween(280)) { it } + fadeIn(tween(280))
    val exitSheetFade: ExitTransition =
        if (reducedMotion) ExitTransition.None else fadeOut(tween(200))

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier,
        enterTransition = { enterTab },
        exitTransition = { exitTab },
        popEnterTransition = { popEnterTab },
        popExitTransition = { popExitTab }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    if (currentUser != null) {
                        // Session restored — go straight into the app.
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(
            Screen.Login.route,
            enterTransition = { enterSheet },
            popExitTransition = { exitSheetFade }
        ) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) }
            )
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            HomeDashboard(
                onCreateTicket = { navController.navigateCreate() },
                onTicketClick = { id -> navController.navigateTicketDetails(id) },
                onViewAllTickets = { navController.navigateToTab(Screen.Tickets.route) },
                onOpenNotifications = { navController.navigateToTab(Screen.Notifications.route) },
                onOpenAdminConsole = { navController.navigate(Screen.AdminHome.route) },
                authViewModel = authViewModel
            )
        }

        composable(Screen.Tickets.route) {
            TicketsScreen(
                onTicketClick = { id -> navController.navigateTicketDetails(id) },
                onCreateTicket = { navController.navigateCreate() }
            )
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onNotificationClick = { ticketId -> navController.navigateTicketDetails(ticketId) }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenAdminConsole = { navController.navigate(Screen.AdminHome.route) },
                onTicketClick = { id -> navController.navigateTicketDetails(id) },
                authViewModel = authViewModel
            )
        }

        composable(
            Screen.CreateTicket.route,
            enterTransition = { enterSheet },
            exitTransition = { exitSheetFade },
            popEnterTransition = { if (reducedMotion) EnterTransition.None else fadeIn(tween(200)) },
            popExitTransition = { slideOutVertically(tween(240)) { it } + fadeOut(tween(240)) }
        ) {
            CreateTicketScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.AdminHome.route) {
            // The admin console is restricted to admins; anyone else is bounced back.
            if (currentUser?.role == UserRole.ADMIN) {
                AdminHome(
                    onTicketClick = { id -> navController.navigateTicketDetails(id) },
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                AccessDeniedScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(
            route = Screen.TicketDetails.route,
            arguments = listOf(navArgument("ticketId") { type = NavType.StringType }),
            enterTransition = { slideInVertically(tween(260)) { it } + fadeIn(tween(260)) },
            exitTransition = { fadeOut(tween(200)) },
            popExitTransition = { slideOutVertically(tween(240)) { it } + fadeOut(tween(240)) }
        ) { backStackEntry ->
            val ticketId = backStackEntry.arguments?.getString("ticketId").orEmpty()
            TicketDetailsScreen(
                ticketId = ticketId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

private fun NavHostController.navigateCreate() {
    navigate(Screen.CreateTicket.route) { launchSingleTop = true }
}

private fun NavHostController.navigateTicketDetails(ticketId: String) {
    navigate(Screen.TicketDetails.createRoute(ticketId)) { launchSingleTop = true }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AccessDeniedScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Admin Console") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.size(16.dp))
            Text(
                text = "Admins only",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "You don't have permission to open the admin dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(24.dp))
            Button(onClick = onBack) { Text("Go back") }
        }
    }
}

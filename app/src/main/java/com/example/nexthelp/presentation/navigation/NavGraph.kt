package com.example.nexthelp.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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

    // Tab transitions: subtle horizontal shift + fade.
    val enterTab = fadeIn(tween(TAB_ANIM)) + slideInHorizontally(tween(TAB_ANIM)) { it / 16 }
    val exitTab = fadeOut(tween(TAB_ANIM))
    val popEnterTab = fadeIn(tween(TAB_ANIM))
    val popExitTab = fadeOut(tween(TAB_ANIM)) + slideOutHorizontally(tween(TAB_ANIM)) { -it / 16 }

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
            enterTransition = { slideInVertically(tween(280)) { it } + fadeIn(tween(280)) },
            popExitTransition = { fadeOut(tween(200)) }
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
            enterTransition = { slideInVertically(tween(260)) { it } + fadeIn(tween(260)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { slideOutVertically(tween(240)) { it } + fadeOut(tween(240)) }
        ) {
            CreateTicketScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.AdminHome.route) {
            AdminHome(
                onTicketClick = { id -> navController.navigateTicketDetails(id) },
                onNavigateBack = { navController.popBackStack() }
            )
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

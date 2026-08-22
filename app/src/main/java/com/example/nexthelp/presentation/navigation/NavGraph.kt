package com.example.nexthelp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nexthelp.domain.models.UserRole
import com.example.nexthelp.presentation.auth.AuthViewModel
import com.example.nexthelp.presentation.auth.ForgotPasswordScreen
import com.example.nexthelp.presentation.auth.LoginScreen
import com.example.nexthelp.presentation.auth.RegisterScreen
import com.example.nexthelp.presentation.home.AdminHome
import com.example.nexthelp.presentation.home.HomeDashboard
import com.example.nexthelp.presentation.splash.SplashScreen
import com.example.nexthelp.presentation.tickets.CreateTicketScreen
import com.example.nexthelp.presentation.tickets.details.TicketDetailsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Splash.route,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val currentUser by authViewModel.currentUser.collectAsState(initial = null)

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    if (currentUser != null) {
                        val destination = if (currentUser?.role == UserRole.ADMIN) Screen.AdminHome.route else Screen.Home.route
                        navController.navigate(destination) {
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
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { role ->
                    val destination = if (role == UserRole.ADMIN) Screen.AdminHome.route else Screen.Home.route
                    navController.navigate(destination) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                }
            )
        }
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    val destination = if (currentUser?.role == UserRole.ADMIN) Screen.AdminHome.route else Screen.Home.route
                    navController.navigate(destination) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Home.route) {
            HomeDashboard(
                onCreateTicket = {
                    navController.navigate(Screen.CreateTicket.route)
                },
                onTicketClick = { id ->
                    navController.navigate(Screen.TicketDetails.createRoute(id))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.AdminHome.route) {
            AdminHome(
                onTicketClick = { id ->
                    navController.navigate(Screen.TicketDetails.createRoute(id))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.CreateTicket.route) {
            CreateTicketScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.TicketDetails.route) { backStackEntry ->
            val ticketId = backStackEntry.arguments?.getString("ticketId") ?: ""
            TicketDetailsScreen(
                ticketId = ticketId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

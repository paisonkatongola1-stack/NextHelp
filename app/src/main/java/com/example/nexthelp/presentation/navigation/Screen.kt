package com.example.nexthelp.presentation.navigation

import androidx.annotation.DrawableRes
import com.example.nexthelp.R

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object ForgotPassword : Screen("forgot_password")

    // Tabs (bottom navigation / rail)
    object Home : Screen("home")
    object Tickets : Screen("tickets")
    object CreateTicket : Screen("create_ticket")
    object Notifications : Screen("notifications")
    object Profile : Screen("profile")

    // Full-screen destinations
    object AdminHome : Screen("admin_home")
    object TicketDetails : Screen("ticket_details/{ticketId}") {
        fun createRoute(ticketId: String) = "ticket_details/$ticketId"
    }

    companion object {
        /** Destinations that show the bottom navigation / rail. */
        val tabRoutes = setOf(Home.route, Tickets.route, Notifications.route, Profile.route)
    }
}

enum class BottomTab(
    val route: String,
    val label: String,
    val iconSelected: Int,
    val iconUnselected: Int
) {
    HOME(Screen.Home.route, "Home", R.drawable.ic_nav_home_filled, R.drawable.ic_nav_home),
    TICKETS(Screen.Tickets.route, "Tickets", R.drawable.ic_nav_tickets_filled, R.drawable.ic_nav_tickets),
    NOTIFICATIONS(Screen.Notifications.route, "Inbox", R.drawable.ic_nav_bell_filled, R.drawable.ic_nav_bell),
    PROFILE(Screen.Profile.route, "Profile", R.drawable.ic_nav_person_filled, R.drawable.ic_nav_person)
}

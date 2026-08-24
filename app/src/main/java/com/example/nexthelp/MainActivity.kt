package com.example.nexthelp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.nexthelp.data.notifications.TicketNotifications
import com.example.nexthelp.presentation.navigation.NextHelpApp
import com.example.nexthelp.ui.theme.NextHelpTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Consume once so recreation doesn't re-trigger navigation.
        val pushTicketId = intent?.getStringExtra(TicketNotifications.EXTRA_TICKET_ID)
        intent?.removeExtra(TicketNotifications.EXTRA_TICKET_ID)

        setContent {
            NextHelpTheme {
                NextHelpApp(pushTicketId = pushTicketId)
            }
        }
    }
}

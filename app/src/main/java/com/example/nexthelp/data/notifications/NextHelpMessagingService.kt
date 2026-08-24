package com.example.nexthelp.data.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NextHelpMessagingService : FirebaseMessagingService() {

    @Inject lateinit var fcmTokenManager: FcmTokenManager

    override fun onNewToken(token: String) {
        fcmTokenManager.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // FCM shows system-tray notifications itself when the app is backgrounded;
        // this path covers data messages and messages received while foregrounded.
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val ticketId = message.data["ticketId"]
        TicketNotifications.post(applicationContext, title, body, ticketId)
    }
}

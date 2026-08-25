package com.example.nexthelp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end navigation flow against the current UI:
 * splash -> login (real Firebase auth with the configured dev admin) ->
 * home dashboard -> each bottom tab.
 *
 * Requires debug-build dev admin credentials in local.properties
 * (nexthelp.dev.adminEmail / nexthelp.dev.adminPassword) and a device with
 * network access. Skips when they are absent.
 */
class AppNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun devCredentials(): Pair<String, String>? {
        val email = runCatching {
            Class.forName("com.example.nexthelp.BuildConfig")
                .getField("DEV_ADMIN_EMAIL").get(null) as String
        }.getOrNull().takeIf { !it.isNullOrBlank() } ?: return null
        val password = runCatching {
            Class.forName("com.example.nexthelp.BuildConfig")
                .getField("DEV_ADMIN_PASSWORD").get(null) as String
        }.getOrNull().takeIf { !it.isNullOrBlank() } ?: return null
        return email to password
    }

    private fun waitForText(text: String, timeout: Long = 15_000) {
        composeTestRule.waitUntil(timeoutMillis = timeout) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }

    @Test
    fun splashToLoginToAllTabs() {
        val creds = devCredentials()
        assumeTrue("Dev admin credentials not configured; skipping E2E test", creds != null)
        val (email, password) = creds!!

        // 1. Splash routes to login (or straight home if a session was restored).
        waitForText("Welcome Back", timeout = 20_000)

        // 2. Sign in with the configured admin account.
        composeTestRule.onNodeWithText("Email Address").performTextInput(email)
        composeTestRule.onNodeWithText("Password").performTextInput(password)
        composeTestRule.onNodeWithText("Login").performClick()

        // 3. Home dashboard renders its quick actions once tickets stream in.
        waitForText("Quick actions", timeout = 30_000)

        // 4. Walk the bottom navigation tabs.
        composeTestRule.onNodeWithText("Tickets").performClick()
        waitForText("My Tickets", timeout = 15_000)

        composeTestRule.onNodeWithText("Inbox").performClick()
        waitForText("Inbox", timeout = 15_000)

        composeTestRule.onNodeWithText("Profile").performClick()
        waitForText("Edit profile", timeout = 15_000)

        composeTestRule.onNodeWithText("Home").performClick()
        waitForText("Quick actions", timeout = 15_000)
    }
}

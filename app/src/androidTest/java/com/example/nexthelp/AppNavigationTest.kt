package com.example.nexthelp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fullAppFlow_SplashToLoginToHome() {
        // 1. Wait for Splash Screen (Logo should be displayed)
        composeTestRule.onNodeWithContentDescription("NextHelp Logo").assertIsDisplayed()
        
        // 2. Wait for navigation to Login Screen (Splash delay is 2s + 1s anim)
        // We use a longer timeout or just wait for the element
        composeTestRule.waitUntil(10000) {
            composeTestRule
                .onAllNodes(androidx.compose.ui.test.hasText("Welcome Back"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Welcome Back").assertIsDisplayed()
        
        // 3. Perform Login
        composeTestRule.onNodeWithText("Email").performTextInput("test@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("password123")
        
        composeTestRule.onNodeWithText("Login").performClick()
        
        // 4. Verify Home Dashboard
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodes(androidx.compose.ui.test.hasText("My Tickets"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithText("My Tickets").assertIsDisplayed()
        composeTestRule.onNodeWithText("OPEN").assertIsDisplayed()
    }
}

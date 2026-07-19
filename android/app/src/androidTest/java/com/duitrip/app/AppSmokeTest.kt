package com.duitrip.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator UI smoke test: launches the app and verifies it reaches the Landing screen
 * without crashing and renders visibly (would catch the startup-crash and black-text
 * bugs). Heavy (needs an emulator), so it runs only on manual dispatch / tags.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_launches_andShowsLanding() {
        // Fresh install = signed out → Landing. Wait for the async auth gate to settle.
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("Sign in").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Duitrip").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()
    }
}

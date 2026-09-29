package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tapping a backup notification opens the app at the Backup section in Tools, then Settings (owner, "Tap on the notify
 * to go to the backup page"; dispatch 2026-09-28-153). The real screen, given the request the notification's intent
 * becomes: from the Maps tab and from another tab, the section is on screen.
 */
abstract class OpenBackupSectionTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    private fun setScreen() {
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
    }

    @Test
    fun `the request opens Settings at the Backup section from the Maps tab`() {
        setScreen()

        state.openBackupRequest = 1
        composeRule.waitForIdle()

        // The top of the section is on screen: "Back up now" is its first control. (The section is taller than a short
        // window, so the rest of it is a scroll away.)
        composeRule.onNodeWithText("Back up now").assertIsDisplayed()
    }

    @Test
    fun `the request opens it from another tab too`() {
        setScreen()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()

        state.openBackupRequest = 1
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Back up now").assertIsDisplayed()
    }

    @Test
    fun `a second tap on a notification opens it again`() {
        setScreen()
        state.openBackupRequest = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Back up now").assertIsDisplayed()
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.waitForIdle()

        state.openBackupRequest = 2
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Back up now").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class OpenBackupSectionPortraitTest : OpenBackupSectionTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class OpenBackupSectionShortLandscapeTest : OpenBackupSectionTests()

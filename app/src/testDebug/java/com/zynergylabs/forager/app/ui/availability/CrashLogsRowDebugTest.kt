package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Debug builds keep Settings' "Crash Logs" row and what it opens (dispatch 2026-10-11, RECORD -830).
 * Reached through the real screen ([SettingsPageHarness]); the panel lists the real (empty) crash
 * directory. `CrashLogsRowReleaseTest` asks the same screen in a release build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class CrashLogsRowDebugTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SettingsPageHarness.declareHostActivity()).around(composeRule)

    @Test
    fun `Settings shows Crash Logs in a debug build, and it opens the crash list`() {
        SettingsPageHarness.openSettings(composeRule)

        composeRule.onNodeWithText(SettingsPageHarness.SETTINGS_CONTROL_ROW).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsPageHarness.CRASH_LOGS_ROW).performScrollTo().assertIsDisplayed().performClick()

        composeRule.onNodeWithText("No crash reports yet.").assertIsDisplayed()
    }
}

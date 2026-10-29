package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Release builds have no "Crash Logs" row in Settings (dispatch 2026-10-11; the owner, RECORD -830:
 * "The Crash logs row in Settings"). The same screen as `CrashLogsRowDebugTest`, reached the same
 * way; "Night Maps" being there is the check that the page showing is Settings, so the row's absence
 * is not a page that never opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class CrashLogsRowReleaseTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SettingsPageHarness.declareHostActivity()).around(composeRule)

    @Test
    fun `Settings has no Crash Logs row in a release build`() {
        SettingsPageHarness.openSettings(composeRule)

        composeRule.onNodeWithText(SettingsPageHarness.SETTINGS_CONTROL_ROW).performScrollTo().assertIsDisplayed()
        assertEquals(
            "Settings shows a '${SettingsPageHarness.CRASH_LOGS_ROW}' row in a release build",
            0,
            composeRule.onAllNodesWithText(SettingsPageHarness.CRASH_LOGS_ROW).fetchSemanticsNodes().size,
        )
    }
}

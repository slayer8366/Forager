package com.zynergylabs.forager.app.ui.crash

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * Dispatch 2026-09-28-658, scout item J1: [CrashLogPanel] opening a crash report it cannot read
 * shows a plain line, and does not throw out of its composition. Driven through the panel the
 * Settings tab composes, by tapping the report's row in the list, as a person would.
 *
 * The file is listed and then gone, the way a report pruned between the list being read and the
 * tap would be. Before the guarded read, the `readText` inside the panel's effect threw
 * `FileNotFoundException` out of the composition, which fails this test with that exception.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrashLogPanelTest {

    private val composeRule = createComposeRule()
    private val folder = TemporaryFolder()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(folder).around(declareHostActivity).around(composeRule)

    @Test
    fun `opening a crash report that can no longer be read says so instead of crashing`() {
        val report = File(folder.root, "not-a-timestamp.txt").apply { writeText("trace") }
        composeRule.setContent { ForagerTheme { CrashLogPanel(files = listOf(report), onBack = {}) } }
        check(report.delete()) { "precondition: the report is gone before it is opened" }

        composeRule.onNodeWithText(report.name).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTextCount(CRASH_LOG_UNREADABLE_TEXT) == 1
        }

        composeRule.onNodeWithText(CRASH_LOG_UNREADABLE_TEXT).assertIsDisplayed()
    }

    @Test
    fun `a readable crash report shows its text`() {
        val report = File(folder.root, "readable.txt").apply { writeText("java.lang.IllegalStateException: boom") }
        composeRule.setContent { ForagerTheme { CrashLogPanel(files = listOf(report), onBack = {}) } }

        composeRule.onNodeWithText(report.name).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTextCount("java.lang.IllegalStateException: boom") == 1
        }

        composeRule.onNodeWithText("java.lang.IllegalStateException: boom").assertIsDisplayed()
    }

    @Test
    fun `the guarded read returns the text, or null for a file that is not there`() {
        val present = File(folder.root, "present.txt").apply { writeText("trace") }
        assertEquals("trace", readCrashLog(present))
        assertNull(readCrashLog(File(folder.root, "absent.txt")))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}

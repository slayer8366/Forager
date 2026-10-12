package com.zynergylabs.forager.app.ui.diagnostics

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.diagnostics.DiagnosticsLog
import com.zynergylabs.forager.app.diagnostics.walklog.WalkLoggerSwitch
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The Diagnostics screen's "Walk logger" toggle (dispatch 2026-09-28-532, Amendment 3, RECORD -560:
 * "A switch, off by default"), driven with real touches, as `DiagnosticsSyntheticForecastSwitchTest`
 * drives the toggle above it. Debug build only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class DiagnosticsWalkLoggerSwitchTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private lateinit var context: Application
    private lateinit var photosDir: File
    private lateinit var capturesDir: File
    private lateinit var log: DiagnosticsLog

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        photosDir = File(context.filesDir, PHOTOS_DIRECTORY).apply { mkdirs() }
        capturesDir = File(context.filesDir, CAPTURES_DIRECTORY).apply { mkdirs() }
        log = DiagnosticsLog(File(File(context.filesDir, DiagnosticsLog.DIRECTORY_NAME), DiagnosticsLog.FILE_NAME))
    }

    private class RecordingSwitch(var stored: Boolean = false, var failWrites: Boolean = false) : WalkLoggerSwitch {
        val writes = mutableListOf<Boolean>()
        override suspend fun isWalkLoggerEnabled(): Result<Boolean> = Result.success(stored)
        override suspend fun setWalkLoggerEnabled(enabled: Boolean): Result<Unit> {
            if (failWrites) return Result.failure(IOException("unwritable"))
            writes += enabled
            stored = enabled
            return Result.success(Unit)
        }
    }

    private fun setPanel(switch: WalkLoggerSwitch) {
        composeRule.setContent {
            DiagnosticsPanel(photosDir = photosDir, capturesDir = capturesDir, log = log, onBack = {}, walkLoggerSwitch = switch)
        }
        awaitToggle()
    }

    private fun awaitToggle() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DIAGNOSTICS_WALK_LOGGER_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun toggleState(): ToggleableState? =
        composeRule.onNodeWithTag(DIAGNOSTICS_WALK_LOGGER_TAG).fetchSemanticsNode().config
            .getOrElseNullable(SemanticsProperties.ToggleableState) { null }

    private fun touchToggle() {
        composeRule.onNodeWithTag(DIAGNOSTICS_WALK_LOGGER_TAG).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    @Test
    fun `the toggle is labelled Walk logger, above the log row, showing the stored state`() {
        setPanel(RecordingSwitch(stored = false))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DIAGNOSTICS_LOG_ROW_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals("Walk logger", WALK_LOGGER_TOGGLE_LABEL)
        val label = composeRule.onNodeWithText(WALK_LOGGER_TOGGLE_LABEL).getUnclippedBoundsInRoot()
        val toggle = composeRule.onNodeWithTag(DIAGNOSTICS_WALK_LOGGER_TAG).getUnclippedBoundsInRoot()
        assertTrue("the label is on the toggle's row ($label in $toggle)", label.top >= toggle.top && label.bottom <= toggle.bottom)
        assertEquals(ToggleableState.Off, toggleState())
        val logRow = composeRule.onNodeWithTag(DIAGNOSTICS_LOG_ROW_TAG).getUnclippedBoundsInRoot()
        assertTrue("the toggle is above the log row ($toggle, $logRow)", toggle.bottom <= logRow.top)
    }

    @Test
    fun `a real touch turns the switch on and stores it, and a second turns it off`() {
        val switch = RecordingSwitch(stored = false)
        setPanel(switch)

        touchToggle()
        assertEquals(listOf(true), switch.writes)
        assertEquals(ToggleableState.On, toggleState())

        touchToggle()
        assertEquals(listOf(true, false), switch.writes)
        assertEquals(ToggleableState.Off, toggleState())
    }

    @Test
    fun `a write that fails leaves the toggle showing what is stored`() {
        val switch = RecordingSwitch(stored = false, failWrites = true)
        setPanel(switch)

        touchToggle()

        assertEquals(emptyList<Boolean>(), switch.writes)
        assertEquals(ToggleableState.Off, toggleState())
    }

    /** Through the panel's real entry point: the toggle writes the app container's own switch, the one the logger reads. */
    @Test
    fun `the panel's entry point shows the app's own switch, and a touch turns the logger on`() {
        val switch = (context as ForagerApplication).container.forecastCellStore as WalkLoggerSwitch
        assertEquals(false, runBlocking { switch.isWalkLoggerEnabled().getOrThrow() })

        composeRule.setContent { DiagnosticsPanel(onBack = {}) }
        awaitToggle()
        assertEquals(ToggleableState.Off, toggleState())
        touchToggle()
        composeRule.waitUntil(timeoutMillis = 5_000) { toggleState() == ToggleableState.On }

        assertEquals(true, runBlocking { switch.isWalkLoggerEnabled().getOrThrow() })
    }
}

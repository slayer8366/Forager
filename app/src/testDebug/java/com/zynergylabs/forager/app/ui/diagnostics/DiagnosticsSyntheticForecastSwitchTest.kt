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
import com.zynergylabs.forager.app.domain.ForecastAvailability
import com.zynergylabs.forager.app.forecast.SyntheticForecastSwitch
import java.io.File
import java.io.IOException
import java.time.LocalDate
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
 * The Diagnostics screen's first toggle, "Synthetic forecast layers" (map layers L0b, B6; owner:
 * "Diagnostics screen (Recommended)", "Debug-only source (Recommended)"), driven with real touches.
 * Debug build only: the panel and the switch live in `src/debug`, and this class runs under
 * `testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class DiagnosticsSyntheticForecastSwitchTest {

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
        // Not the production log file, for the reason DiagnosticsPanelTest.setUp gives.
        log = DiagnosticsLog(File(File(context.filesDir, DiagnosticsLog.DIRECTORY_NAME), DiagnosticsLog.FILE_NAME))
    }

    private class RecordingSwitch(var stored: Boolean = false, var failWrites: Boolean = false) : SyntheticForecastSwitch {
        val writes = mutableListOf<Boolean>()
        override suspend fun isEnabled(): Result<Boolean> = Result.success(stored)
        override suspend fun setEnabled(enabled: Boolean): Result<Unit> {
            if (failWrites) return Result.failure(IOException("unwritable"))
            writes += enabled
            stored = enabled
            return Result.success(Unit)
        }
    }

    private fun setPanel(switch: SyntheticForecastSwitch) {
        composeRule.setContent {
            DiagnosticsPanel(photosDir = photosDir, capturesDir = capturesDir, log = log, onBack = {}, syntheticForecastSwitch = switch)
        }
        awaitToggle()
    }

    private fun awaitToggle() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DIAGNOSTICS_SYNTHETIC_FORECAST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun toggleState(): ToggleableState? =
        composeRule.onNodeWithTag(DIAGNOSTICS_SYNTHETIC_FORECAST_TAG).fetchSemanticsNode().config
            .getOrElseNullable(SemanticsProperties.ToggleableState) { null }

    private fun touchToggle() {
        composeRule.onNodeWithTag(DIAGNOSTICS_SYNTHETIC_FORECAST_TAG).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    @Test
    fun `the first toggle is Synthetic forecast layers, above the log row, showing the stored state`() {
        setPanel(RecordingSwitch(stored = false))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DIAGNOSTICS_LOG_ROW_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals("Synthetic forecast layers", SYNTHETIC_FORECAST_TOGGLE_LABEL)
        val label = composeRule.onNodeWithText(SYNTHETIC_FORECAST_TOGGLE_LABEL).getUnclippedBoundsInRoot()
        val toggle = composeRule.onNodeWithTag(DIAGNOSTICS_SYNTHETIC_FORECAST_TAG).getUnclippedBoundsInRoot()
        assertTrue("the label is on the toggle's row ($label in $toggle)", label.top >= toggle.top && label.bottom <= toggle.bottom)
        assertEquals(ToggleableState.Off, toggleState())
        val logRow = composeRule.onNodeWithTag(DIAGNOSTICS_LOG_ROW_TAG).getUnclippedBoundsInRoot()
        assertTrue("the toggle is above the log row ($toggle, $logRow)", toggle.bottom <= logRow.top)
    }

    @Test
    fun `a real touch on the toggle turns the switch on and stores it, and a second turns it off`() {
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
        assertEquals("the toggle does not claim a state that was not stored", ToggleableState.Off, toggleState())
    }

    /**
     * Through the panel's real entry point: the switch it shows is the app container's forecast store
     * (planner's ruling on Q12), so a touch there is what makes the Maps tab's store report data.
     */
    @Test
    fun `the panel's entry point shows the app's own store, and turning it on gives the store data`() {
        val store = (context as ForagerApplication).container.forecastCellStore
        val week = LocalDate.of(2026, 9, 28)
        assertEquals(ForecastAvailability.NoForecastData, runBlocking { store.availability(week) })

        composeRule.setContent { DiagnosticsPanel(onBack = {}) }
        awaitToggle()
        assertEquals(ToggleableState.Off, toggleState())
        touchToggle()
        composeRule.waitUntil(timeoutMillis = 5_000) { toggleState() == ToggleableState.On }

        assertTrue(runBlocking { store.availability(week) } is ForecastAvailability.Groups)
    }
}

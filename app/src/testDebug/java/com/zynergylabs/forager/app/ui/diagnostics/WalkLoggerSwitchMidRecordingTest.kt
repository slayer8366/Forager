package com.zynergylabs.forager.app.ui.diagnostics

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.diagnostics.DiagnosticsLog
import com.zynergylabs.forager.app.diagnostics.WalkLogger
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.service.TrackRecordingService
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

/**
 * The walk logger follows its switch during a recording (dispatch 2026-09-28-796; the owner, RECORD
 * -795: "Start straight away (Recommended)"). On the L3 walk the switch was turned on 61 s after
 * Record and nothing was logged, because the switch was read only at the start. Driven through the
 * real entry points: the real recording service started with the switch off, then real touches on
 * the Diagnostics panel's own toggle, which writes the app container's real switch. Debug build only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class WalkLoggerSwitchMidRecordingTest {

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
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = (context as ForagerApplication).container
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val shadowLocationManager = shadowOf(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    }

    @Test
    fun `switched on mid-recording the log starts at once, switched off it stops, and each start says the switch in diagnostics log`() {
        val trackId = "walklog-midway"
        runBlocking {
            container.trackRepository.create(Track(id = trackId, name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList())).getOrThrow()
        }
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
        try {
            controller.create().startCommand(0, 1)
            assertTrue(
                "diagnostics.log says the switch was off at the start: ${diagnosticsLog().read()}",
                await { diagnosticsLog().read().contains("Walk logger switch at recording start: off (track '$trackId')") },
            )
            assertFalse("off at the start: no log", WalkLogger.of(context).isLogging)

            composeRule.setContent { DiagnosticsPanel(onBack = {}) }
            composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithTag(DIAGNOSTICS_WALK_LOGGER_TAG).fetchSemanticsNodes().isNotEmpty() }
            touchToggle()

            assertTrue("switched on mid-recording, the log starts for that recording", await { WalkLogger.of(context).isLogging })
            val file = walkLogFiles().single()
            assertEquals("# walklog version=1", file.readLines().first())
            assertTrue("a wake lock while logging", ShadowPowerManager.getLatestWakeLock()?.isHeld == true)

            touchToggle()

            assertTrue("switched off, the log stops", await { !WalkLogger.of(context).isLogging })
            assertEquals("END reason=switched-off", file.readLines().last().substringAfter(' '))
            assertFalse("the wake lock is let go", ShadowPowerManager.getLatestWakeLock()?.isHeld == true)
            assertTrue("the recording carries on", container.returnWatch.state.value.let { it.isBegun && it.trackId == trackId })
        } finally {
            end(controller)
            runBlocking { (container.forecastCellStore as com.zynergylabs.forager.app.diagnostics.walklog.WalkLoggerSwitch).setWalkLoggerEnabled(false) }
        }
    }

    private fun touchToggle() {
        composeRule.onNodeWithTag(DIAGNOSTICS_WALK_LOGGER_TAG).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    private fun diagnosticsLog() = DiagnosticsLog.forContext(context)

    private fun walkLogFiles(): List<File> =
        File(context.getExternalFilesDir(null) ?: context.filesDir, WalkLogger.DIRECTORY_NAME).listFiles()?.filter { it.isFile }.orEmpty()

    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    private fun await(timeoutMillis: Long = 10_000L, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            idleAndSettle()
        }
        return condition()
    }

    private fun end(controller: ServiceController<TrackRecordingService>) {
        val service = controller.get()
        if (!shadowOf(service).isStoppedBySelf) {
            controller.withIntent(Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP)).startCommand(0, 99)
            val deadline = System.currentTimeMillis() + 5_000L
            while (!shadowOf(service).isStoppedBySelf && System.currentTimeMillis() < deadline) Thread.sleep(25L)
        }
        controller.destroy()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(250L)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun idleAndSettle() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(25L)
        shadowOf(Looper.getMainLooper()).idle()
    }
}

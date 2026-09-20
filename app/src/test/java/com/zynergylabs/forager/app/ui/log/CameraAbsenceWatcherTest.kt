package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * [CameraAbsenceWatcher]: that a real lifecycle actually drives the two callbacks, and with the
 * clock reading this test controls. The pure decision and the ViewModel's handling of it are in
 * [CameraAbsenceTest]; what this class adds is the wiring, which is the part no pure test can see.
 *
 * **The clock is injected rather than pinned in the harness**, so the under- and over-threshold
 * cases are different numbers in the test body. The failure the dispatch named — a silently failing
 * pin leaving every case at one instant — cannot happen here, and the `readings` list is asserted
 * so a watcher that stopped consulting the clock at all would fail rather than pass on a default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraAbsenceWatcherTest {

    // This composable's only dependency is a LifecycleOwner, which the test provides itself — but a
    // compose rule still launches a host Activity, and Robolectric will not resolve one that the
    // package manager has never heard of. Same rule the other camera test classes use.
    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.STARTED, UnconfinedTestDispatcher())

    private val left = mutableListOf<Long>()
    private val returned = mutableListOf<Long>()
    private val readings = mutableListOf<Long>()
    private var clock = 0L

    private fun setWatcher() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                CameraAbsenceWatcher(
                    onLeftApp = { left += it },
                    onReturnedToApp = { returned += it },
                    elapsedMillis = { clock.also { reading -> readings += reading } },
                )
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * `addObserver` replays events to bring a new observer up to the owner's current state, so
     * composing this watcher over an already-STARTED lifecycle fires one `ON_START` immediately.
     * That is benign in production — see the replay test below — but it is a real reading, and the
     * tests that measure an absence clear it first rather than pretending it did not happen.
     */
    private fun clearReplay() {
        left.clear()
        returned.clear()
        readings.clear()
    }

    @Test
    fun `entering composition replays ON_START, and it is harmless because no departure is recorded`() {
        setWatcher()

        assertEquals("the replay is one ON_START, not an ON_STOP", emptyList<Long>(), left)
        assertEquals(listOf(0L), returned)

        // What the replay reaches in production: a freshly opened camera has no recorded departure,
        // so the return is a no-op rather than an absence of zero being measured against anything.
        val viewModel = InAppCameraViewModel()
        viewModel.open(InAppCameraTarget.LOG_ENTRY)
        viewModel.onReturnedToApp(returned.single())
        assertEquals(InAppCameraTarget.LOG_ENTRY, viewModel.target.value)
    }

    @Test
    fun `ON_STOP reports the departure and ON_START the return, each with the clock's reading`() {
        setWatcher()
        clearReplay()

        clock = 1_000
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        clock = 5_000
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        composeRule.waitForIdle()

        assertEquals(listOf(1_000L), left)
        assertEquals(listOf(5_000L), returned)
        assertEquals("the watcher must actually consult the clock, not a default", listOf(1_000L, 5_000L), readings)
    }

    @Test
    fun `an over-threshold absence and an under-threshold one are different readings, not the same instant`() {
        setWatcher()
        clearReplay()

        clock = 1_000
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        clock = 1_000 + CAMERA_ABSENCE_TIMEOUT_MILLIS
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        composeRule.waitForIdle()

        val away = returned.single() - left.single()
        assertEquals(CAMERA_ABSENCE_TIMEOUT_MILLIS, away)
        // The positive control: this is the reading the decision function is given, and it is on the
        // closing side of the threshold. A harness that reported one instant for both would give 0.
        org.junit.Assert.assertTrue(cameraClosesAfterAbsence(left.single(), returned.single()))
    }

    @Test
    fun `a pause without a stop is not a departure`() {
        setWatcher()
        clearReplay()

        clock = 1_000
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        composeRule.waitForIdle()

        assertEquals("a permission dialog pauses without stopping, and is not the user leaving", emptyList<Long>(), left)
    }

    /**
     * The camera closing takes the watcher out of composition, since `MainActivity` composes it
     * only while the target is non-null. Driven by state rather than a second `setContent`, which a
     * compose rule does not allow.
     */
    @Test
    fun `leaving composition removes the observer, so a closed camera measures nothing`() {
        var composed by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                if (composed) {
                    CameraAbsenceWatcher(
                        onLeftApp = { left += it },
                        onReturnedToApp = { returned += it },
                        elapsedMillis = { clock },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Precondition: while composed, the observer really is attached — otherwise the assertion
        // below would pass on a watcher that never worked at all.
        clock = 1_000
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        composeRule.waitForIdle()
        assertEquals(listOf(1_000L), left)

        composed = false
        composeRule.waitForIdle()

        clock = 9_000
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        composeRule.waitForIdle()

        assertEquals("no further departures once the camera is closed", listOf(1_000L), left)
    }
}

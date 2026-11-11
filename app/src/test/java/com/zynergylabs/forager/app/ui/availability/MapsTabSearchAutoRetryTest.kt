package com.zynergylabs.forager.app.ui.availability

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-11-01, F2 and F3 (Sorrel's review; the owner's E2 "Only with Forager on screen (Recommended)" and E3 "3 tries,
 * then wait for me (Recommended)"), through the real screen and ViewModel ([RealSearchScreenRig]) with the raw signal driven by
 * the test and the Activity's lifecycle moved by the test. Returns are spaced more than [SIGNAL_RETURN_MIN_INTERVAL_MS] apart
 * where the test means each to count, so the 10 s spacing is not what is being measured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchAutoRetryTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** MainActivity's configChanges, so a turn reaches the same Activity (the LandscapeSearchFixesTest precedent). */
    private val rotatesInPlace = object : org.junit.rules.ExternalResource() {
        override fun before() {
            val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
            val info = android.content.pm.ActivityInfo().apply {
                packageName = app.packageName
                name = ComponentActivity::class.java.name
                configChanges = android.content.pm.ActivityInfo.CONFIG_ORIENTATION or android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE or
                    android.content.pm.ActivityInfo.CONFIG_SCREEN_LAYOUT or android.content.pm.ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
                    android.content.pm.ActivityInfo.CONFIG_KEYBOARD_HIDDEN
            }
            Shadows.shadowOf(app.packageManager).addOrUpdateActivity(info)
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(rotatesInPlace).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private fun search() = rig.searchCoordinates("45.3736", "-121.6959")

    private fun signal(validated: Boolean) {
        rig.networkValidated.value = validated
        composeRule.waitForIdle()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    private fun pass(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        composeRule.waitForIdle()
    }

    /** The signal goes, comes back, and the spacing passes, so the next return counts on its own. */
    private fun returnOfSignal() {
        signal(false)
        signal(true)
        pass(SIGNAL_RETURN_MIN_INTERVAL_MS + 1_000)
    }

    /** The screen, no signal, and a search that failed. */
    private fun failedWithNoSignal() {
        rig.setScreen()
        signal(false)
        rig.sightingsFailure = IOException("no signal (test)")
        search()
        assertEquals("positive control: one fetch, failed", 1, rig.sightingsFetches.size)
    }

    private fun lifecycle(state: Lifecycle.State) {
        composeRule.activityRule.scenario.moveToState(state)
        composeRule.waitForIdle()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    @Test
    fun `a return of signal while the fetch is still running runs the search again once when that fetch fails`() {
        rig.setScreen()
        signal(false)
        rig.sightingsFailure = IOException("no signal (test)")
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        search()
        assertEquals("positive control: the fetch is running", true, rig.viewModel.uiState.value.isLoadingSightings)

        signal(true)
        rig.sightingsGate = null
        gate.complete(Unit)
        rig.settle()

        assertEquals("a return during the running fetch was lost when the fetch failed", 2, rig.sightingsFetches.size)
        pass(3 * SIGNAL_RETURN_MIN_INTERVAL_MS)
        assertEquals("the held return ran the search more than once", 2, rig.sightingsFetches.size)
    }

    @Test
    fun `a return while Forager is in the background runs nothing until it comes back, then once`() {
        failedWithNoSignal()
        lifecycle(Lifecycle.State.STARTED)

        signal(true)
        pass(3 * SIGNAL_RETURN_MIN_INTERVAL_MS)
        assertEquals("a return in the background ran the search", 1, rig.sightingsFetches.size)

        lifecycle(Lifecycle.State.RESUMED)
        assertEquals("coming back after a return did not run the search", 2, rig.sightingsFetches.size)
        pass(3 * SIGNAL_RETURN_MIN_INTERVAL_MS)
        lifecycle(Lifecycle.State.STARTED)
        lifecycle(Lifecycle.State.RESUMED)
        assertEquals("coming back again with no new return ran the search again", 2, rig.sightingsFetches.size)
    }

    @Test
    fun `coming back with a failure and good signal, and no return while away, runs nothing`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("server error (test)")
        search()
        lifecycle(Lifecycle.State.STARTED)
        lifecycle(Lifecycle.State.RESUMED)
        pass(3 * SIGNAL_RETURN_MIN_INTERVAL_MS)
        assertEquals("coming back ran a failure with good signal by itself", 1, rig.sightingsFetches.size)
    }

    @Test
    fun `three automatic tries, then only Try again or a new search runs it, and each starts the count again`() {
        failedWithNoSignal()
        repeat(5) { returnOfSignal() }
        assertEquals("five returns did not make exactly three automatic tries", 1 + 3, rig.sightingsFetches.size)

        rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
        assertEquals("Try again did not run the search", 5, rig.sightingsFetches.size)
        repeat(5) { returnOfSignal() }
        assertEquals("after Try again the returns did not make three more automatic tries", 5 + 3, rig.sightingsFetches.size)

        search()
        assertEquals("the new search did not fetch", 9, rig.sightingsFetches.size)
        repeat(5) { returnOfSignal() }
        assertEquals("after a new search the returns did not make three more automatic tries", 9 + 3, rig.sightingsFetches.size)
    }

    @Test
    fun `a turn of the phone keeps the count of automatic tries`() {
        failedWithNoSignal()
        repeat(2) { returnOfSignal() }
        assertEquals("positive control: two automatic tries", 3, rig.sightingsFetches.size)

        composeRule.activityRule.scenario.onActivity { RuntimeEnvironment.setQualifiers("w823dp-h384dp-land-xxhdpi") }
        rig.settle()
        repeat(3) { returnOfSignal() }

        assertEquals("after a turn the count of automatic tries started again", 4, rig.sightingsFetches.size)
    }
}

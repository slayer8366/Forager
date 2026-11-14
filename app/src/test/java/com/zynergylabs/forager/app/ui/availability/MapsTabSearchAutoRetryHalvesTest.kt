package com.zynergylabs.forager.app.ui.availability

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-11-01, Amendment V. The owner's rulings: "3 tries, then wait for me"; "Growing: 3, then 10"; "Same as the map's
 * half (Recommended)". The automatic tries when the two halves of one search end at different times, through the real screen,
 * the real ViewModel and the real signal flow.
 *
 * - VA1 (Sorrel's trace, from the code): an automatic try of a search whose halves both failed runs both again (`refresh`). If the
 *   map's half comes back first while the list still loads, the gate saw nothing failed and nothing running and started its
 *   count again; the list then failing got no 3 s and 10 s tries, and the next return of signal a fresh three. Both orderings.
 * - VA2: a return of signal while only the list still loads is held, and runs once if the list then fails, as F2 does for the
 *   map's half.
 *
 * Every test ends with the three tries spent, or its gates completed, so no try is left pending.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchAutoRetryHalvesTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

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

    /** The signal goes, comes back after the spacing, so the return counts on its own. */
    private fun signalReturnsAgain() {
        signal(false)
        pass(SIGNAL_RETURN_MIN_INTERVAL_MS + 1_000)
        signal(true)
    }

    /** A search with no signal whose two halves both fail. */
    private fun bothFailed() {
        rig.setScreen()
        signal(false)
        rig.sightingsFailure = IOException("no signal (test)")
        rig.availabilityFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        val state = rig.viewModel.uiState.value
        assertTrue("positive control: the map's half failed", state.sightingsErrorMessage != null)
        assertEquals("positive control: the list failed", LIST_SEARCH_FAILED_MESSAGE, state.errorMessage)
    }

    /**
     * The first automatic try, on the signal's return, with the map's half to work and the list to fail. [mapFirst]: the map's
     * half comes back while the list is held, then the list fails; otherwise the list fails while the map's half is held.
     */
    private fun firstTryHalvesApart(mapFirst: Boolean) {
        rig.sightingsFailure = null
        val gate = CompletableDeferred<Unit>()
        if (mapFirst) rig.availabilityGate = gate else rig.sightingsGate = gate
        val lists = rig.availabilityFetches.size
        try {
            signal(true)
            assertEquals("positive control: the return of signal ran the list's half again", lists + 1, rig.availabilityFetches.size)
            val state = rig.viewModel.uiState.value
            if (mapFirst) {
                assertTrue("positive control: the map's half came back first", !state.isLoadingSightings && state.sightingsErrorMessage == null && state.isLoading)
            } else {
                assertEquals("positive control: the list failed first", LIST_SEARCH_FAILED_MESSAGE, state.errorMessage)
                assertTrue("positive control: the map's half still loads", state.isLoadingSightings)
            }
        } finally {
            rig.availabilityGate = null
            rig.sightingsGate = null
            gate.complete(Unit)
            rig.settle()
        }
        val state = rig.viewModel.uiState.value
        assertEquals("positive control: the list failed", LIST_SEARCH_FAILED_MESSAGE, state.errorMessage)
        assertTrue("positive control: the map's half loaded", state.sightingsErrorMessage == null && !state.isLoadingSightings)
    }

    private fun timedTriesFollow(order: String, mapFirst: Boolean) {
        bothFailed()
        val lists = rig.availabilityFetches.size
        firstTryHalvesApart(mapFirst)
        pass(3_500)
        assertEquals("VA1 ($order): no timed try of the list 3 s after the first automatic try's list failed", lists + 2, rig.availabilityFetches.size)
        pass(10_500)
        assertEquals("VA1 ($order): no timed try of the list 10 s after the second", lists + 3, rig.availabilityFetches.size)
        pass(60_000)
        assertEquals("VA1 ($order): a fourth automatic try ran", lists + 3, rig.availabilityFetches.size)
    }

    private fun neverMoreThanThree(order: String, mapFirst: Boolean) {
        bothFailed()
        val lists = rig.availabilityFetches.size
        firstTryHalvesApart(mapFirst)
        pass(30_000)
        repeat(4) {
            signalReturnsAgain()
            pass(30_000)
        }
        assertTrue(
            "VA1 ($order): one failed search got ${rig.availabilityFetches.size - lists} automatic tries, more than three",
            rig.availabilityFetches.size - lists <= SEARCH_AUTO_RETRY_MAX_TRIES,
        )
    }

    @Test
    fun `map back first then the list fails - the 3 s and 10 s tries follow, three in all`() = timedTriesFollow("map back first", mapFirst = true)

    @Test
    fun `map back first then the list fails - one failed search never gets more than three automatic tries`() = neverMoreThanThree("map back first", mapFirst = true)

    @Test
    fun `list fails first then the map comes back - the 3 s and 10 s tries follow, three in all`() = timedTriesFollow("list failed first", mapFirst = false)

    @Test
    fun `list fails first then the map comes back - one failed search never gets more than three automatic tries`() = neverMoreThanThree("list failed first", mapFirst = false)

    @Test
    fun `a return of signal while only the list loads is held, and runs once when the list fails`() {
        rig.setScreen()
        signal(false)
        rig.availabilityFailure = IOException("no signal (test)")
        val gate = CompletableDeferred<Unit>()
        rig.availabilityGate = gate
        var lists = 0
        try {
            rig.searchCoordinates("45.3736", "-121.6959")
            lists = rig.availabilityFetches.size
            val state = rig.viewModel.uiState.value
            assertTrue("positive control: the map's half loaded and the list still loads", !state.isLoadingSightings && state.sightingsErrorMessage == null && state.isLoading)
            signal(true)
        } finally {
            rig.availabilityGate = null
            gate.complete(Unit)
            rig.settle()
        }
        assertEquals("positive control: the list failed", LIST_SEARCH_FAILED_MESSAGE, rig.viewModel.uiState.value.errorMessage)
        pass(500)
        assertEquals("VA2: the return of signal while the list loaded was not run once when the list failed", lists + 1, rig.availabilityFetches.size)
        // The held return ran as the first automatic try; the timed two follow, then nothing.
        pass(3_500)
        pass(10_500)
        pass(60_000)
        assertEquals("VA2: the held return's try did not count as the first of three", lists + 3, rig.availabilityFetches.size)
    }
}

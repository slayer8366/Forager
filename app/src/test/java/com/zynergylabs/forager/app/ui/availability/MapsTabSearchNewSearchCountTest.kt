package com.zynergylabs.forager.app.ui.availability

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.IOException
import java.time.Duration
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
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
 * Dispatch 2026-11-11-01, Amendment W. The owner's ruling: "3 tries, then wait for me"; the rule built under it
 * (`SearchAutoRetry.kt`): the count starts again on "Try again", on a new search and on a success.
 *
 * - WA1 (Sorrel's trace): with the list's own fetch counted as running (Amendment V), a new search whose list is still loading
 *   when its map's half fails no longer starts the count again, so after three tries spent, a new search that fails gets none.
 *   Each way a new search starts that the rig reaches: the bar's coordinates (real touches), Search (locate), a month and a
 *   recent search. The last three are in the search panel, whose month menu is a popup the rig's root touch does not reach,
 *   so those are clicked semantically: the claim is about the count, not about where a finger lands.
 * - WA1 with the list failing first: the new search's list fails at once, before its map's half fails.
 * - WA2: a timed try waiting from the old search is dropped by a new search.
 * - WA4: the map's half failing while the list still loads, with a return of signal then: tried at once.
 *
 * Every test ends its gates in a `finally`; no automatic try is left waiting past a test's last check that matters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchNewSearchCountTest {

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

    /** The signal goes, comes back, and the spacing passes, so the next return counts on its own. */
    private fun returnOfSignal() {
        signal(false)
        signal(true)
        pass(SIGNAL_RETURN_MIN_INTERVAL_MS + 1_000)
    }

    /** A search whose map's half failed with no signal (its list loads), then its three automatic tries spent. */
    private fun threeTriesSpent() {
        rig.setScreen()
        signal(false)
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertEquals("positive control: one fetch, failed", 1, rig.sightingsFetches.size)
        repeat(5) { returnOfSignal() }
        assertEquals("positive control: five returns made exactly three automatic tries", 1 + 3, rig.sightingsFetches.size)
    }

    private fun monthName(month: Int) = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())

    /** The ways a new search starts that the rig reaches. */
    private enum class Entry { BAR, LOCATE, MONTH, RECENT }

    private fun newSearch(entry: Entry) {
        when (entry) {
            Entry.BAR -> rig.searchCoordinates("45.5000", "-121.5000")
            Entry.LOCATE -> {
                rig.tapBar()
                composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_TAG).performScrollTo().performClick()
                rig.settle()
            }
            Entry.MONTH -> {
                rig.tapBar()
                val next = rig.viewModel.uiState.value.selectedMonth % 12 + 1
                composeRule.onNode(hasClickAction() and hasText("Month")).performScrollTo().performClick()
                rig.settle()
                composeRule.onNode(hasAnyAncestor(hasTestTag(MONTH_MENU_TAG)) and hasText(monthName(next))).performScrollTo().performClick()
                rig.settle()
                assertEquals("positive control: the month changed", next, rig.viewModel.uiState.value.selectedMonth)
            }
            Entry.RECENT -> {
                rig.tapBar()
                composeRule.onNodeWithText("Recent searches").performScrollTo().performClick()
                rig.settle()
                val state = rig.viewModel.uiState.value
                assertTrue("positive control: a recent search is saved", state.recentSearches.isNotEmpty())
                val recent = state.recentSearches.first()
                val line = "${recent.filter.label} · ${monthName(recent.month)}"
                composeRule.onNode(hasClickAction() and hasText(line, substring = true)).performScrollTo().performClick()
                rig.settle()
            }
        }
    }

    /**
     * WA1: three tries spent, then a new search by [entry] whose list is held while its map's half fails; returns of signal then
     * make three more tries.
     */
    private fun newSearchStartsTheCount(entry: Entry) {
        threeTriesSpent()
        val gate = CompletableDeferred<Unit>()
        rig.availabilityGate = gate
        try {
            val before = rig.sightingsFetches.size
            newSearch(entry)
            assertEquals("positive control ($entry): the new search fetched the map's half once", before + 1, rig.sightingsFetches.size)
            val state = rig.viewModel.uiState.value
            assertTrue("positive control ($entry): the map's half failed and the list still loads", state.sightingsErrorMessage != null && state.isLoading)
            repeat(5) { returnOfSignal() }
            assertEquals("WA1 ($entry): after a new search that failed, returns of signal did not make three more automatic tries", before + 1 + 3, rig.sightingsFetches.size)
        } finally {
            rig.availabilityGate = null
            gate.complete(Unit)
            rig.settle()
        }
    }

    @Test
    fun `a new search from the bar starts the count again while its list still loads`() = newSearchStartsTheCount(Entry.BAR)

    @Test
    fun `a new search from Search (locate) starts the count again while its list still loads`() = newSearchStartsTheCount(Entry.LOCATE)

    @Test
    fun `a new month starts the count again while its list still loads`() = newSearchStartsTheCount(Entry.MONTH)

    @Test
    fun `a recent search starts the count again while its list still loads`() = newSearchStartsTheCount(Entry.RECENT)

    @Test
    fun `a new search whose list fails first starts the count again`() {
        threeTriesSpent()
        rig.availabilityFailure = IOException("no signal (test)")
        val before = rig.sightingsFetches.size
        rig.searchCoordinates("45.5000", "-121.5000")
        val state = rig.viewModel.uiState.value
        assertTrue("positive control: both halves of the new search failed", state.sightingsErrorMessage != null && state.errorMessage == LIST_SEARCH_FAILED_MESSAGE)
        repeat(5) { returnOfSignal() }
        assertEquals("WA1 (list failed first): after a new search that failed, returns of signal did not make three more automatic tries", before + 1 + 3, rig.sightingsFetches.size)
    }

    @Test
    fun `a timed try waiting from the old search is dropped by a new search`() {
        rig.setScreen()
        signal(false)
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        signal(true)
        assertEquals("positive control: the return of signal ran the first automatic try", 2, rig.sightingsFetches.size)
        // The first automatic try failed; its timed try is due 3 s later. A new search comes first, and fails too.
        val gate = CompletableDeferred<Unit>()
        rig.availabilityGate = gate
        try {
            rig.searchCoordinates("45.5000", "-121.5000")
            assertEquals("positive control: the new search fetched the map's half once", 3, rig.sightingsFetches.size)
            pass(3_500)
            pass(10_500)
            assertEquals("WA2: the old search's timed try ran on the new search, with no return of signal", 3, rig.sightingsFetches.size)
        } finally {
            rig.availabilityGate = null
            gate.complete(Unit)
            rig.settle()
        }
    }

    @Test
    fun `the map's half failing while the list still loads is tried at once on a return of signal`() {
        rig.setScreen()
        signal(false)
        rig.sightingsFailure = IOException("no signal (test)")
        val gate = CompletableDeferred<Unit>()
        rig.availabilityGate = gate
        try {
            rig.searchCoordinates("45.3736", "-121.6959")
            val state = rig.viewModel.uiState.value
            assertTrue("positive control: the map's half failed and the list still loads", state.sightingsErrorMessage != null && state.isLoading)
            signal(true)
            assertEquals("WA4: a return of signal with the map's half failed and the list still loading did not try the map at once", 2, rig.sightingsFetches.size)
        } finally {
            rig.availabilityGate = null
            gate.complete(Unit)
            rig.settle()
        }
    }
}

package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.NO_SIGNAL_SWITCH_DELAY_MS
import com.zynergylabs.forager.app.domain.NetworkSignal
import com.zynergylabs.forager.app.domain.SIGNAL_RETURN_SWITCH_DELAY_MS
import com.zynergylabs.forager.app.map.OFFLINE_STYLE_ATTRIBUTION
import com.zynergylabs.forager.app.map.STREET_STYLE_URL
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Trail Map is tried again when the connection returns (dispatch 2026-10-27-09 Amendment 1, RECORD -1739; the owner:
 * "Yes, retry after 30 s (Recommended)"). Before it, a fallback taken offline with no cached style (the S22, card step 1
 * of docs/audits/2026-10-27-s22-check-v3-8d.md) stayed until the app restarted.
 *
 * Driven through the app's own entry point for a network change, [noSignalState], fed a [NetworkSignal] of the test's
 * own (the interface `AndroidNetworkSignal` implements) and a fallback of the test's own, in virtual time. Robolectric
 * only for `android.util.Log`, which both the state and the fallback write to. Every coroutine here runs in a scope the
 * test ends (`runTest`'s `backgroundScope`, or a [TestScope] cancelled in a `finally`), so no pending delay outlives a
 * failing test (CLAUDE.md: an unstopped timer makes `runTest` spin).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OwnStreetFallbackRetryTest {
    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val trailRequest = MapStyleRequest(MapStyleSource.Uri(STREET_STYLE_URL), useOfflineTiles = false)
    private val fallbackLine =
        "Own Street style failed to load (Failed to load style: offline); drawing OpenStreetMap's Street until the connection returns or the app restarts."
    private val retryLine = "Connection back: trying Forager Trail Map again."

    private class FakeSignal : NetworkSignal {
        override val validated = MutableSharedFlow<Boolean>(replay = 1)
    }

    @Test
    fun `a fallback taken with no signal is retried once the signal has held 30 s, and falls back again if Trail Map still fails`() = runTest {
        val signal = FakeSignal()
        val lines = mutableListOf<String>()
        val fallback = OwnStreetFallback(log = { lines += it })
        val noSignal = noSignalState(signal, backgroundScope, fallback)

        signal.validated.emit(false)
        runCurrent()
        assertTrue("offline at start: no signal at once", noSignal.value)
        assertTrue("Trail Map's style fails offline and falls back", fallback.onStyleLoadFailed(trailRequest, "Failed to load style: offline"))
        assertFalse(fallback.ownStreet())

        signal.validated.emit(true)
        advanceTimeBy(SIGNAL_RETURN_SWITCH_DELAY_MS - 1)
        runCurrent()
        assertFalse("still OpenStreetMap at 29.999 s", fallback.ownStreet())
        advanceTimeBy(1)
        runCurrent()
        assertFalse("signal held 30 s: the no-signal state has ended", noSignal.value)
        assertTrue("Trail Map tried again at 30 s", fallback.ownStreet())
        assertEquals(listOf(fallbackLine, retryLine), lines)

        // Trail Map still cannot load (the Worker down): it quietly falls back again, and logs it.
        assertTrue("a failure after the retry falls back again", fallback.onStyleLoadFailed(trailRequest, "Failed to load style: offline"))
        assertFalse(fallback.ownStreet())
        assertEquals(listOf(fallbackLine, retryLine, fallbackLine), lines)
    }

    @Test
    fun `a fallback taken while the signal holds stays through a drop too short to be no signal`() = runTest {
        val signal = FakeSignal()
        val lines = mutableListOf<String>()
        val fallback = OwnStreetFallback(log = { lines += it })
        val noSignal = noSignalState(signal, backgroundScope, fallback)

        signal.validated.emit(true)
        runCurrent()
        assertFalse("online at start", noSignal.value)
        assertTrue("Trail Map's style fails online (the Worker down)", fallback.onStyleLoadFailed(trailRequest, "Failed to load style: offline"))

        // A drop shorter than the 5 s it takes to be "no signal", then the signal held well past 30 s.
        signal.validated.emit(false)
        advanceTimeBy(NO_SIGNAL_SWITCH_DELAY_MS - 1_000)
        runCurrent()
        signal.validated.emit(true)
        advanceTimeBy(SIGNAL_RETURN_SWITCH_DELAY_MS * 3)
        runCurrent()

        assertFalse("never no signal", noSignal.value)
        assertFalse("still OpenStreetMap: the no-signal state never ended, so nothing is retried", fallback.ownStreet())
        assertEquals("one fallback line, no retry line", listOf(fallbackLine), lines)
    }

    @Test
    fun `every reader of the fallback follows the retry`() {
        val scope = TestScope(StandardTestDispatcher())
        try {
            val signal = FakeSignal()
            val fallback = OwnStreetFallback(log = {})
            val noSignal = noSignalState(signal, scope, fallback)
            scope.emitAndRun(signal, validated = false)

            var normalSelected = Color.Unspecified
            var grey = Color.Unspecified
            var request: AppliedMapStyle? = null
            var credits: List<String> = emptyList()
            var maxZoom = 0
            var navigationZoom = 0.0
            composeRule.setContent {
                ForagerThemeWithNoSignal(darkTheme = false, noSignal = noSignal, offlineRegions = emptyList()) {
                    // Amendment 4 (RECORD -1771): the light theme's normal selected fill is `secondary`.
                    normalSelected = MaterialTheme.colorScheme.secondary
                    grey = MaterialTheme.colorScheme.outlineVariant
                    // Read in composition, as MapStyleReload, SightingsMapLayout and MapNavigationView read it.
                    val ownStreet = fallback.ownStreet()
                    request = requestedMapStyle(Basemap.FORAGER_TRAIL, useOfflineTiles = false, nightMode = false, nightModeLoaded = true, ownStreet = ownStreet)
                    credits = mapCreditsFor(Basemap.FORAGER_TRAIL, useOfflineTiles = false, ownStreet = ownStreet)
                    maxZoom = operatingMaxZoom(Basemap.FORAGER_TRAIL, ownStreet)
                    navigationZoom = navigationMaxZoom(Basemap.FORAGER_TRAIL, navigating = false, ownStreet = ownStreet)
                    MapModeChipRow(mapMode = MapMode.TRAIL, onMapModeSelected = {}, fallback = fallback)
                }
            }
            composeRule.waitForIdle()
            val trailMap = request

            composeRule.runOnIdle { fallback.onStyleLoadFailed(trailRequest, "Failed to load style: offline") }
            composeRule.waitForIdle()
            val fallenBack = request
            assertEquals("fallen back: OpenStreetMap's credit", listOf(Basemap.OSM_STANDARD.attribution), credits)
            assertEquals("fallen back: OpenStreetMap's limit", Basemap.OSM_STANDARD.maxZoom, maxZoom)
            assertEquals("fallen back: the Trail chip grey", grey, recordedFill(MapMode.TRAIL))
            assertTrue("fallen back: the style effect reloads", needsStyleReload(applied = trailMap, requested = fallenBack!!))

            scope.emitAndRun(signal, validated = true)
            scope.advanceTimeBy(SIGNAL_RETURN_SWITCH_DELAY_MS)
            scope.runCurrent()
            composeRule.waitForIdle()

            assertTrue("signal back 30 s: Trail Map draws", fallback.ownStreet())
            assertEquals("Trail Map's credit again", listOf(OFFLINE_STYLE_ATTRIBUTION), credits)
            assertEquals("Trail Map's limit again", Basemap.FORAGER_TRAIL.maxZoom, maxZoom)
            assertEquals(18.0, navigationZoom, 0.0)
            assertEquals("the Trail chip in its normal selected fill", normalSelected, recordedFill(MapMode.TRAIL))
            assertEquals("the request is Trail Map's again", trailMap, request)
            assertTrue("and the style effect reloads to it", needsStyleReload(applied = fallenBack, requested = request!!))
        } finally {
            scope.cancel()
        }
    }

    private fun TestScope.emitAndRun(signal: FakeSignal, validated: Boolean) {
        check(signal.validated.tryEmit(validated))
        runCurrent()
    }

    private fun recordedFill(mode: MapMode): Color =
        composeRule.onNodeWithTag(mapModeChipTag(mode)).fetchSemanticsNode().config[MapModeChipSelectedFill]
}

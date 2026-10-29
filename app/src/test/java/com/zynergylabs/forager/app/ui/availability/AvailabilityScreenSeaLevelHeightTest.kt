package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.NoSeaLevelConverter
import com.zynergylabs.forager.app.domain.PressureProvider
import com.zynergylabs.forager.app.domain.PressureReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-28-13 (RECORD -1792): the strip's height is above sea level, steadied by the barometer, through the
 * real entry point: the real [AvailabilityScreen] and [AvailabilityViewModel] ([RealSearchScreenRig]), fed by a fake
 * tracker, a fake barometer and a converter that puts sea level 21.6 m above the raw height (the geoid's depth at the
 * 2026-10-28 walk, the altitude report, section 2). Every walk here is made up. Metric, so heights read in metres.
 *
 * No test here waits on a loop: the fakes emit what they are given and nothing else, so no virtual time is spun on a
 * sensor (CLAUDE.md, the poll-loop entry).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenSeaLevelHeightTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val fixes = MutableSharedFlow<LocationFix>(replay = 16)
    private val tracker = object : LocationTracker {
        override val fixes: Flow<LocationFix> = this@AvailabilityScreenSeaLevelHeightTest.fixes
    }

    /**
     * A barometer that counts its collections, and whether one is running, as the platform's registration would. With
     * [sources], each new collection reads the next one, as a new sensor registration gives only new readings.
     */
    private class FakeBarometer(private val source: Flow<PressureReading?>, private val sources: List<Flow<PressureReading?>> = emptyList()) : PressureProvider {
        var collectionsStarted = 0
        var collecting = false
        override val readings: Flow<PressureReading?> = flow {
            collectionsStarted++
            collecting = true
            emitAll(sources.getOrNull(collectionsStarted - 1) ?: source)
        }.onCompletion { collecting = false }
    }

    private val pressure = MutableSharedFlow<PressureReading?>(replay = 64)

    private fun fixAt(seconds: Long, raw: Double?) = LocationFix.Update(
        lat = 45.52, lng = -122.68, altitude = raw, accuracyMeters = 5f, timestampEpochMillis = T0 + seconds * 1_000L, provider = FixProvider.GPS,
    )

    private fun readingAt(seconds: Long, hPa: Float) = PressureReading(hPa = hPa, elapsedRealtimeNanos = seconds * 1_000_000_000L)

    private fun rig(barometer: PressureProvider, converter: com.zynergylabs.forager.app.domain.SeaLevelConverter = FixedOffsetSeaLevelConverter(SEA_LEVEL_ABOVE_RAW)) =
        RealSearchScreenRig(composeRule, locationTracker = tracker, pressureProvider = barometer, seaLevelConverter = converter).also {
            it.setScreen()
            it.searchCoordinates("45.52", "-122.68")
            it.enterForeground()
        }

    /** A real touch at the centre of a bottom-nav destination, as HeadingKeptAcrossTabsTest does. */
    private fun touchNav(label: String) {
        val b = composeRule.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag(COMPACT_BOTTOM_NAV_TAG)), useUnmergedTree = true)
            .onFirst().fetchSemanticsNode().boundsInRoot
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(b.center.x, b.center.y)) }
        composeRule.waitForIdle()
    }

    private fun stripHeight(): String {
        val nodes = composeRule.onAllNodesWithTag(COMPASS_STRIP_ELEVATION_TAG).fetchSemanticsNodes()
        check(nodes.size == 1) { "expected one strip height, found ${nodes.size}" }
        return nodes.single().config[SemanticsProperties.Text].joinToString { it.text }
    }

    @Test
    fun `the strip shows the height above sea level, not the phone's raw height`() {
        val r = rig(FakeBarometer(flow { emit(null) }))
        fixes.tryEmit(fixAt(0, raw = 50.0))
        r.settle()

        // 50 m raw is 71.6 m above sea level here. Before this change the strip read the raw "50 m".
        assertEquals("72 m", stripHeight())
    }

    @Test
    fun `on a phone that cannot convert the strip reads Alt unavailable, never the raw height`() {
        val r = rig(FakeBarometer(flow { emit(null) }), converter = NoSeaLevelConverter)
        fixes.tryEmit(fixAt(0, raw = 50.0))
        r.settle()

        assertEquals("Alt unavailable", stripHeight())
    }

    @Test
    fun `with no barometer the strip follows each fix's sea-level height as it comes`() {
        val r = rig(FakeBarometer(flow { emit(null) }))
        fixes.tryEmit(fixAt(0, raw = 50.0))
        r.settle()
        assertEquals("72 m", stripHeight())

        fixes.tryEmit(fixAt(1, raw = 60.0))
        r.settle()
        assertEquals("82 m", stripHeight())
    }

    @Test
    fun `the barometer is read while the map is open with no recording, and stops when the app leaves the foreground`() {
        val barometer = FakeBarometer(pressure)
        val r = rig(barometer)

        assertEquals("the map's own collection read the barometer, with no recording running", 1, barometer.collectionsStarted)
        assertEquals(true, barometer.collecting)

        r.viewModel.onLeftForeground()
        r.settle()
        assertEquals("leaving the foreground stops the barometer with the fixes", false, barometer.collecting)
    }

    @Test
    fun `when GPS drops out the strip follows the barometer`() {
        val r = rig(FakeBarometer(pressure))
        (0L..4L).forEach { pressure.tryEmit(readingAt(it, 1000.0f)) }
        r.settle()
        fixes.tryEmit(fixAt(4, raw = 50.0))
        r.settle()
        assertEquals("anchored: the barometer's height plus the anchor is the fix's sea-level height", "72 m", stripHeight())

        // No more fixes. The pressure falls by 1 hPa, about 8.4 m up by the standard atmosphere.
        (5L..30L).forEach { pressure.tryEmit(readingAt(it, 999.0f)) }
        r.settle()

        assertEquals("the height follows the barometer with GPS gone (71.6 + 8.4)", "80 m", stripHeight())
    }

    /** Amendment 3, N2 (RECORD -1797; the owner: "Map tab only (Recommended)"): not read on the other tabs. */
    @Test
    fun `the barometer is read only while the Maps tab is showing`() {
        val barometer = FakeBarometer(pressure)
        val r = rig(barometer)
        assertEquals("on the Maps tab", true, barometer.collecting)

        touchNav(CompactTab.LIST.label)
        r.settle()
        assertEquals("off the Maps tab the barometer is not read", false, barometer.collecting)

        touchNav(CompactTab.MAP.label)
        r.settle()
        assertEquals("back on the Maps tab it is read again", true, barometer.collecting)
        assertEquals(2, barometer.collectionsStarted)
    }

    /**
     * Amendment 3, N2 under N1's rule: back on the map the steadying starts afresh, so for its first minute the strip
     * shows each fix's converted GPS height as it comes, not an anchor from before the tab was left.
     */
    @Test
    fun `back on the Maps tab the strip shows each fix's sea-level height until the barometer has re-anchored`() {
        val before = MutableSharedFlow<PressureReading?>(replay = 8).also { it.tryEmit(readingAt(0, 1000.0f)) }
        val after = MutableSharedFlow<PressureReading?>(replay = 8).also { it.tryEmit(readingAt(300, 1000.0f)) }
        val r = rig(FakeBarometer(pressure, sources = listOf(before, after)))
        listOf(0L, 30L, 60L, 95L).forEach { fixes.tryEmit(fixAt(it, raw = 50.0)) }
        r.settle()
        fixes.tryEmit(fixAt(96, raw = 58.0))
        r.settle()
        assertEquals("positive control: steadied, the strip holds 72 m where this fix alone reads 80 m", "72 m", stripHeight())

        touchNav(CompactTab.LIST.label)
        r.settle()
        touchNav(CompactTab.MAP.label)
        r.settle()
        fixes.tryEmit(fixAt(200, raw = 58.0))
        r.settle()

        assertEquals("a fresh first minute: the fix's own height above sea level (58 + 21.6)", "80 m", stripHeight())
    }

    private companion object {
        const val T0 = 1_700_000_000_000L
        const val SEA_LEVEL_ABOVE_RAW = 21.6
    }
}

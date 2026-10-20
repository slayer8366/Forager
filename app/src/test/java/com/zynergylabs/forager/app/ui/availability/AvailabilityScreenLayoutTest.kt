package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.BuildConfig
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.NoTripWindowReason
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SoilAvailability
import com.zynergylabs.forager.app.domain.model.RainEvent
import com.zynergylabs.forager.app.domain.model.TripWindow
import com.zynergylabs.forager.app.domain.model.TripWindowReport
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * Measured layout invariants for [AvailabilityScreen], run headlessly on the JVM.
 *
 * ## Why this exists
 *
 * Every layout defect this screen has had was found by a human holding a phone: the map starved
 * to a ~55dp sliver by unweighted siblings in an unscrolled `Column`, the tab row and Conditions
 * card measured to zero height, the Conditions card invisible for two builds, and — after the
 * map-first fix — tiles painting over the tab row and the caption below. The 71 tests that
 * existed before this file are all headless domain and ViewModel tests: none of them measures
 * anything, so none of them could have caught any of it. An earlier attempt reproduced the
 * squeeze as a hand-computed height-budget table, which was arithmetic about a model of the
 * layout rather than evidence about the layout, and it both missed a symptom and produced a
 * replacement that broke in a new way.
 *
 * These tests compose the real screen with the real Material3 tree under Robolectric and read
 * back the positions and sizes Compose actually measured. That is a measure pass, not a model.
 *
 * ## What this does not cover
 *
 * The map is a **stub**. These tests verify that [AvailabilityScreen] hands the map a box of the
 * right size in the right place; they say nothing about what osmdroid draws inside that box. The
 * escaping polyline and overhanging tiles fixed by `Modifier.clipToBounds()` are by construction
 * out of reach here — osmdroid does not render meaningfully under Robolectric either. This closes
 * the layout-geometry part of the on-device verification gap and no more of it.
 *
 * ## How the host activity gets declared
 *
 * `createComposeRule()` launches `androidx.activity.ComponentActivity`, which normally has to be
 * in the manifest — the job of `androidx.compose.ui:ui-test-manifest`, added as
 * `debugImplementation`, which would put that entry into the debug APK a tester installs.
 * Robolectric reads the manifest packaged inside `apk_for_local_test`, which comes from the main
 * variant and cannot be reached from the unit-test classpath at all (see app/build.gradle.kts).
 * So the activity is registered on Robolectric's package manager at runtime instead: test-only by
 * construction, and the shipped manifest is untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // Pinned rather than inherited. The default is the manifest's targetSdk, 37, and
    // Robolectric 4.16.1 has no runtime for it ("API level 37 is not available"). 36 is the
    // newest it ships. Nothing measured here is SDK-dependent — the geometry comes from Compose
    // and Material3, not the platform — but the pin is explicit so a future targetSdk bump
    // doesn't silently turn these red.
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = SMALL_DENSE_PHONE,
)
class AvailabilityScreenLayoutOnSmallDensePhoneTest : AvailabilityScreenLayoutTest()

/**
 * The same invariants at a doubled font scale — the config where the original squeeze was worst,
 * because every wrap-content control in the old stacked column grew while the screen did not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = SMALL_DENSE_PHONE, fontScale = 2.0f)
class AvailabilityScreenLayoutAtLargeFontScaleTest : AvailabilityScreenLayoutTest()

/** A roomier, taller phone, so a threshold tuned to the small screen isn't the only thing tested. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = LARGE_PHONE)
class AvailabilityScreenLayoutOnLargePhoneTest : AvailabilityScreenLayoutTest()

private const val ROBOLECTRIC_SDK = 36

/** A small, dense phone: the least room this layout has to work with. */
private const val SMALL_DENSE_PHONE = "w360dp-h640dp-xhdpi"

/** A current mid-size phone. */
private const val LARGE_PHONE = "w411dp-h891dp-xxhdpi"

/** The tag the stub map slot carries; the real [com.zynergylabs.forager.app.ui.map.SightingsMap] has no tag. */
private const val MAP_SLOT_TAG = "map-slot"

/**
 * The floor for the map's share of the screen, as a fraction of the whole root height.
 *
 * Justified from both ends rather than picked round:
 *
 * - **What a broken layout gives it.** The reported sliver was ~55dp of a ~780dp screen: 7%.
 *   Restoring the original stacked-controls column and re-running these tests measured it at
 *   **0dp on all three configurations** — the starved child is not merely small, it is gone.
 * - **What a healthy layout gives it**, measured by these tests rather than computed, with the
 *   foraging-areas panel expanded (the state in which the map has the most competition):
 *   **316dp of 640dp (49%)** on `w360dp-h640dp-xhdpi`, **247dp of 640dp (39%)** on the same
 *   screen at fontScale 2.0 — the tightest configuration tested — and **571dp of 891dp (64%)**
 *   on `w411dp-h891dp-xxhdpi`.
 *
 * 33% therefore sits below the tightest healthy figure with room for a Material metric to shift
 * a little, and far above every sliver. It is expressed as a fraction rather than a dp count so
 * it means the same thing on a 640dp phone and an 891dp one. It is not a cosmetic preference:
 * this screen's documented contract is that the results are the primary content and the controls
 * are in a drawer precisely so the map can have the space. A map holding less than a third of the
 * screen is not that layout any more.
 */
private const val MIN_MAP_SHARE_OF_SCREEN = 0.33f

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private fun sighting(index: Int) = Sighting(
    observationId = index.toLong(),
    taxonId = 48473L,
    scientificName = "Ganoderma applanatum",
    commonName = "artist's bracket",
    lat = REGION.lat + index * 0.001,
    lng = REGION.lng + index * 0.001,
    observedOn = LocalDate.of(2025, 8, 14),
    photoUrl = null,
)

/**
 * A searched region with mapped sightings: the state the Map tab is in for most of its life.
 */
private val SEARCHED_STATE = AvailabilityUiState(
    region = REGION,
    sightings = List(12) { sighting(it) },
    // Fixed explicitly — this file's assertions are hardcoded to "15 km" and "12.4mm" text, so it
    // must not drift with the default this field carries; the imperial rainfall case below opts in.
    unitSystem = UnitSystem.METRIC,
)

/**
 * One window carrying a soil temperature, for the units-follow tests (dispatch 2026-09-28-549).
 * Only the soil temperature is the subject; the other measurements are left null so their rows
 * do not render.
 */
private val TRIP_WINDOW_REPORT_ONE_WINDOW = TripWindowReport(
    region = REGION,
    referenceDay = LocalDate.of(2025, 8, 14),
    horizonEnd = LocalDate.of(2025, 8, 21),
    rainEvents = emptyList(),
    windows = listOf(
        TripWindow(
            startDate = LocalDate.of(2025, 8, 16),
            endDate = LocalDate.of(2025, 8, 18),
            precedingRainEvents = listOf(RainEvent(LocalDate.of(2025, 8, 5), LocalDate.of(2025, 8, 6), totalMm = 30.0, isForecast = false)),
            daysAfterMostRecentRainAtStart = 10,
            daysAfterMostRecentRainAtEnd = 12,
            meanShallowSoilMoistureM3M3 = null,
            meanDeeperSoilMoistureM3M3 = null,
            meanSoilTemperatureC = 11.3,
            minSoilTemperatureC = null,
            maxSoilTemperatureC = null,
            evapotranspirationSinceRainMm = null,
            precipitationDuringWindowMm = 0.0,
        ),
    ),
    noWindowReason = null,
    soilAvailability = SoilAvailability(
        shallowMoistureBand = null,
        deeperMoistureBand = null,
        temperatureBand = null,
    ),
)

/** [TRIP_WINDOW_REPORT_ONE_WINDOW] with a soil moisture reading (data part C). */
private val TRIP_WINDOW_REPORT_WITH_MOISTURE = TRIP_WINDOW_REPORT_ONE_WINDOW.copy(
    windows = TRIP_WINDOW_REPORT_ONE_WINDOW.windows.map { it.copy(meanShallowSoilMoistureM3M3 = 0.27) },
)

private val CONDITIONS = ConditionsSummary(
    region = REGION,
    totalPrecipitationMm = 12.4,
    daysSinceSignificantRain = 2,
)

/**
 * A trip-window search that ran and found nothing, with a stated reason — the simplest non-null
 * report, sufficient to prove the Trip Planner tab renders [TripWindowsCard]'s content rather
 * than the "no search yet" message. This file owns the layout question of which tab shows the
 * card and when; the content of a populated window list is a rendering detail this fixture
 * doesn't need to exercise to answer that question.
 */
private val TRIP_WINDOW_REPORT_NO_WINDOWS = TripWindowReport(
    region = REGION,
    referenceDay = LocalDate.of(2025, 8, 14),
    horizonEnd = LocalDate.of(2025, 8, 21),
    rainEvents = emptyList(),
    windows = emptyList(),
    noWindowReason = NoTripWindowReason.NoForecastDays,
    soilAvailability = SoilAvailability(
        shallowMoistureBand = null,
        deeperMoistureBand = null,
        temperatureBand = null,
    ),
)

abstract class AvailabilityScreenLayoutTest {

    private val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    /**
     * Declares the Compose test host activity on Robolectric's package manager before
     * [composeRule] tries to launch it. An [ExternalResource] in a [RuleChain] rather than an
     * `@Before`, because JUnit runs every rule's `before` ahead of every `@Before`, and the
     * compose rule launches the activity in its own `before`.
     */
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    /** Composes the real screen with every real callback wired to a recorder, and a stubbed map. */
    private fun setScreen(
        uiState: AvailabilityUiState,
        onUseCurrentLocation: () -> Unit = {},
        onSearchManualCoordinates: () -> Unit = {},
        // Navigation HUD stage one: a State so a test can open the HUD mid-composition and
        // measure the map before and after, in one setContent.
        isReturning: State<Boolean> = mutableStateOf(false),
        navigationTarget: Waypoint? = null,
    ) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = uiState,
                recording = AvailabilityScreenRecording(
                    isRecording = isReturning.value,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = isReturning.value,
                    navigationTarget = navigationTarget,
                ),
                searchInputs = AvailabilityScreenSearchInputs(
                    onUseCurrentLocation = onUseCurrentLocation,
                    onManualLatChanged = {},
                    onManualLngChanged = {},
                    onSearchManualCoordinates = onSearchManualCoordinates,
                    onRadiusChanged = {},
                    onMonthSelected = {},
                    onMapTabSelected = {},
                    onSeasonalTabSelected = {},
                    onTaxonSearchQueryChanged = {},
                    onTaxonSearchResultSelected = {},
                    onDismissTaxonSuggestions = {},
                    onReopenTaxonSuggestions = {},
                    onPlaceTripPin = { _, _, _ -> },
                    onDeletePlannedTrip = {},
                    onRecentSearchSelected = {},
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                ),
                mapSlot = StubMapSlot,
            )
        }
    }

    private fun mapSlotBounds(): DpRect =
        composeRule.onNodeWithTag(MAP_SLOT_TAG).getUnclippedBoundsInRoot()

    private fun rootBounds(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()

    /**
     * Opens the drawer via the bottom nav's "Tools" tab — map/navigation redesign dispatch B
     * removed the map icon stack's own "Search" button and repointed Tools at this same drawer.
     * Dispatch C moved species/category search, Recent Searches, and Advanced Search all out of it
     * and into [SearchDropdown] instead (see [openSearchDropdown]) — what's left here is Trip
     * Planner, Waypoints, and Settings; see [CompactToolsDrawerContent]'s own doc
     * comment. Tools opens the drawer as an overlay over whatever `compactTab` is already showing
     * rather than becoming a tab itself, and [setScreen] always lands on the Maps tab by default,
     * so it's already on screen with no tab switch needed.
     */
    private fun openToolsDrawer() {
        composeRule.onNodeWithText("Tools").performClick()
    }

    /**
     * Opens [SearchDropdown] via the search summary bar — map/navigation redesign dispatch C moved
     * species/category search, Recent Searches, and Advanced Search (location/radius/month) out of
     * the Tools drawer entirely, to float over the map from where quick species search used to sit.
     */
    private fun openSearchDropdown() {
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
    }

    /**
     * The bottom edge of the top strip, read from [SEARCH_ENTRY_BAR_TAG]'s own real measured
     * bounds rather than a hard-coded height, so a Material change can't make this quietly wrong.
     * Replaces the pre-redesign `tabRowBottom()`, the app-bar-based `appBarBottom()` that replaced
     * it in turn, and (map/navigation redesign dispatch D) a version of this that read the "15 km"
     * fragment of [ActiveSearchSummary]'s own rendered text — [SearchEntryBar] replaced that
     * read-only summary with a real entry field, so there is no stable text fragment left to key
     * off; a tag on the bar's own outer bounds is what survived that redesign. There is no more app
     * bar for compact at all now — species/category search moved out entirely, first into the Tools
     * drawer (see [CompactToolsDrawerContent]), then into [SearchDropdown] — so [SearchEntryBar] is
     * the one thing still visible above the map in the default state, and the sibling this
     * regression test now guards against.
     */
    private fun topStripBottom(): Dp =
        composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG).getUnclippedBoundsInRoot().bottom

    /**
     * **Test 1 — the regression test for the original bug.**
     *
     * The map used to be measured against whatever height the unweighted controls above it left
     * over, which on a real phone was ~55dp and on a shorter one zero.
     */
    @Test
    fun `the map slot gets a substantial share of the screen`() {
        setScreen(SEARCHED_STATE)

        val map = mapSlotBounds()
        val root = rootBounds()
        val share = map.height / root.height

        // Printed so the threshold above stays anchored to figures somebody can re-read rather
        // than to a number in a comment that may have gone stale.
        println("MEASURED map=${map.height} root=${root.height} share=$share")

        assertTrue(
            "The map slot must hold at least ${(MIN_MAP_SHARE_OF_SCREEN * 100).toInt()}% of the " +
                "screen height on this configuration, but it measured ${map.height} of " +
                "${root.height} (${(share * 100).toInt()}%). The map is the primary content of " +
                "this screen; a slot this small is the starvation bug returning.",
            share >= MIN_MAP_SHARE_OF_SCREEN,
        )
    }

    /**
     * **Test 2 — the map fills the full available height on the Map tab, not squeezed by the bar.**
     *
     * Originally guarded against the map rendering above the (then top) tab row — a genuinely
     * broken clip/ordering bug. That row is long gone (see [ForagerBottomNav], now at the
     * *bottom* of the screen), and the sibling this test tracked next, [SEARCH_ENTRY_BAR_TAG]
     * ("the top strip"), stopped being the right invariant too: the search-bar opacity/gap
     * dispatch made [SearchEntryBar] compose as a real overlay above the map on this tab (its own
     * 80% fill revealing real map imagery through it, like the compass strip and the two
     * TrailheadControls pills already do), not a sibling that reserves space above it. So "the
     * map slot starts below the bar's own bottom edge" is now the wrong thing to assert — the map
     * is *supposed* to extend underneath it. What this test still needs to catch: the map slot
     * silently losing height to some sibling claiming space above it the old way, which is why it
     * now asserts flush-with-root instead of flush-with-the-bar.
     */
    @Test
    fun `the map slot starts at the top of the screen, not squeezed below the search bar`() {
        setScreen(SEARCHED_STATE)

        val mapTop = mapSlotBounds().top
        val rootTop = rootBounds().top
        val topBottom = topStripBottom()

        println("MEASURED mapTop=$mapTop rootTop=$rootTop topStripBottom=$topBottom")

        assertTrue(
            "The map slot should start flush with the screen's own top edge ($rootTop) now that " +
                "SearchEntryBar composes as a real overlay above it on the Map tab (opacity/gap " +
                "dispatch, the owner's own direct call) rather than a sibling that reserves space " +
                "above it, but its top is $mapTop. The bar's own bottom edge ($topBottom) is no " +
                "longer the right invariant here: this test used to guard against the map " +
                "rendering above the (opaque) top app bar, a genuinely broken clip/ordering bug — " +
                "an intentional, translucent overlap so the bar's 80% fill reveals real map " +
                "imagery through it is not that bug.",
            mapTop == rootTop,
        )
    }

    /**
     * **Test 3 — fullscreen floats the bottom nav over the map; it does not resize the map.**
     *
     * Fullscreen-fixes dispatch, Item 1, third design (the first two attempts both failed this
     * exact test, with concrete numbers — see the dispatch's own investigation report for both).
     * The bar no longer lives in Scaffold's `bottomBar` slot for the Map tab at all, in either
     * fullscreen state — it renders as an always-present overlay inside `CompactMapTab`'s own
     * content `Box` instead (that composable's own doc comment). Because that `Box`'s own size
     * never depends on `isMapFullscreen` — `bottomBar` reports zero height for the Map tab
     * regardless of fullscreen, since nothing renders there for that tab either way — the map's
     * measured height must be identical before and after the toggle, by construction rather than by
     * a padding trick applied after the fact.
     */
    @Test
    fun `fullscreen does not change the map's own measured height`() {
        setScreen(SEARCHED_STATE)
        val heightBefore = mapSlotBounds().height

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()

        val heightAfter = mapSlotBounds().height
        println("MEASURED heightBefore=$heightBefore heightAfter=$heightAfter")
        assertTrue(
            "The map's own measured height must not change when fullscreen toggles — it was " +
                "$heightBefore before and $heightAfter after. A resize here means MapLibre has to " +
                "re-layout and re-fit, the exact non-seamless transition floating chrome over a " +
                "map whose dimensions never change was built to remove.",
            heightBefore == heightAfter,
        )
    }

    /**
     * **Test 3c — the navigation HUD (stage one) floats over the map; it does not resize it.**
     *
     * The HUD is a child of `CompactMapTab`'s own map Box (see `NavigationHud`'s doc comment for
     * the mounting reasoning), so opening it must leave the map's own measured height exactly as it
     * was — with and without fullscreen. This is the dispatch's own guard, extended with the
     * HUD-open case: if the HUD ever gets wrapped around the map, or reserves layout space, this is
     * the test that goes red.
     *
     * **Navigation-chrome dispatch: the navigating case, both ways.** The compass strip is a
     * top-aligned child of the same constraint-sized Box, so hiding it while navigating and
     * restoring it after must not move the map either — asserted by measuring on entry *and* on
     * exit, with the strip's absence and return asserted at each step so the height claim is
     * about the transition that actually happened.
     */
    @Test
    fun `entering and leaving navigation does not change the map's own measured height, in or out of fullscreen`() {
        val returning = mutableStateOf(false)
        val origin = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = 1L, trackId = "t1", designation = WaypointDesignation.ORIGIN)
        setScreen(SEARCHED_STATE, isReturning = returning, navigationTarget = origin)
        composeRule.onNodeWithTag("compass-elevation-strip").assertIsDisplayed()
        val heightClosed = mapSlotBounds().height

        composeRule.runOnUiThread { returning.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
        // Dispatch 2026-10-12-08 (RECORD -950): the strip stays while navigating. Was assertDoesNotExist().
        composeRule.onNodeWithTag("compass-elevation-strip").assertIsDisplayed()
        val heightOpen = mapSlotBounds().height
        println("MEASURED heightClosed=$heightClosed heightOpen=$heightOpen")
        assertTrue(
            "The map's own measured height must not change when the navigation HUD opens — it was " +
                "$heightClosed closed and $heightOpen open. The HUD is chrome floating over the map, " +
                "never a sibling that reserves space or a wrapper that resizes it.",
            heightClosed == heightOpen,
        )

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
        // Dispatch 2026-10-12-08 (RECORD -950): the strip stays while navigating. Was assertDoesNotExist().
        composeRule.onNodeWithTag("compass-elevation-strip").assertIsDisplayed()
        val heightFullscreen = mapSlotBounds().height
        assertTrue(
            "…and fullscreen with the HUD open must not change it either: $heightOpen vs $heightFullscreen.",
            heightOpen == heightFullscreen,
        )

        composeRule.runOnUiThread { returning.value = false }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(NAVIGATION_HUD_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag("compass-elevation-strip").assertIsDisplayed()
        val heightLeft = mapSlotBounds().height
        println("MEASURED heightFullscreen=$heightFullscreen heightLeft=$heightLeft")
        assertTrue(
            "…and leaving navigation (the strip returning) must not change it either: " +
                "$heightFullscreen vs $heightLeft.",
            heightFullscreen == heightLeft,
        )
    }

    /**
     * **Test 3b — attribution's own clearance reaches the map in both states, not just fullscreen.**
     *
     * Fullscreen-fixes dispatch, Item 1, third design: the bottom nav overlays the map outside
     * fullscreen too now (`CompactMapTab`'s own doc comment), not only while fullscreen — the first
     * two attempts at this fix only floated the bar over the map while fullscreen, so this test
     * used to assert `bottomInset` was zero normally. That premise no longer holds: attribution
     * must clear the bar exactly the same way in both states, since the bar sits over the map in
     * both now, so `bottomInset` must be positive — and, since only the bar's own opacity changes
     * between the two states, not its layout, identical — in both.
     */
    @Test
    fun `bottomInset is positive and identical whether or not the map is fullscreen`() {
        setScreen(SEARCHED_STATE)
        val insetBefore = capturedRenderMode?.bottomInset
        println("MEASURED bottomInset before fullscreen=$insetBefore")
        assertTrue(
            "bottomInset must already be positive outside fullscreen, since the bottom nav overlays " +
                "the map there too now, but it measured $insetBefore.",
            (insetBefore ?: 0.dp) > 0.dp,
        )

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()

        val insetAfter = capturedRenderMode?.bottomInset
        println("MEASURED bottomInset after fullscreen=$insetAfter")
        assertEquals(
            "bottomInset should not change across the fullscreen toggle — only the bar's own " +
                "opacity changes, not its layout, so its measured height (and this inset) should be " +
                "identical in both states.",
            insetBefore,
            insetAfter,
        )
    }

    /**
     * Part 2 follow-ups F1 item 2 (Part 2 item 37): the attribution "i" sat inside the system navigation band
     * in portrait fullscreen, where two real taps opened nothing. The caption keeps the true edge in
     * fullscreen (owner's ruling, see `AvailabilityCompactScaffold`); the "i" clears the navigation bar.
     * Robolectric reports zero insets, so these tests put a real navigation-bar inset on the window
     * ([withNavigationBarInset]) and read what the map is handed. What MapLibre then draws, and the real
     * band, are device-only.
     */
    @Test
    fun `in portrait fullscreen the attribution button clears the navigation bar inset`() {
        setScreen(SEARCHED_STATE)
        val navBarPx = withNavigationBarInset(96)
        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()

        val expectedDp = with(androidx.compose.ui.unit.Density(composeRule.activity.resources.displayMetrics.density)) { navBarPx.toDp() }
        val actual = capturedRenderMode?.attributionBottomInset
        assertEquals("the map is handed the button's own bottom inset, the navigation bar's", expectedDp.value, actual?.value ?: -1f, 0.5f)
    }

    @Test
    fun `the caption keeps the true bottom edge in fullscreen while the button clears the bar`() {
        setScreen(SEARCHED_STATE)
        withNavigationBarInset(96)
        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()

        assertEquals("the caption's inset is unchanged: the true edge", 0f, capturedRenderMode?.bottomInset?.value ?: -1f, 0.01f)
    }

    @Test
    fun `out of fullscreen the attribution button follows the navigation bar's measured height, as the caption does`() {
        setScreen(SEARCHED_STATE)
        withNavigationBarInset(96)
        composeRule.waitForIdle()

        val mode = capturedRenderMode
        assertEquals("the button's inset is the caption's outside fullscreen", (mode?.bottomInset?.value ?: -1f), (mode?.attributionBottomInset ?: mode?.bottomInset)?.value ?: -2f, 0.01f)
        assertTrue("and it is positive: the nav overlays the map", (mode?.bottomInset ?: 0.dp) > 0.dp)
    }

    @Test
    fun `with no navigation bar inset the button in fullscreen sits at the true edge`() {
        setScreen(SEARCHED_STATE)
        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()

        assertEquals(0f, capturedRenderMode?.attributionBottomInset?.value ?: 0f, 0.01f)
    }

    /**
     * Puts a bottom navigation-bar inset of [px] on the window, as the platform delivers one, and returns it.
     * Compose reads system-bar insets through the window-insets dispatch, which Robolectric never fires with a
     * bar in it; dispatching one here is the only way a test sees a non-zero value.
     */
    private fun withNavigationBarInset(px: Int): Int {
        composeRule.runOnUiThread {
            val insets = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, 0, px))
                .setInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, 0, px))
                .build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(composeRule.activity.window.decorView, insets)
        }
        composeRule.waitForIdle()
        return px
    }

    /**
     * **Test 4 — the Conditions card is actually on screen.**
     *
     * The card shipped and was measured to zero height for two builds: it existed, it was in the
     * tree, and nobody ever saw it. [assertIsDisplayed] is the assertion that distinguishes those
     * two states — it fails both for a missing node and for a node with no area on screen.
     *
     * Lives in the Seasonal tab, not List — PANEL-CONTENTS-DISPATCH.md item 2 moved it there; see
     * [ConditionsCard]'s own doc comment.
     *
     * The month half of this is asserted end to end in [AvailabilityScreenConditionsMonthTest]:
     * the screen renders the card whenever the state carries conditions, and it is the ViewModel
     * that clears them for a non-current month, so that gate is exercised there by picking a
     * month from the real dropdown rather than by restating the screen's own `if` here.
     */
    @Test
    fun `the conditions card is displayed on the seasonal tab when conditions are present`() {
        setScreen(SEARCHED_STATE.copy(conditions = CONDITIONS, selectedMonth = LocalDate.now().monthValue))

        composeRule.onNodeWithText("Seasonal").performClick()

        composeRule.onNodeWithText("Current Conditions").assertIsDisplayed()
        // Data part C (dispatch -668): a labelled table, not sentences. Each row is one merged node,
        // so its label and its value are found on the same node. The last rain is 2 days before the
        // newest observed day, which is yesterday, so 3 days ago (it used to read "2 days since last
        // rain.").
        composeRule.onNode(hasText("Rain, last 14 days") and hasText("12.4mm")).assertIsDisplayed()
        composeRule.onNode(hasText("Last rainy day") and hasText("3 days ago")).assertIsDisplayed()
    }

    /**
     * Return-estimate dispatch, Item 4: the same 12.4 mm reads as tenths of an inch under the US
     * setting (12.4 / 25.4 = 0.488 → "0.5 in", worked by hand), and the metric string is gone.
     */
    @Test
    fun `the conditions card reads rainfall in inches under the imperial setting`() {
        setScreen(SEARCHED_STATE.copy(conditions = CONDITIONS, selectedMonth = LocalDate.now().monthValue, unitSystem = UnitSystem.IMPERIAL))

        composeRule.onNodeWithText("Seasonal").performClick()

        composeRule.onNode(hasText("Rain, last 14 days") and hasText("0.5 in")).assertIsDisplayed()
        composeRule.onNodeWithText("12.4mm").assertDoesNotExist()
    }

    /** The screen's own half of the gate: no conditions in state, no card in the tree. */
    @Test
    fun `the conditions card is absent when the state carries no conditions`() {
        setScreen(SEARCHED_STATE.copy(conditions = null))

        composeRule.onNodeWithText("Seasonal").performClick()

        composeRule.onNodeWithText("Current Conditions").assertDoesNotExist()
    }

    /**
     * The Trip Planner drawer section's own gate: [TripWindowsCard] used to be shown inside the
     * List tab whenever `uiState.region != null`, with no message otherwise, then moved to its own
     * top-level tab, and now lives inside the drawer's collapsible Trip Planner section. It still
     * needs the same "nothing chosen yet" gate [MapTab] has for its own content — otherwise
     * expanding the section before any search would render an empty card with nothing explaining
     * why.
     *
     * `performScrollTo()` before the assertion, not just `assertIsDisplayed()` — needed since map/
     * navigation redesign dispatch B added [SettingsEntryRow] as a fixed sibling below
     * [SearchControls] in the drawer's outer `Column`, which shrinks [SearchControls]'s own
     * `weight(1f)` share of the sheet by that row's height. At `fontScale = 2.0`, the gate message
     * (after Trip Planner, the third of three collapsed sections) no longer fits the now-slightly-
     * shorter internal scroll viewport without scrolling to it, matching the same pattern this
     * file's sibling Trip Planner tests already use for content reached the same way.
     */
    @Test
    fun `the drawer's Trip Planner section shows a no-search message before any region is chosen`() {
        setScreen(AvailabilityUiState())

        openToolsDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        composeRule.onNodeWithText("Choose a region in search options to see rain-driven trip windows.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Trip Windows").assertDoesNotExist()
    }

    /** The other half of the gate: once a region has been searched, the card itself is shown. */
    @Test
    fun `the drawer's Trip Planner section shows the trip windows card once a region is searched`() {
        setScreen(SEARCHED_STATE.copy(tripWindowReport = TRIP_WINDOW_REPORT_NO_WINDOWS))

        openToolsDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        // Scrolled to the card's last piece, not to "Trip Windows" itself: performScrollTo()
        // bottom-aligns its target flush with the scrollable viewport's edge, so anchoring on the last
        // piece brings the title into view with clearance. That last piece was the guidance heading
        // until RECORD -727 removed the guidance; the card now ends at its measurements line.
        composeRule.onNodeWithText(
            "No forecast days were returned for this location, so there's nothing to plan against.",
        ).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Trip Windows").performScrollTo().assertIsDisplayed()
    }

    /**
     * Dispatch 2026-09-28-549: under Imperial (US) the window's soil temperature reads in °F to one
     * decimal (11.3 °C × 9/5 + 32 = 52.34). The guidance band it also read (10–20 °C = 50–68 °F) went
     * with the guidance text (RECORD -727), and that assertion with it. Driven through the real drawer
     * as the test above is; the metric strings are the next test.
     */
    @Test
    fun `under the imperial setting the trip window's soil temperature reads in Fahrenheit`() {
        setScreen(SEARCHED_STATE.copy(tripWindowReport = TRIP_WINDOW_REPORT_ONE_WINDOW, unitSystem = UnitSystem.IMPERIAL))

        openToolsDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        composeRule.onNode(hasText("Soil temperature") and hasText("52.3°F")).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("°C", substring = true).assertCountEquals(0)
    }

    /** The metric half: the same window reads exactly as before (its guidance band went with RECORD -727). */
    @Test
    fun `under the metric setting the trip window's soil temperature reads as before`() {
        setScreen(SEARCHED_STATE.copy(tripWindowReport = TRIP_WINDOW_REPORT_ONE_WINDOW))

        openToolsDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        composeRule.onNode(hasText("Soil temperature") and hasText("11.3°C")).performScrollTo().assertIsDisplayed()
    }

    /**
     * Data part C (dispatch -668): a trip window reads as dated rows. Its dates are "Aug 16, 2025"
     * (owner, RECORD -656), and its soil moisture is the plain scale with the figure under it: 0.27
     * m³/m³ sits between the proposed Dry (below 0.15) and Wet (0.30 and up) thresholds, so Moist.
     * The scale is read to TalkBack as one phrase, which is what is asserted.
     */
    @Test
    fun `a trip window shows its dates as Aug 16, 2025, its rain and soil as labelled rows, and soil moisture on the plain scale`() {
        setScreen(SEARCHED_STATE.copy(tripWindowReport = TRIP_WINDOW_REPORT_WITH_MOISTURE))

        openToolsDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        composeRule.onNodeWithText("Aug 16, 2025 – Aug 18, 2025").performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText("Days after rain") and hasText("10–12")).performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText("Last soaking rain") and hasText("30mm, ended Aug 6, 2025")).performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText("Soil moisture") and hasContentDescription("Moist, 0.27 m³/m³")).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Shallow soil moisture", substring = true).assertDoesNotExist()
    }

    /**
     * **The planned-trips feature's core display promise.** A trip dated today is moved to the
     * front of the list and carries a "Today" label, distinct from a trip on any other date — the
     * "auto-promoted display, no notification" behaviour the user asked for. Drives the real
     * drawer-section expand tap rather than handing the screen a pre-expanded state, so this also
     * proves the list is reachable through the same gesture a user would use.
     *
     * The trips are scrolled to before being asserted on, as in
     * [every Trip Planner drawer control is reachable] above. They were not, until the drawer grew
     * its "Recent searches" section: one more collapsed header row above Trip Planner is enough,
     * at fontScale 2.0 on a 640dp-tall screen, to put the *second* trip's coordinate line below the
     * fold — measured, by removing that section and watching this test go green again. What this
     * test is about is the promotion and the label, and reachability-through-the-drawer's-scroll is
     * the property its sibling above states; asserting "visible with no scrolling at double font
     * size" was incidental to it and is not a claim this drawer makes (see [SearchControls] on why
     * the sheet scrolls at all).
     */
    @Test
    fun `a planned trip dated today is shown with a Today label in the Trip Planner section`() {
        val today = LocalDate.now()
        val todayTrip = PlannedTrip(id = "today-trip", name = "Today's Trip", location = LatLng(45.40, -122.70), date = today)
        val futureTrip = PlannedTrip(id = "future-trip", name = "Future Trip", location = LatLng(45.50, -122.80), date = today.plusDays(5))
        setScreen(SEARCHED_STATE.copy(plannedTrips = listOf(todayTrip, futureTrip)))

        openToolsDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        composeRule.onNodeWithText("Today").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45.4000, -122.7000").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45.5000, -122.8000").performScrollTo().assertIsDisplayed()
    }

    /**
     * **Test 5 — the coordinates are the first thing the search bar's tap shows** (owner, 2026-09-28,
     * continuation 2026-09-28-40: "Also open manual coordinates"; dispatch 2026-09-28-697 put them
     * first and removed the folds around them: "Manual can go at the top", "remove the drop down
     * functions for the advanced search").
     *
     * Latitude and Longitude are asserted displayed with no header tapped and no `performScrollTo()`,
     * on every configuration this class runs under, the 2x font scale included, and the two folds
     * that used to hold them are asserted gone.
     */
    @Test
    fun `the search bar's tap shows the coordinates first, with no fold, without scrolling`() {
        setScreen(SEARCHED_STATE)

        openSearchDropdown()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Latitude").assertIsDisplayed()
        composeRule.onNodeWithText("Longitude").assertIsDisplayed()
        composeRule.onAllNodesWithText("Advanced search").assertCountEquals(0)
        composeRule.onAllNodesWithText("Enter coordinates manually").assertCountEquals(0)
    }

    /**
     * Dispatch 2026-09-28-697's order, top to bottom: the coordinates with "Search coordinates" under
     * them (RECORD -700: "Small button under the fields"), Month, Search radius, Recent
     * searches, then Set on map and Search side by side, Set on map on the left (owner: "the slider
     * should go below the month selection. Recent search below the slider", "The set on map and
     * search button where the "Search this location" button is now", "Search on the right, set on
     * map on the left"). Asserted by position, each from its laid-out bounds, not by tree order.
     * "Use current location" and "Search this location" are asserted gone; Set on map appears once.
     */
    @Test
    fun `the search dropdown runs coordinates, Search coordinates, Month, Search radius, Recent searches, then Set on map left of Search`() {
        setScreen(SEARCHED_STATE)

        openSearchDropdown()
        composeRule.waitForIdle()

        fun top(node: androidx.compose.ui.test.SemanticsNodeInteraction) = node.getUnclippedBoundsInRoot().top
        val latitude = composeRule.onNodeWithTag(SEARCH_DROPDOWN_LATITUDE_TAG).getUnclippedBoundsInRoot()
        val longitude = composeRule.onNodeWithTag(SEARCH_DROPDOWN_LONGITUDE_TAG).getUnclippedBoundsInRoot()
        // 8 km, not SEARCHED_STATE's own REGION.radiusKm (15) -- this text reads uiState.radiusKm,
        // the slider's own current value (AvailabilityUiState's default, 8).
        val searchCoordinates = composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG).getUnclippedBoundsInRoot()
        val month = top(composeRule.onNodeWithText("Month"))
        val radius = top(composeRule.onNodeWithText("Search radius: 8 km"))
        val recent = top(composeRule.onNodeWithText("Recent searches"))
        val setOnMap = composeRule.onNodeWithTag(SEARCH_DROPDOWN_SET_ON_MAP_TAG).getUnclippedBoundsInRoot()
        val search = composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_TAG).getUnclippedBoundsInRoot()

        assertEquals("Latitude and Longitude share the first row", latitude.top.value, longitude.top.value, 0.5f)
        assertTrue("Latitude is left of Longitude", latitude.right <= longitude.left)
        assertTrue("Search coordinates (${searchCoordinates.top}) is under the coordinates (${latitude.bottom})", searchCoordinates.top >= latitude.bottom)
        assertTrue("Month ($month) is below Search coordinates (${searchCoordinates.bottom})", month >= searchCoordinates.bottom)
        assertTrue("Search radius ($radius) is below Month ($month)", radius > month)
        assertTrue("Recent searches ($recent) is below Search radius ($radius)", recent > radius)
        assertTrue("the bottom row (${setOnMap.top}) is below Recent searches ($recent)", setOnMap.top > recent)
        assertEquals("Set on map and Search share the bottom row", setOnMap.top.value, search.top.value, 0.5f)
        assertTrue("Set on map (${setOnMap.right}) is left of Search (${search.left})", setOnMap.right <= search.left)

        composeRule.onAllNodesWithText("Set on map").assertCountEquals(1)
        composeRule.onAllNodesWithText("Use current location").assertCountEquals(0)
        composeRule.onAllNodesWithText("Search this location").assertCountEquals(0)
    }

    /**
     * **Test 6 — the species search bar is reachable inside the search dropdown.**
     *
     * This is the control the user originally reported as buried, promoted out of a drawer section
     * into the app bar, moved back into the drawer ("the whole side panel is the search feature"),
     * and finally — map/navigation redesign dispatch C's own follow-up owner call — into
     * [SearchDropdown], over the map, alongside Recent Searches and Advanced Search rather than
     * split across two surfaces. It's the first thing in the dropdown, above its own "Recent
     * searches"/"Advanced search" sections, so it needs no section-expanding to reach. The species
     * field itself is [SearchEntryBar]'s own — [ACTIVE_SEARCH_SUMMARY_TAG] is that real field, not
     * a duplicate inside the drawer (removed per the map/navigation search-UI redo dispatch) — and
     * has no generic hint text to match on any more (removed from the app entirely, same dispatch):
     * it shows the active filter summary instead, focused or not.
     *
     * The category chip row this test used to also assert on ("Fungi"/"Plants"/"Lichens (approx.)"
     * all displayed) is gone — owner decision: the app is fungi-only now, with nothing left to
     * choose between, so there is no chip row left to assert on.
     */
    @Test
    fun `the species search bar is reachable inside the search dropdown`() {
        setScreen(SEARCHED_STATE)

        openSearchDropdown()

        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertIsDisplayed()
    }

    /**
     * The Trip Planner section's own controls (the planned-trips list, reached through
     * [TripPlannerSection]) are reachable the same way the Advanced search section's are: open
     * the drawer, expand the section, scroll to the control.
     */
    @Test
    fun `every Trip Planner drawer control is reachable`() {
        val trip = PlannedTrip(id = "reachable-trip", name = "Reachable Trip", location = LatLng(45.40, -122.70), date = LocalDate.now())
        setScreen(SEARCHED_STATE.copy(plannedTrips = listOf(trip)))

        openToolsDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        composeRule.onNodeWithText("Planned Trips").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reachable Trip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45.4000, -122.7000").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Directions to ${trip.name}")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove planned trip for ${displayDate(trip.date)}")
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * Settings moved twice: first out of the drawer onto its own bottom-nav tab ("move settings and
     * mushroom log from the side panel, add them both to the bottom row"), then — map/navigation
     * redesign dispatch B, collapsing the bottom nav to five destinations to make room for Tools —
     * one level back in, as a sticky entry at the bottom of the "Tools" drawer's own content (see
     * [CompactToolsDrawerContent]'s `showSettings` state). This asserts it's reachable through
     * that drawer entry, replacing the old "one bottom-nav tap, no drawer involved" claim, which no
     * longer holds now that Tools — not Settings — is what's on the bottom nav.
     */
    @Test
    fun `the Settings entry is reachable inside the Tools drawer`() {
        setScreen(SEARCHED_STATE)

        openToolsDrawer()
        composeRule.onNodeWithText("Settings").assertIsDisplayed().performClick()

        // "Units", not "Distance Unit", since the return-estimate dispatch made the preference a
        // system (UnitSystem's own doc comment); both systems are offered by their labels.
        composeRule.onNodeWithText("Units").assertIsDisplayed()
        composeRule.onNodeWithText("Metric").assertIsDisplayed()
        composeRule.onNodeWithText("Imperial (US)").assertIsDisplayed()
        composeRule.onNodeWithText("Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.VERSION_NAME}")
            .assertIsDisplayed()
    }

    /**
     * The drawer's own close affordance. Gestures are off on this drawer (a swipe over the map
     * means "pan", not "close" — see [AvailabilityScreen]'s doc comment), so tapping the scrim was
     * the only way out before this button existed; this asserts the button actually closes it
     * rather than just existing in the tree. "Trip Planner" (the drawer's own first section header
     * now that Recent Searches has moved to [SearchDropdown] — dispatch C's own follow-up) stands
     * in for "the drawer is open" — "Settings" doesn't work for this: it's the drawer's own sticky
     * entry row (map/navigation redesign dispatch B), so it renders exactly whenever the drawer's
     * own content does and says nothing about open vs. closed on its own.
     */
    @Test
    fun `the drawer close button closes the drawer`() {
        setScreen(SEARCHED_STATE)

        openToolsDrawer()
        composeRule.onNodeWithText("Trip Planner").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Close search options").performClick()

        composeRule.onNodeWithText("Trip Planner").assertIsNotDisplayed()
    }

    /**
     * Where a real touch lands, as fractions of a target's width and height: its centre and four points
     * towards its corners. A finger is not a point (CLAUDE.md, Testing): one centre touch would miss a
     * region of the button that something else covers.
     */
    private val touchSamples = listOf(0.5f to 0.5f, 0.15f to 0.25f, 0.85f to 0.25f, 0.15f to 0.75f, 0.85f to 0.75f)

    /** A real touch, at screen coordinates, on the node tagged [tag] at ([fx], [fy]) of its own bounds, after scrolling it into view. */
    private fun touchTagged(tag: String, fx: Float, fy: Float) {
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.waitForIdle()
        val b = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val at = androidx.compose.ui.geometry.Offset(b.left + b.width * fx, b.top + b.height * fy)
        composeRule.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().performTouchInput { click(at) }
        composeRule.waitForIdle()
    }

    // One touch per test, each on a fresh screen. This class's window is out of touch mode (Robolectric's start; it sets no
    // touch mode), where the close's clearFocus hands focus straight back to the search field, so a second tap on the field
    // in the same test opens nothing (measured at the build, RECORD -717; it is the window's mode, not the button: Back does
    // the same there). In touch mode, as with a finger, the bar reopens after every button: SearchDropdownReopenTest.

    /**
     * RECORD -700, the owner: "Search is the same as "Use current location" just renamed and relocated."
     * Search, on the right of the bottom row, calls the one [AvailabilityScreen] callback "Use current
     * location" called, `onUseCurrentLocation` (permission, one fix, the fields written, a search; see
     * MainActivity), and never the typed-coordinates search. A real touch at screen coordinates at sample [i]
     * of [touchSamples]; it must reach Search and close the dropdown.
     */
    private fun checkSearchTouch(i: Int) {
        val (fx, fy) = touchSamples[i]
        var currentLocation = 0
        var coordinateSearches = 0
        setScreen(SEARCHED_STATE, onUseCurrentLocation = { currentLocation++ }, onSearchManualCoordinates = { coordinateSearches++ })
        openSearchDropdown()
        composeRule.waitForIdle()
        touchTagged(SEARCH_DROPDOWN_SEARCH_TAG, fx, fy)
        assertEquals("a touch at ($fx, $fy) of Search ran the current-location path", 1, currentLocation)
        assertEquals("Search never runs the typed-coordinates search", 0, coordinateSearches)
        composeRule.onAllNodesWithTag(SEARCH_DROPDOWN_TAG).assertCountEquals(0)
    }

    @Test fun `a real touch at the centre of Search runs the current-location path`() = checkSearchTouch(0)
    @Test fun `a real touch at Search's upper left runs the current-location path`() = checkSearchTouch(1)
    @Test fun `a real touch at Search's upper right runs the current-location path`() = checkSearchTouch(2)
    @Test fun `a real touch at Search's lower left runs the current-location path`() = checkSearchTouch(3)
    @Test fun `a real touch at Search's lower right runs the current-location path`() = checkSearchTouch(4)

    /**
     * RECORD -700, the owner on typed coordinates: "Small button under the fields". "Search coordinates"
     * calls `onSearchManualCoordinates`, the path "Search this location" called, and never the
     * current-location path. A real touch at sample [i] of [touchSamples].
     */
    private fun checkSearchCoordinatesTouch(i: Int) {
        val (fx, fy) = touchSamples[i]
        var currentLocation = 0
        var coordinateSearches = 0
        setScreen(SEARCHED_STATE, onUseCurrentLocation = { currentLocation++ }, onSearchManualCoordinates = { coordinateSearches++ })
        openSearchDropdown()
        composeRule.waitForIdle()
        touchTagged(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG, fx, fy)
        assertEquals("a touch at ($fx, $fy) of Search coordinates ran the coordinate search", 1, coordinateSearches)
        assertEquals("Search coordinates never runs the current-location path", 0, currentLocation)
        composeRule.onAllNodesWithTag(SEARCH_DROPDOWN_TAG).assertCountEquals(0)
    }

    @Test fun `a real touch at the centre of Search coordinates runs the coordinate search`() = checkSearchCoordinatesTouch(0)
    @Test fun `a real touch at Search coordinates' upper left runs the coordinate search`() = checkSearchCoordinatesTouch(1)
    @Test fun `a real touch at Search coordinates' upper right runs the coordinate search`() = checkSearchCoordinatesTouch(2)
    @Test fun `a real touch at Search coordinates' lower left runs the coordinate search`() = checkSearchCoordinatesTouch(3)
    @Test fun `a real touch at Search coordinates' lower right runs the coordinate search`() = checkSearchCoordinatesTouch(4)

    /**
     * Set on map, on the left of the bottom row, keeps its behaviour (dispatch 2026-09-28-697: "Keep
     * everything that works today ... Set on map's behaviour"): the dropdown closes and the centre-pin
     * picker's OK and Cancel come up over the map. A real touch at sample [i] of [touchSamples].
     */
    private fun checkSetOnMapTouch(i: Int) {
        val (fx, fy) = touchSamples[i]
        var searches = 0
        var currentLocation = 0
        setScreen(SEARCHED_STATE, onUseCurrentLocation = { currentLocation++ }, onSearchManualCoordinates = { searches++ })
        openSearchDropdown()
        composeRule.waitForIdle()
        touchTagged(SEARCH_DROPDOWN_SET_ON_MAP_TAG, fx, fy)
        composeRule.onAllNodesWithTag(SEARCH_DROPDOWN_TAG).assertCountEquals(0)
        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        assertEquals("a touch at ($fx, $fy) of Set on map ran no coordinate search", 0, searches)
        assertEquals("a touch at ($fx, $fy) of Set on map ran no current-location search", 0, currentLocation)
    }

    @Test fun `a real touch at the centre of Set on map opens the centre-pin picker`() = checkSetOnMapTouch(0)
    @Test fun `a real touch at Set on map's upper left opens the centre-pin picker`() = checkSetOnMapTouch(1)
    @Test fun `a real touch at Set on map's upper right opens the centre-pin picker`() = checkSetOnMapTouch(2)
    @Test fun `a real touch at Set on map's lower left opens the centre-pin picker`() = checkSetOnMapTouch(3)
    @Test fun `a real touch at Set on map's lower right opens the centre-pin picker`() = checkSetOnMapTouch(4)
}

/**
 * Stands in for [com.zynergylabs.forager.app.ui.map.SightingsMap].
 *
 * Composing the real one instantiates osmdroid's `MapView`: tile worker threads, a filesystem
 * cache under `cacheDir` and network fetches, none of which belong in a unit test and none of
 * which would render anything measurable under Robolectric anyway. This fills the same box and
 * carries a tag, so the box itself can be measured.
 */
private val StubMapSlot: MapSlot = { _, _, renderMode, _, _, _, _, _, modifier ->
    capturedRenderMode = renderMode
    Box(modifier.testTag(MAP_SLOT_TAG))
}

/**
 * The last [com.zynergylabs.forager.app.ui.map.MapRenderMode] [StubMapSlot] was called with — fullscreen-fixes
 * dispatch, Item 1: lets a test confirm [com.zynergylabs.forager.app.ui.map.MapRenderMode.bottomInset] is
 * actually reaching the map, since the stub renders nothing real for a device-style attribution
 * check to inspect.
 */
private var capturedRenderMode: com.zynergylabs.forager.app.ui.map.MapRenderMode? = null

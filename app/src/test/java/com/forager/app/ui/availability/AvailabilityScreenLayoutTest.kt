package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.forager.app.BuildConfig
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.NoTripWindowReason
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SoilAvailability
import com.forager.app.domain.model.TripWindowReport
import com.forager.app.ui.map.MapSlot
import com.forager.app.ui.map.VISITING_ORDER_DISCLAIMER
import java.time.LocalDate
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

/** The tag the stub map slot carries; the real [com.forager.app.ui.map.SightingsMap] has no tag. */
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

private fun area(visitOrder: Int) = ForagingArea(
    visitOrder = visitOrder,
    center = LatLng(REGION.lat + visitOrder * 0.01, REGION.lng + visitOrder * 0.01),
    sightings = List(4) { sighting(visitOrder * 10 + it) },
    distinctSpeciesCount = 3,
    mostRecentYear = 2025,
    undatedObservationCount = 0,
)

/**
 * A searched region with mapped sightings and clustered areas: the state the Map tab is in for
 * most of its life, and the one where the map has the most competition for height, since the
 * foraging-areas panel below it is populated.
 */
private val SEARCHED_STATE = AvailabilityUiState(
    region = REGION,
    sightings = List(12) { sighting(it) },
    foragingAreas = ForagingAreas.Found(areas = List(3) { area(it + 1) }, ungroupedObservationCount = 5),
    showForagingAreas = true,
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

    private val composeRule = createComposeRule()

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
    private fun setScreen(uiState: AvailabilityUiState, onUseCurrentLocation: () -> Unit = {}) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = uiState,
                onUseCurrentLocation = onUseCurrentLocation,
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onToggleForagingAreas = {},
                onCategorySelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
                onOfflineMapsOpened = {},
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                mapSlot = StubMapSlot,
            )
        }
    }

    private fun mapSlotBounds(): DpRect =
        composeRule.onNodeWithTag(MAP_SLOT_TAG).getUnclippedBoundsInRoot()

    private fun rootBounds(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()

    /**
     * Opens the search drawer via the map icon stack's own "Search" button — the compact drawer's
     * only entry point now that the app bar (and its tune icon) is gone; species/category search
     * and "Advanced search" both moved into the drawer itself — see
     * [CompactSearchDrawerContent]'s doc comment. [setScreen] always lands on the Maps tab by
     * default, so the icon is already on screen with no tab switch needed.
     */
    /**
     * Opens the drawer via the icon stack's "Search" icon — [CompactMapTab] shows a real map (and
     * so the icon stack) from its very first composition, GPS-centred or on a fixed fallback while
     * that's still pending, not only once a region has been searched.
     */
    private fun openSearchDrawer() {
        composeRule.onNodeWithContentDescription("Search").performClick()
    }

    /**
     * The bottom edge of the top strip, read from the "15 km" fragment of [ActiveSearchSummary]'s
     * own rendered text rather than a hard-coded height, so a Material change can't make this
     * quietly wrong. Replaces the pre-redesign `tabRowBottom()`, and the app-bar-based
     * `appBarBottom()` that replaced it in turn: there is no more app bar for compact at all now
     * — species/category search moved into the drawer (see [CompactSearchDrawerContent]) — so
     * [ActiveSearchSummary] is the one thing still visible above the map in the default state, and
     * the sibling this regression test now guards against. [SEARCHED_STATE]'s 15 km radius is
     * stable text regardless of which month the test happens to run in; the month portion of that
     * summary is not.
     */
    private fun topStripBottom(): Dp =
        composeRule.onNodeWithText("15 km", substring = true).getUnclippedBoundsInRoot().bottom

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
     * **Test 2 — the map does not start above the top app bar.**
     *
     * After the map-first fix the tiles were overlapping the (then top) tab row. The clip that
     * fixed that is osmdroid's business, but the slot's own geometry is this screen's, and if the
     * slot itself starts above the app bar then no clip can save it. The map redesign removed the
     * compact top tab row entirely (see [ForagerBottomNav], now at the *bottom* of the screen), so
     * the app bar is the sibling this regression test now guards against.
     */
    @Test
    fun `the map slot starts at or below the bottom of the top strip`() {
        setScreen(SEARCHED_STATE)

        val mapTop = mapSlotBounds().top
        val topBottom = topStripBottom()

        println("MEASURED mapTop=$mapTop topStripBottom=$topBottom")

        assertTrue(
            "The map slot must begin at or below the top strip's bottom edge, but its top is " +
                "$mapTop and the strip ends at $topBottom — the map's box overlaps it.",
            mapTop >= topBottom,
        )
    }

    /**
     * **Test 3 — the visiting-order caption is reachable, wherever it currently lives.**
     *
     * Before the map redesign, [VISITING_ORDER_DISCLAIMER] rendered in a fixed-height box below
     * the map. The redesign first made it a floating overlay on the map itself, then the project
     * owner's own later call ("move the foraging areas to the side search panel") moved it again,
     * into [CompactSearchDrawerContent] alongside the rest of the foraging-areas section — it no
     * longer touches the map's own bounds at all, so a geometry claim relative to the map slot is
     * no longer the right invariant. What stays true across all three homes: the caption must
     * still be reachable, not lost in whichever container currently holds it.
     */
    @Test
    fun `the visiting order caption is reachable inside the search drawer`() {
        setScreen(SEARCHED_STATE)
        openSearchDrawer()

        composeRule.onNodeWithText(VISITING_ORDER_DISCLAIMER).assertIsDisplayed()
    }

    /**
     * **Test 4 — the Conditions card is actually on screen.**
     *
     * The card shipped and was measured to zero height for two builds: it existed, it was in the
     * tree, and nobody ever saw it. [assertIsDisplayed] is the assertion that distinguishes those
     * two states — it fails both for a missing node and for a node with no area on screen.
     *
     * The month half of this is asserted end to end in [AvailabilityScreenConditionsMonthTest]:
     * the screen renders the card whenever the state carries conditions, and it is the ViewModel
     * that clears them for a non-current month, so that gate is exercised there by picking a
     * month from the real dropdown rather than by restating the screen's own `if` here.
     */
    @Test
    fun `the conditions card is displayed on the list tab when conditions are present`() {
        setScreen(SEARCHED_STATE.copy(conditions = CONDITIONS, selectedMonth = LocalDate.now().monthValue))

        composeRule.onNodeWithText("List").performClick()

        composeRule.onNodeWithText("Current Conditions").assertIsDisplayed()
        composeRule.onNodeWithText("12.4mm of rain in the last 14 days").assertIsDisplayed()
        composeRule.onNodeWithText("2 days since last rain.").assertIsDisplayed()
    }

    /** The screen's own half of the gate: no conditions in state, no card in the tree. */
    @Test
    fun `the conditions card is absent when the state carries no conditions`() {
        setScreen(SEARCHED_STATE.copy(conditions = null))

        composeRule.onNodeWithText("List").performClick()

        composeRule.onNodeWithText("Current Conditions").assertDoesNotExist()
    }

    /**
     * The Trip Planner drawer section's own gate: [TripWindowsCard] used to be shown inside the
     * List tab whenever `uiState.region != null`, with no message otherwise, then moved to its own
     * top-level tab, and now lives inside the drawer's collapsible Trip Planner section. It still
     * needs the same "nothing chosen yet" gate [MapTab] has for its own content — otherwise
     * expanding the section before any search would render an empty card with nothing explaining
     * why.
     */
    @Test
    fun `the drawer's Trip Planner section shows a no-search message before any region is chosen`() {
        setScreen(AvailabilityUiState())

        openSearchDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        composeRule.onNodeWithText("Choose a region in search options to see rain-driven trip windows.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Trip Windows").assertDoesNotExist()
    }

    /** The other half of the gate: once a region has been searched, the card itself is shown. */
    @Test
    fun `the drawer's Trip Planner section shows the trip windows card once a region is searched`() {
        setScreen(SEARCHED_STATE.copy(tripWindowReport = TRIP_WINDOW_REPORT_NO_WINDOWS))

        openSearchDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        // Scrolled to the guidance heading below both assertions, not to "Trip Windows" itself:
        // performScrollTo() bottom-aligns its target flush with the scrollable viewport's edge, and
        // the whole card is short enough to fit the viewport once scrolled this far — so anchoring
        // on the last piece brings the earlier two into view with clearance instead of flush against
        // the boundary, which at this fontScale is exactly where "Trip Windows" itself would land.
        composeRule.onNodeWithText("Rain and fungi: the general pattern").performScrollTo()

        composeRule.onNodeWithText("Trip Windows").assertIsDisplayed()
        composeRule.onNodeWithText(
            "No forecast days were returned for this location, so there's nothing to plan against.",
        ).assertIsDisplayed()
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

        openSearchDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        composeRule.onNodeWithText("Today").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45.4000, -122.7000").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45.5000, -122.8000").performScrollTo().assertIsDisplayed()
    }

    /**
     * **Test 5 — every advanced-search drawer control can actually be reached.**
     *
     * The tall stack of controls that starved the map is now behind a scroll in the drawer sheet,
     * *and* behind the "Advanced search" section's own expand tap — both have to be defeated for
     * a control to count as reachable. "Reachable" is the property that matters: unreachable is
     * exactly what they were before, and a control measured off the bottom of a fixed-height sheet
     * (or behind a collapsed section nobody expanded) is no better than one measured to zero
     * height. Each is scrolled to through the real scroll container and then asserted to be on
     * screen, which is a stronger claim than existing in the tree.
     */
    @Test
    fun `every advanced-search drawer control is reachable`() {
        setScreen(SEARCHED_STATE)

        openSearchDrawer()
        composeRule.onNodeWithText("Advanced search").performClick()

        listOf(
            "Use current location",
            "Search this location",
            "Search radius: 15 km",
            "Month",
        ).forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
    }

    /**
     * The foraging-areas toggle's own reachability claim. It has moved twice since the map
     * redesign started: out of the drawer to sit below the map, then to float as an overlay on
     * the map itself, and finally — the project owner's own later call, "move the foraging areas
     * to the side search panel" — into [CompactSearchDrawerContent], where it lives now. Unlike
     * [SearchControls]'s three sections, it's placed outside that composable's own scroll region
     * (a fixed block below it, mirroring [FORAGING_AREAS_PANEL_MAX_HEIGHT]'s old below-map
     * treatment — see that composable's doc comment), so it needs no section to expand first.
     */
    @Test
    fun `the foraging areas toggle is reachable inside the search drawer`() {
        setScreen(SEARCHED_STATE)

        openSearchDrawer()

        composeRule.onNodeWithText("Foraging areas").assertIsDisplayed()
    }

    /**
     * **Test 6 — the species search bar is reachable inside the search drawer.**
     *
     * This is the control the user originally reported as buried, then promoted out of the drawer
     * into the app bar — and now, per the project owner's later call ("the whole side panel is
     * the search feature"), moved back into the drawer, alongside every other search control
     * rather than split across two surfaces. It's the first thing in the drawer, above
     * [SearchControls]'s own scroll region, so it needs no scrolling or section-expanding to reach.
     */
    @Test
    fun `the species search bar is reachable inside the search drawer`() {
        setScreen(SEARCHED_STATE)

        openSearchDrawer()

        listOf(
            "Fungi",
            "Plants",
            "Lichens (approx.)",
            "Or search a species",
        ).forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
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

        openSearchDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        composeRule.onNodeWithText("Planned Trips").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reachable Trip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45.4000, -122.7000").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Directions to ${trip.name}")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove planned trip for ${trip.date}")
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * Settings is a bottom-nav destination now, not a drawer panel — moved there, alongside
     * Journal, per the project owner's own call ("move settings and mushroom log from the side
     * panel, add them both to the bottom row"). This asserts it's reachable with a single bottom
     * nav tap, no drawer involved at all, replacing the old "Settings entry row stays visible
     * without scrolling" and "the Settings panel shows Choose Maps Service and the build identity
     * footer" tests (both bundled into one now that reaching Settings is one step instead of two).
     */
    @Test
    fun `the Settings tab is reachable from the bottom nav, with no drawer involved`() {
        setScreen(SEARCHED_STATE)

        composeRule.onNodeWithText("Settings").performClick()

        composeRule.onNodeWithText("Choose Maps Service").assertIsDisplayed()
        composeRule.onNodeWithText("Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.VERSION_NAME}")
            .assertIsDisplayed()
    }

    /**
     * The drawer's own close affordance. Gestures are off on this drawer (a swipe over the map
     * means "pan", not "close" — see [AvailabilityScreen]'s doc comment), so tapping the scrim was
     * the only way out before this button existed; this asserts the button actually closes it
     * rather than just existing in the tree. "Recent searches" (the drawer's own first section
     * header) stands in for "the drawer is open" now — "Settings" no longer works for this, since
     * it moved to the always-visible bottom nav and would be displayed whether the drawer is open
     * or not.
     */
    @Test
    fun `the drawer close button closes the drawer`() {
        setScreen(SEARCHED_STATE)

        openSearchDrawer()
        composeRule.onNodeWithText("Recent searches").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Close search options").performClick()

        composeRule.onNodeWithText("Recent searches").assertIsNotDisplayed()
    }

    /**
     * The "use current location" shortcut on the species search field, inside the drawer. It has
     * to call the same [AvailabilityScreen.onUseCurrentLocation] callback [RegionControls]' own
     * button calls — not a second location-fetch path — which this proves by wiring a recorder
     * into that single callback and tapping the species field's icon rather than the drawer's
     * "Advanced search" button.
     */
    @Test
    fun `the species search field's location icon calls onUseCurrentLocation`() {
        var callCount = 0
        setScreen(SEARCHED_STATE, onUseCurrentLocation = { callCount++ })

        openSearchDrawer()
        composeRule.onNodeWithContentDescription("Use current location").performClick()

        assertTrue("onUseCurrentLocation should have been called exactly once", callCount == 1)
    }
}

/**
 * Stands in for [com.forager.app.ui.map.SightingsMap].
 *
 * Composing the real one instantiates osmdroid's `MapView`: tile worker threads, a filesystem
 * cache under `cacheDir` and network fetches, none of which belong in a unit test and none of
 * which would render anything measurable under Robolectric anyway. This fills the same box and
 * carries a tag, so the box itself can be measured.
 */
private val StubMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
    Box(modifier.testTag(MAP_SLOT_TAG))
}

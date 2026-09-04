package com.forager.app.ui.availability

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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.forager.app.BuildConfig
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.NoTripWindowReason
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SoilAvailability
import com.forager.app.domain.model.TripWindowReport
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate
import org.junit.Assert.assertEquals
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

/**
 * A searched region with mapped sightings: the state the Map tab is in for most of its life.
 */
private val SEARCHED_STATE = AvailabilityUiState(
    region = REGION,
    sightings = List(12) { sighting(it) },
    // Fixed explicitly — this file's assertions are hardcoded to "15 km" text and have nothing to
    // do with the km/mi preference, so it must not drift with the default this field carries.
    distanceUnit = DistanceUnit.KILOMETERS,
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
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                onNightModeMapsChanged = {},
                onThemeModeChanged = {},
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
     * Fullscreen-fixes dispatch, Item 1: the prior attempt kept Scaffold's own reported content
     * padding constant by reserving an invisible same-height spacer in its `bottomBar` slot while
     * fullscreen — which kept the map's own size correct, but left the *real* bar floating in a
     * different Compose subtree, one row for `weight(1f)`'s content above a separately-reserved
     * (and now empty) strip Scaffold still measured space for: dead space on a real device. The fix
     * keeps the bar in Scaffold's `bottomBar` slot always (so Scaffold's own layout math never
     * changes — confirmed empirically, see that code's own doc comment) and instead has the
     * content ignore Scaffold's own reported bottom inset while fullscreen, letting the map extend
     * under the bar rather than stopping short of it. This test guards both halves at once: the
     * map's measured height must be identical with the bar present (normal) and floating
     * (fullscreen) — if either half of the fix regresses, one of those two numbers moves.
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
     * **Test 3b — attribution's own clearance actually reaches the map while fullscreen, and only then.**
     */
    @Test
    fun `bottomInset is zero normally and rises to the bar's own measured height once fullscreen`() {
        setScreen(SEARCHED_STATE)
        assertEquals(0.dp, capturedRenderMode?.bottomInset)

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()

        val insetAfter = capturedRenderMode?.bottomInset
        println("MEASURED bottomInset after fullscreen=$insetAfter")
        assertTrue(
            "bottomInset must become positive once fullscreen floats the bar over the map, so " +
                "SightingsMap's own attribution rises to clear it, but it measured $insetAfter.",
            (insetAfter ?: 0.dp) > 0.dp,
        )
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
        composeRule.onNodeWithText("12.4mm of rain in the last 14 days").assertIsDisplayed()
        composeRule.onNodeWithText("2 days since last rain.").assertIsDisplayed()
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

        openToolsDrawer()
        composeRule.onNodeWithText("Trip Planner").performClick()

        composeRule.onNodeWithText("Today").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45.4000, -122.7000").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45.5000, -122.8000").performScrollTo().assertIsDisplayed()
    }

    /**
     * **Test 5 — "Enter coordinates manually" is the one control still actually gated behind
     * Advanced search.**
     *
     * Map/navigation redesign dispatch C, item 1 moved this content out of the drawer entirely,
     * into [SearchDropdown]'s own "Advanced search" section. Dispatch D then promoted radius and
     * month out to this surface's own top level; the map/navigation search-UI redo dispatch promoted
     * "Set on map" and "Use current location" out too, into their own top-level Location row —
     * leaving manual coordinates (lat/lng fields + "Search this location") as the only thing left
     * actually nested inside Advanced search. `performScrollTo()` before each assertion, same as
     * the drawer sheet this replaces: fully expanding Advanced search genuinely doesn't fit
     * `w360dp-h640dp-xhdpi`'s own [SearchDropdown] share of the screen (measured — the earlier
     * "no scroll modifier" version of this dropdown went from failing on "Search this location" to
     * failing on "Month" the moment [SearchDropdown]'s `Column` gained a `verticalScroll`, proving
     * the content really does extend past the fold rather than being genuinely absent), so
     * [SearchDropdown] carries the same `weight(1f)`-bounded scroll [SearchControls] does — see that
     * composable's own doc comment for why this is safe over the map despite Understory rule 2.
     *
     * `performScrollTo()` before the *tap on* "Enter coordinates manually" too, not just before
     * the assertion below — confirmed only at 2x font scale (`AvailabilityScreenLayoutAtLarge
     * FontScaleTest`): a semantic [performClick] normally reaches its node regardless of scroll
     * position, but that header's own [CollapsibleSection] never toggled to expanded there without
     * scrolling to it first (its own leading icon stayed "Expand", not "Collapse" — confirmed via a
     * semantics-tree dump, not assumed), the promoted content above it having pushed it far enough
     * down the scrolled column to expose the gap.
     */
    @Test
    fun `Search this location is reachable inside Advanced search`() {
        setScreen(SEARCHED_STATE)

        openSearchDropdown()
        composeRule.onNodeWithText("Advanced search").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Enter coordinates manually").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Search this location").performScrollTo().assertIsDisplayed()
    }

    /**
     * Radius, month, and — as of the map/navigation search-UI redo dispatch — the Location row
     * ("Set on map"/"Use current location") all now live at [SearchDropdown]'s own top level.
     * Unlike manual coordinates (the test above), these must be reachable *without* expanding
     * "Advanced search" at all, which is the whole point of promoting them; asserting them in the
     * same test as the still-nested "Search this location" (which does expand that section)
     * wouldn't actually prove that. "Set on map"/"Use current location" are asserted absent from
     * a *second* expand of Advanced search too — moved, not duplicated.
     */
    @Test
    fun `search radius, month, and the location row are reachable without expanding advanced search`() {
        setScreen(SEARCHED_STATE)

        openSearchDropdown()

        // 8 km, not SEARCHED_STATE's own REGION.radiusKm (15) -- this text reads uiState.radiusKm,
        // the slider's own current value, a separate field from the region that actually got
        // searched (SEARCHED_STATE leaves it at AvailabilityUiState's own default, 8).
        listOf(
            "Set on map",
            "Use current location",
            "Search radius: 8 km",
            "Month",
        ).forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("Advanced search").assertIsDisplayed()

        composeRule.onNodeWithText("Advanced search").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Set on map").assertCountEquals(1)
        composeRule.onAllNodesWithText("Use current location").assertCountEquals(1)
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
        composeRule.onNodeWithContentDescription("Remove planned trip for ${trip.date}")
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

        composeRule.onNodeWithText("Distance Unit").assertIsDisplayed()
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
     * "Use current location" in the drawer's promoted Location row — map/navigation search-UI
     * redo dispatch. This used to be a shortcut icon on the species search field itself (see this
     * test's prior version); that icon is gone now ([SearchEntryBar] passes
     * `showLocationTrailingIcon = false` to match the reference bar exactly, which shows nothing
     * but the magnifying glass and the text), and "Use current location" is a first-class,
     * promoted action in the drawer instead. Still has to call the same
     * [AvailabilityScreen.onUseCurrentLocation] callback [RegionControls]' own button calls
     * (inside that same dropdown's nested "Advanced search" section) — not a second location-fetch
     * path — which this proves by wiring a recorder into that single callback.
     */
    @Test
    fun `the drawer's Use current location button calls onUseCurrentLocation`() {
        var callCount = 0
        setScreen(SEARCHED_STATE, onUseCurrentLocation = { callCount++ })

        openSearchDropdown()
        composeRule.onNodeWithText("Use current location").performClick()

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
private val StubMapSlot: MapSlot = { _, _, renderMode, _, _, _, _, _, modifier ->
    capturedRenderMode = renderMode
    Box(modifier.testTag(MAP_SLOT_TAG))
}

/**
 * The last [com.forager.app.ui.map.MapRenderMode] [StubMapSlot] was called with — fullscreen-fixes
 * dispatch, Item 1: lets a test confirm [com.forager.app.ui.map.MapRenderMode.bottomInset] is
 * actually reaching the map, since the stub renders nothing real for a device-style attribution
 * check to inspect.
 */
private var capturedRenderMode: com.forager.app.ui.map.MapRenderMode? = null

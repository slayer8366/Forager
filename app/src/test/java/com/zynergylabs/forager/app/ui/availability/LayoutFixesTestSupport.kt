package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import java.time.LocalDate
import org.junit.rules.ExternalResource
import org.robolectric.Shadows

/**
 * Shared fixtures for the Part 1 layout-fix tests (dispatch `2026-09-28-78`, with `-88`, `-98` and
 * `-99`): the host activity, a map stub that takes pointer input as the real `AndroidView` does and
 * records what it is handed, the screen with the inputs these tests vary, and bounds helpers.
 */

internal fun layoutFixesHostActivityRule() = object : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}

internal const val LAYOUT_FIXES_MAP_TAG = "map-slot"

/**
 * Stands in for the real map. Like the real map's `AndroidView`, it takes pointer input over its whole
 * bounds, so a touch no control claims lands here and is counted ([taps]); and it records the last
 * [MapRenderMode] and [MapOverlayContent] it was handed.
 */
internal class LayoutFixesMapSlot {
    var renderMode: MapRenderMode? = null
    var content: MapOverlayContent? = null
    var taps = 0

    val slot: MapSlot = { _, content, renderMode, _, _, onTap, _, _, modifier ->
        this.renderMode = renderMode
        this.content = content
        Box(
            modifier
                .testTag(LAYOUT_FIXES_MAP_TAG)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    taps++
                    onTap()
                },
        )
    }
}

internal val LAYOUT_FIXES_REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)
internal val LAYOUT_FIXES_FIX_LOCATION = LatLng(45.3301, -122.6402)

/** A searched state with a live fix, so the compass strip shows its coordinate readout. */
internal val LAYOUT_FIXES_FIX_STATE = AvailabilityUiState(
    region = LAYOUT_FIXES_REGION,
    liveFix = LocationFix.Update(
        lat = LAYOUT_FIXES_FIX_LOCATION.lat,
        lng = LAYOUT_FIXES_FIX_LOCATION.lng,
        altitude = 123.0,
        accuracyMeters = 5f,
        timestampEpochMillis = System.currentTimeMillis(),
        provider = FixProvider.GPS,
    ),
)

/** One saved day entry shown on the map, so J8's chip shows ("1 journal entry on map"). */
internal val LAYOUT_FIXES_SHOWN_ENTRY_STATE = CartographyUiState(
    entries = listOf(
        CartographyEntry.draft(id = "entry-shown", date = LocalDate.of(2026, 9, 27), updatedAtEpochMillis = 1_000L)
            .copy(isDraft = false, text = "Shown on the map.", shownOnMap = true),
    ),
)

/** The cells in view, as the map reports them, so the legend chip shows (as the L0b legend tests do). */
internal val LAYOUT_FIXES_DATES = mapOf(
    MapLayerIds.FORECAST_CHANTERELLES to ForecastCellsShown(week = MAP_LAYERS_TEST_WEEK, weatherThrough = LocalDate.of(2026, 9, 26)),
    MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS to ForecastCellsShown(week = MAP_LAYERS_TEST_WEEK, weatherThrough = LocalDate.of(2026, 9, 25)),
)

@Composable
internal fun LayoutFixesScreen(
    uiState: AvailabilityUiState,
    mapSlot: MapSlot,
    cartographyUiState: CartographyUiState = CartographyUiState(),
    isRecording: Boolean = false,
    onToggleRecording: () -> Unit = {},
    onToggleReturning: () -> Unit = {},
) {
    AvailabilityScreen(
        uiState = uiState,
        recording = AvailabilityScreenRecording(
            isRecording = isRecording,
            onToggleRecording = onToggleRecording,
        ),
        navigation = AvailabilityScreenNavigation(
            onToggleReturning = onToggleReturning,
        ),
        searchInputs = AvailabilityScreenSearchInputs(
            onUseCurrentLocation = {},
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
        mapSlot = mapSlot,
        journalEntries = AvailabilityScreenJournalEntries(
            cartographyUiState = cartographyUiState,
        ),
    )
}

internal fun DpRect.overlapsRect(other: DpRect): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

internal fun DpRect.describe(): String = "[%.1f, %.1f][%.1f, %.1f]".format(left.value, top.value, right.value, bottom.value)

internal fun ComposeTestRule.bounds(node: SemanticsNodeInteraction): DpRect = node.getUnclippedBoundsInRoot()

/** A real touch at ([x], [y]) dp on the app's own window (the first root: a popup is a window of its own). */
internal fun ComposeTestRule.touchAt(x: Dp, y: Dp) {
    val at = with(density) { Offset(x.toPx(), y.toPx()) }
    onAllNodes(isRoot()).onFirst().performTouchInput { click(at) }
    waitForIdle()
}

/** A long-press drag from ([x], [y]) by ([dx], [dy]) dp, as the cluster's handles are dragged. */
internal fun ComposeTestRule.longPressDrag(x: Dp, y: Dp, dx: Dp, dy: Dp) {
    val start = with(density) { Offset(x.toPx(), y.toPx()) }
    val delta = with(density) { Offset(dx.toPx(), dy.toPx()) }
    onAllNodes(isRoot()).onFirst().performTouchInput {
        down(start)
        advanceEventTime(600)
        moveTo(start + delta)
        advanceEventTime(50)
        up()
    }
    waitForIdle()
    mainClock.advanceTimeBy(2_000)
    waitForIdle()
}

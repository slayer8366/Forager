package com.zynergylabs.forager.app.ui.availability

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.GroundHeight
import com.zynergylabs.forager.app.domain.GroundHeightSource
import com.zynergylabs.forager.app.domain.GroundHeightUnknown
import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.PlannedTripRepository
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MgrsCoordinate
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDialog

// Dispatch 2026-11-05-05: what the trip bubble, trip card, trip navigation and Directions-beside-Navigate tests share.

/** The planned trip those tests draw, with no route, so a tap on its flag opens its bubble. */
internal val SADDLE = PlannedTrip("trip-1", "Saddle loop", LatLng(45.326, -122.634), LocalDate.of(2026, 10, 3))

/** [SADDLE]'s decimal degrees as its Trip Planner row and its card write them, and as its bubble no longer does. */
internal const val SADDLE_COORDINATES = "45.3260, -122.6340"

/** The `geo:` hand-off [launchDirections] builds for [SADDLE]. */
internal const val SADDLE_GEO = "geo:0,0?q=45.326,-122.634(Saddle%20loop)"

/** [SADDLE]'s flag on the stub map, clear of the chrome on the left half below the compass strip. */
internal val SADDLE_GLYPH = StubGlyph(MapLayerIds.PLANNED_TRIPS, SADDLE.id, 60.dp, 380.dp, SADDLE.location)

/** The MGRS line a row, a bubble and a card show for [at]. */
internal fun gridOf(at: LatLng): String = (MgrsConverter.convert(at) as MgrsCoordinate.Grid).value

/** Planned trips in memory, as the app's store holds them; while [gate] is set every read waits on it, as a slow first load does. */
internal class HeldTripRepository(initial: List<PlannedTrip>) : PlannedTripRepository {
    private val trips = initial.associateByTo(linkedMapOf()) { it.id }
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun getAll(): Result<List<PlannedTrip>> {
        gate?.await()
        return Result.success(trips.values.toList())
    }

    override suspend fun save(trip: PlannedTrip): Result<Unit> = Result.success(Unit).also { trips[trip.id] = trip }

    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit).also { trips.remove(id) }
}

/** Ground heights by spot; anything else is not downloaded. Every point asked for is recorded. */
internal class SpotGroundHeights(private val known: Map<LatLng, GroundHeight>) : GroundHeightSource {
    val asked = mutableListOf<LatLng>()

    override suspend fun groundHeightAt(point: LatLng): GroundHeight {
        asked += point
        return known[point] ?: GroundHeight.Unknown(GroundHeightUnknown.NOT_DOWNLOADED)
    }
}

/** A maps app that takes `geo:` links, so [launchDirections] starts it rather than saying none is installed. */
internal fun registerMapsApp(activity: Activity) {
    val componentName = ComponentName(activity, "com.example.fakemaps.MapsActivity")
    val shadowPackageManager = Shadows.shadowOf(activity.packageManager)
    shadowPackageManager.addActivityIfNotPresent(componentName)
    shadowPackageManager.addIntentFilterForActivity(
        componentName,
        IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("geo")
        },
    )
}

/** The `geo:` link of the activity started last, taken off the list, or `null` for none. */
internal fun takeStartedGeo(activity: Activity): String? =
    Shadows.shadowOf(activity).nextStartedActivity?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString()

/** Scrolls [tag] into view where it sits in something that scrolls, as a card's buttons do; a bubble's buttons do not. */
internal fun ComposeTestRule.scrollIntoViewIfScrollable(tag: String) {
    if (onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) onNodeWithTag(tag).performScrollTo()
    waitForIdle()
}

/** One real touch at ([fx], [fy]) of [tag]'s own bounds, scrolled into view first where it sits in a scrolling card. */
internal fun ComposeTestRule.touchAcrossTag(tag: String, fx: Float, fy: Float) {
    scrollIntoViewIfScrollable(tag)
    onNodeWithTag(tag).performTouchInput { click(Offset(width * fx, height * fy)) }
    waitForIdle()
}

/** A real Back key, down and up, on the details sheet's own window (the latest dialog shown), as RecordDetailsSheetTest sends it. */
internal fun ComposeTestRule.pressBackOnSheetWindow() {
    val dialog = ShadowDialog.getLatestDialog()
    assertTrue("a sheet window must be showing to press Back on", dialog != null && dialog.isShowing)
    runOnUiThread {
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
    }
    waitForIdle()
}

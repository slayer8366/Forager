package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.StraightReason
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import org.junit.rules.ExternalResource
import org.robolectric.Shadows

// Shared by the route-plotting screen tests (dispatch 2026-10-30-08), moved unchanged from RoutePlottingScreenTest.kt.

internal typealias RouteRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/**
 * A map slot for the route-plotting tests: [StubGlyph]s that report a feature tap through `renderMode.onFeatureTap` on a real
 * touch, as `SightingsMap` does; a map surface that counts real taps and real long-presses; and the last content and render
 * mode the screen handed it.
 */
internal class RouteMapSlot(private val glyphs: List<StubGlyph>) {
    var renderMode: MapRenderMode? = null
    var content: MapOverlayContent? = null
    var longPresses = 0
    var taps = 0

    val slot: MapSlot = { _, content, renderMode, _, _, onTap, _, _, modifier ->
        this.renderMode = renderMode
        this.content = content
        val density = LocalDensity.current
        Box(modifier.testTag("map-slot").pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }, onTap = { taps++; onTap() }) }) {
            glyphs.filter { it.layerId != MapLayerIds.PLANNED_TRIPS || content.plannedTrips.any { trip -> trip.id == it.featureId } }.forEach { glyph ->
                Box(
                    Modifier
                        .offset(glyph.x - 12.dp, glyph.y - 12.dp)
                        .size(24.dp)
                        .testTag(glyphTag(glyph.featureId))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            val centre = with(density) { Offset(glyph.x.toPx(), glyph.y.toPx()) }
                            renderMode.onFeatureTap(MapFeatureTap(glyph.layerId, glyph.featureId, centre, 0f, glyph.at))
                        },
                )
            }
        }
    }
}

internal val CREEK = Waypoint("wp-creek", 45.326, -122.634, null, "Creek", "", 1_000L)
internal val RIDGE = Waypoint("wp-ridge", 45.336, -122.644, null, "Ridge", "", 2_000L)
internal fun Waypoint.stop() = RouteStop(name, LatLng(lat, lng))
internal fun straight(a: LatLng, b: LatLng) = RouteLeg.Straight(listOf(a, b), GeoDistance.metersBetween(a, b), StraightReason.NO_REGION_COVERS)
internal val SAVED_ROUTE = PlottedRoute(listOf(CREEK.stop(), RIDGE.stop()), listOf(straight(CREEK.stop().position, RIDGE.stop().position)))
internal val ROUTE_TRIP = PlannedTrip("route-1", "Creek to Ridge", CREEK.stop().position, null, SAVED_ROUTE)

/** Five points across a node's own bounds: the four corners inset 10%, and the centre (CLAUDE.md: a finger is not a point). */
internal val ACROSS = listOf(0.1f to 0.1f, 0.9f to 0.1f, 0.5f to 0.5f, 0.1f to 0.9f, 0.9f to 0.9f)

internal fun hostActivity() = object : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}

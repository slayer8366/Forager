package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting

/**
 * The map, as a slot the screen fills rather than a call the screen makes.
 *
 * This is the same seam [com.zynergylabs.forager.app.domain.MushroomRepository] puts in front of iNaturalist,
 * applied to the UI layer: an external integration — a `View` that starts render threads, writes a
 * filesystem cache under `cacheDir` and fetches tiles over the network the moment it is composed —
 * sits behind an interface this project owns, and the caller depends on the interface.
 * [com.zynergylabs.forager.app.ui.availability.AvailabilityScreen] previously named [SightingsMap] directly,
 * so there was no way to compose the screen without also standing up the whole tile stack.
 *
 * The vendor behind this seam changed once already, from osmdroid to MapLibre Native
 * (`docs/plans/maplibre-migration.md`), and neither [AvailabilityScreen] nor this typealias's own
 * shape had to change for it — that is the seam doing its job, not a coincidence. [SightingsMap] is
 * the only implementation either renderer's types ever touched.
 *
 * The parameters are exactly what the screen knows and the map needs. [content] is every list the
 * map draws over the basemap — see [MapOverlayContent]'s own doc comment for why those are bundled
 * rather than one parameter apiece. [onLongPress] is how the map *would* report a trip-planning
 * gesture back up, when wired — the caller turns the reported point into a plan/log action without
 * this composable knowing anything about dates or persistence. **No production call site wires it
 * as of 2026-08-28** — both `AvailabilityScreen.kt` call sites (`MapTab`, `CompactMapTab`) pass
 * `{}`. Kept deliberately, not left by accident (see [SightingsMap]'s own doc comment on the same
 * parameter for the fuller reasoning); the interaction it used to drive now goes through panning
 * the camera, tapping add (+), and confirming via `CentrePinLocationPickerOverlay` instead.
 * [Basemap] crosses this seam as this
 * project's own type, not a vendor tile-source type, for the same reason the rest of the seam
 * exists: the screen names the basemap it wants and stays ignorant of which vendor supplies the
 * tiles. [modifier] is last because it is the slot's *size contract* — the screen decides how much
 * room the map gets, which is the one thing about this arrangement the screen is actually
 * responsible for.
 */
typealias MapSlot = @Composable (
    region: Region,
    content: MapOverlayContent,
    renderMode: MapRenderMode,
    /**
     * When non-null, pans the camera here instead of [region]'s own centre — the map redesign's
     * GPS/locate-me icon (distinct from [region], which stays the search centre; see that button's
     * call site in `AvailabilityScreen.kt`'s `CompactMapTab` for why this doesn't touch the search
     * itself).
     */
    focusOverride: LatLng?,
    onLongPress: (LatLng) -> Unit,
    /**
     * Fires on a plain tap anywhere on the map — the map redesign's "tap the map to restore
     * chrome" while fullscreen (see `CompactMapTab`'s call site). `{}` by every caller that has no
     * fullscreen chrome to restore, same default-ignoring shape as [onLongPress] gets from callers
     * with nothing to plan.
     */
    onTap: () -> Unit,
    /**
     * Fires instead of [onTap] when the tap actually lands on a real observation dot — see
     * [SightingsMap]'s own doc comment ("Partially rebuilt") for how that's resolved
     * (`queryRenderedFeatures` against the sighting layer, matched back to the tapped [Sighting] by
     * `observationId`). Every production call site wires this to show a species-name/date detail
     * with a "View on iNaturalist" action; `{}` is still the right default for anything that has no
     * such detail view (tests, previews).
     *
     * The [Offset] is that [Sighting]'s own current on-screen position (px, in this slot's own
     * coordinate space); the [Float] is the camera's current bearing (degrees clockwise from
     * north). Both re-fire on every camera move for as long as the same sighting stays tapped, not
     * just once at tap time, so a caller's detail bubble can stay glued to its marker — in both
     * position and orientation — across a pan/zoom/rotate instead of reading as detached from
     * whichever dot it was originally about, or pointing the wrong way once the map has turned.
     */
    onSightingTap: (Sighting, Offset, Float) -> Unit,
    /**
     * Fires with the geographic point under the screen's centre every time the camera finishes
     * moving (a pan, a fling settling, a programmatic jump) — the read side of [region]/
     * [focusOverride]'s write-only camera control, added for [CentrePinLocationPicker]: a picker
     * that keeps a marker fixed at screen centre while the map pans underneath it needs to know
     * *where* centre currently points to answer "what did the user pick," which nothing before
     * this parameter existed could report. `{}` by every caller that isn't tracking the camera —
     * the same default-ignoring shape [onTap] already established.
     */
    onCameraIdle: (LatLng) -> Unit,
    modifier: Modifier,
) -> Unit

/**
 * The real map. This is the default every production call path gets, so introducing the seam
 * changed no caller: `MainActivity` passes nothing new.
 *
 * [onSightingTap] brought this typealias's declared parameter count to 9 — still short of the 10
 * that previously crashed the Compose compiler lowering this exact lambda (see
 * [MapOverlayContent]'s own doc comment for that `ComposableFunctionBodyTransformer` failure and
 * why [MapOverlayContent] exists at all); confirmed by this file's own
 * `./gradlew :app:compileDebugKotlin` passing with this parameter added, not assumed safe from the
 * count alone.
 */
val SightingsMapSlot: MapSlot = { region, content, renderMode, focusOverride, onLongPress, onTap, onSightingTap, onCameraIdle, modifier ->
    // A start-up failure shows a message with Try again in the map's place instead of throwing (MapStartGate).
    MapStartGate(modifier) {
        SightingsMap(
            region = region,
            sightings = content.sightings,
            plannedTrips = content.plannedTrips,
            basemap = renderMode.basemap,
            nightMode = renderMode.night,
            nightModeLoaded = renderMode.nightModeLoaded,
            focusOverride = focusOverride,
            onLongPress = onLongPress,
            onTap = onTap,
            onSightingTap = onSightingTap,
            onCameraIdle = onCameraIdle,
            breadcrumbPoints = content.breadcrumbPoints,
            waypoints = content.waypoints,
            resumeTrackingRequestId = content.resumeTrackingRequestId,
            searchFrameRequestId = content.searchFrameRequestId,
            resetOrientationRequestId = content.resetOrientationRequestId,
            focusedObservationId = content.focusedObservationId,
            focusedFeature = content.focusedFeature,
            trackLiveLocation = renderMode.trackLiveLocation,
            showSearchCentre = renderMode.showSearchCentre,
            useOfflineTiles = renderMode.useOfflineTiles,
            keptTrackPolylines = content.keptTrackPolylines,
            findMarkers = content.findMarkers,
            photoMarkers = content.photoMarkers,
            offlineRegionCircles = content.offlineRegionCircles,
            bottomInset = renderMode.bottomInset,
            onUserCameraGesture = renderMode.onUserCameraGesture,
            layersState = renderMode.layers,
            onFeatureTap = renderMode.onFeatureTap,
            onCloseBubble = renderMode.onCloseBubble,
            forecast = renderMode.forecast,
            cameraRequest = renderMode.cameraRequest,
            journalHighlights = content.journalHighlights,
            route = content.route,
            cameraMemory = renderMode.cameraMemory,
            leavingTab = renderMode.leavingTab,
            attributionEndInset = renderMode.attributionEndInset,
            attributionBottomInset = renderMode.attributionBottomInset,
            attributionKeepClear = renderMode.attributionKeepClear,
            attributionBesideCredit = renderMode.attributionBesideCredit,
            returnMemory = renderMode.returnMemory,
            backEnabled = renderMode.backEnabled,
            navigationView = renderMode.navigationView,
            trueHeading = renderMode.trueHeading,
            nudgeSpringBack = renderMode.nudgeSpringBack,
            modifier = modifier,
        )
    }
}

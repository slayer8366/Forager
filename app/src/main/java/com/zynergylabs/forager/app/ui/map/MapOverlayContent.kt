package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Waypoint

/** A point feature a caller is showing a bubble for, by layer and feature id (M1, F2): see [MapOverlayContent.focusedFeature]. */
data class FocusedMapFeature(val layerId: String, val featureId: String)

data class MapOverlayContent(
    val sightings: List<Sighting> = emptyList(),
    val plannedTrips: List<PlannedTrip> = emptyList(),
    /**
     * The active track's recorded points, oldest first, drawn as a growing trail — empty whenever
     * nothing is being recorded: a breadcrumb trail is where the device actually walked.
     */
    val breadcrumbPoints: List<LatLng> = emptyList(),
    /** Saved waypoints, drawn as markers — independent of any track, per [Waypoint]'s own doc comment. */
    val waypoints: List<Waypoint> = emptyList(),
    /**
     * A changing token, not a boolean flag — incrementing it (regardless of the new value) tells
     * the map to (re-)engage GPS camera tracking, the same "GPS icon re-centers and resumes
     * following" behavior the project owner asked for. A `Boolean` can't carry a repeat action:
     * tapping locate-me a second time while already tracking (e.g. after wandering off tracking via
     * a pan) needs to fire again even though the *value* "tracking wanted" never changed from
     * `true`; a changing `Int` always represents a fresh request, a toggled `Boolean` would only
     * fire on every other tap. See [com.zynergylabs.forager.app.ui.map.SightingsMap]'s own
     * `activateLiveLocationIfPermitted` for what actually engaging tracking means, and why this is
     * a request rather than a live camera-mode readout (this app's map layer doesn't expose the
     * live/lost-tracking state back up to `AvailabilityScreen` — the compact map icon stack's
     * locate-me icon looks the same whether or not GPS tracking is currently engaged).
     */
    val resumeTrackingRequestId: Int = 0,
    /**
     * RECORD -750, item 5 (the owner: "Yes, fly to the search (Recommended)"): a changing token, one per location search the
     * user ran (a recent search, coordinates, Set on map, Search with the current location). Each new value stops the camera
     * following the GPS fix and moves it to frame [region], which following otherwise blocks ([shouldMoveCameraToTarget]).
     * Applied once per value, the applied value kept in [MapRenderMode.cameraMemory] where there is one, so a map that comes
     * back after a tab change does not fly again over a camera the user has since moved. 0, the default, asks for nothing.
     * The locate button resumes following ([resumeTrackingRequestId]).
     */
    val searchFrameRequestId: Int = 0,
    /**
     * Same changing-token shape as [resumeTrackingRequestId], for the same reason — a `Boolean`
     * can't represent "do it again" when the value wouldn't otherwise change. This one resets the
     * camera's bearing to north, the map redesign's own custom replacement for MapLibre's native
     * compass view (disabled in [SightingsMap] — see that composable's own doc comment on why:
     * the map icon bar's orientation-reset control needed a real callback to trigger, which the
     * SDK's own compass widget doesn't expose one for).
     */
    val resetOrientationRequestId: Int = 0,
    /**
     * Which [Sighting] (by [Sighting.observationId]), if any, the caller is currently showing an
     * observation bubble for — null once the caller has dismissed it, whether by its own close icon
     * or by tapping elsewhere on the map. [SightingsMap] re-derives its own "which sighting to keep
     * re-projecting on camera idle" state from this on every recomposition rather than latching it
     * internally at tap time and never clearing it: a real hardware report found the bubble
     * reappearing after a dismiss, on the very next pan, with no sighting tapped — the previous
     * internal `focusedSighting` var was set once when a dot was tapped and never told about a
     * later dismissal (the caller's own close/tap-elsewhere handling is pure Compose state on the
     * [AvailabilityScreen] side, and never reaches this far down), so every subsequent camera-idle
     * event kept re-firing `onSightingTap` for the same sighting and silently undid the dismissal.
     * Threading the caller's own dismissed-or-not state back in here as plain data closes that gap:
     * once the caller's tracked sighting goes null, this goes null too on the next recomposition,
     * and the camera-idle listener has nothing left to re-fire for.
     */
    val focusedObservationId: Long? = null,
    /**
     * M1 (F2): the point feature, if any, the caller is showing a bubble for, generalising
     * [focusedObservationId] to finds, photos, waypoints and planned trips. On every camera idle the
     * map re-projects that glyph's own position and reports it through [MapRenderMode.onFeatureTap],
     * so the bubble stays on its glyph across a pan, zoom or rotate. `null` once the caller has
     * dismissed the bubble, for the reason [focusedObservationId] gives: a dismissal must not be
     * undone by the next idle. Lines, regions and cells are never focused here; their bubbles keep
     * the tap point (planner's M1 ruling).
     */
    val focusedFeature: FocusedMapFeature? = null,
    /**
     * Journal Stage 2d: a Cartography entry's kept tracks, one [RecordPolyline] per track, each oldest
     * point first — a genuine `MultiLineString`, not [breadcrumbPoints] concatenated. A single
     * `List<LatLng>` (what [breadcrumbPoints] already is) can only ever draw as one connected
     * `LineString` (see [breadcrumbFeatureCollection]); two kept tracks drawn that way would show a
     * spurious straight-line jump between the end of one and the start of the other. This is a
     * genuinely separate field, not a reshaping of [breadcrumbPoints], so every existing caller
     * (both `AvailabilityScreen.kt` call sites) is unaffected by construction — neither sets it, and
     * its default is empty. A kept track that no longer resolves (deleted from Records) is simply
     * absent from this list by the time it reaches here — see [com.zynergylabs.forager.app.domain.GetCartographyEntryMapDataUseCase]'s
     * own doc comment for where that resolution happens; this composable never knows a track was
     * ever kept, only what actually resolved.
     *
     * Each item carries its record's id beside its geometry since map layers L0a ([RecordPoint],
     * [RecordPolyline], [RecordRegion]), written into its map feature so a tap can name the record.
     */
    val keptTrackPolylines: List<RecordPolyline> = emptyList(),
    /**
     * Journal Stage 2d: a Cartography entry's kept finds with a resolved coordinate — drawn as
     * discrete markers, each a [SymbolLayer][org.maplibre.android.style.layers.SymbolLayer] like the
     * [Waypoint] markers; since colour build C2 a find is a mushroom in its own colour, no longer the
     * waypoint's pin in the offline region's colour (`MarkerGlyphs.kt`). A find with no coordinate (the ordinary case — see
     * [com.zynergylabs.forager.app.domain.model.MushroomLogEntry.foundAt]'s own doc comment) is simply absent
     * from this list, never a placeholder point.
     *
     * Each item carries its record's id beside its geometry since map layers L0a ([RecordPoint],
     * [RecordPolyline], [RecordRegion]), written into its map feature so a tap can name the record.
     */
    val findMarkers: List<RecordPoint> = emptyList(),
    /**
     * Journal Stage 2d: a Cartography entry's kept photos with a resolved coordinate (both
     * [com.zynergylabs.forager.app.domain.model.LogPhoto.latitude]/`.longitude` non-null, and the gallery row
     * still present) — most existing photos will have neither, which is normal, not an error; see
     * [com.zynergylabs.forager.app.domain.model.LogPhoto]'s own doc comment on the two ways a photo gains a
     * coordinate. Also a discrete [SymbolLayer][org.maplibre.android.style.layers.SymbolLayer] marker: since
     * colour build C2 a rounded square with a camera, in its own colour, no longer the planned trip's
     * diamond.
     *
     * Each item carries its record's id beside its geometry since map layers L0a ([RecordPoint],
     * [RecordPolyline], [RecordRegion]), written into its map feature so a tap can name the record.
     */
    val photoMarkers: List<RecordPoint> = emptyList(),
    /**
     * Journal Stage 2d: a Cartography entry's kept offline regions, drawn as a translucent coverage
     * circle each — their snapshot already carries lat/lng/radius (see [Region]'s own shape), so
     * unlike tracks/finds/photos this needs no live fetch to resolve at all. Whether this is more
     * useful than cluttered is an open visual question the dispatch that added this explicitly left
     * to be reported on after building it, not decided in advance.
     *
     * Each item carries its record's id beside its geometry since map layers L0a ([RecordPoint],
     * [RecordPolyline], [RecordRegion]), written into its map feature so a tap can name the record.
     */
    val offlineRegionCircles: List<RecordRegion> = emptyList(),
    /**
     * J8: the records kept by the entries shown on the map, highlighted in place by the five
     * journal-entry halo layers under their own records (`GetJournalEntryHighlightsUseCase`, live
     * geometry only). Only the Maps tab sets it; every other map draws no highlight.
     */
    val journalHighlights: JournalEntryHighlights = JournalEntryHighlights.NONE,
    /**
     * The way back while returning (dispatch 2026-09-28-497, plan task T7): the line at Return
     * (dimmed), the line ahead (bright, faded while withheld), and the start's place once arrived.
     * `null` on every map but the Maps tab while navigating.
     */
    val route: RouteOnMap? = null,
)

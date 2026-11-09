package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region

/**
 * The camera a map showed when it last settled (Part 1 layout fixes, item 4): where it looked, how
 * close, turned and tilted how far, and whether the location puck was leading it.
 *
 * [appliedTarget] is the search region and locate-me target the map had last moved the camera to
 * itself ([SightingsMap]'s `lastAppliedCameraTarget`). A map restoring this camera takes it as already
 * applied, so the same target does not move the camera again, while a new search still does.
 */
data class MapCameraSnapshot(
    val target: LatLng,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double,
    /** Whether live-location tracking owned the camera (the user had not panned away from it). */
    val following: Boolean,
    val appliedTarget: Pair<Region, LatLng?>?,
)

/**
 * The Maps tab's camera, kept for the session above the tab switch (planner message `2026-09-28-98`,
 * under CLAUDE.md's UX defaults: what the user has set survives navigating away and back). The map
 * leaves composition with its tab, and its `MapView` holds the camera, so a map that came back would
 * otherwise open where a fresh map does.
 *
 * [saved] is a plain field, not Compose state: the map writes it on every camera idle, which happens
 * continuously while the puck leads, and nothing should recompose for that. It is read once, when a new
 * map first loads its style. Nothing is persisted: a restart opens a fresh map, as before.
 */
class MapCameraMemory {
    var saved: MapCameraSnapshot? = null

    /**
     * RECORD -750, item 5: the last [MapOverlayContent.searchFrameRequestId] a map applied, kept here rather than with the
     * `MapView`, so the map that comes back after a tab change (or after the search's loading spinner replaced it) applies
     * a search's frame once, not again over the camera the user has moved since.
     */
    var appliedSearchFrameId: Int = 0

    /**
     * RECORD -761: the picture a leaving map took of itself, for the map that comes back to draw over its own first frames
     * ([MapReturnCover.kt]). One at a time: [keepCover] recycles the one it replaces; the returning map takes it with
     * [takeCover], which leaves none here.
     */
    var cover: MapCover? = null
        private set

    fun keepCover(new: MapCover) {
        val old = cover
        cover = new
        if (old != null && old.bitmap !== new.bitmap) old.bitmap.recycle()
    }

    fun takeCover(): MapCover? = cover.also { cover = null }

    /**
     * RECORD -761 (the owner: "Keep last reading (Recommended)"): the compass strip's last true heading, kept above the tab like
     * the camera, so a return to Maps shows it until the compass reports again rather than "—" ([rememberTrueHeading]).
     */
    val heading: HeadingMemory = HeadingMemory()
}

package com.zynergylabs.forager.app.ui.map

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.zynergylabs.forager.app.map.TRAIL_STYLE_URL

/**
 * Trail Map's fallback (RECORD -1513). The owner chose "Fall back automatically": "If our Street fails to load, the
 * app quietly draws today's OpenStreetMap Street instead and logs it; built as a small PR before the switch flips."
 *
 * The planner's rulings on the verify-first (dispatch of 2026-10-21, PR A):
 * - **What counts as a failure:** only a failed style load, `MapView.OnDidFailLoadingMapListener` while the last style
 *   this MapView asked for is Trail Map, online. Tile errors are logged ([onTileError]) and never fall back.
 *   The limit, stated plainly: the Worker serves the style with a one-day cache, so with a warm cache a Worker that is
 *   down still gives a style that loads, and only the areas not seen before stay blank. This catches a cold or
 *   expired cache, not a warm one.
 * - **What it falls back to:** [mapStyleSourceFor] with `ownTrail = false`: OpenStreetMap's raster in place of Forager
 *   Trail Map, and at Topographical night the raster style with OpenStreetMap below. The caption, the zoom limit and
 *   the navigation zoom read the same flag, so the credit is OpenStreetMap's and the limit its 19. Since dispatch
 *   2026-10-22-01 Trail Map is one of three map types and the compile-time switch is gone; these rules are unchanged.
 * - **When our Street is tried again:** when the connection returns ([onSignalReturned]), and at the next cold start.
 *   Until dispatch 2026-10-27-09 Amendment 1 (RECORD -1739) it was the cold start alone, a planner's ruling written for
 *   a Worker that is down. The case it missed: with no signal and no cached style, Trail Map's load fails too, and the
 *   map stayed on OpenStreetMap after the connection came back (the S22, card step 1 of
 *   docs/audits/2026-10-27-s22-check-v3-8d.md). The owner: "Yes, retry after 30 s (Recommended)". The retry waits for
 *   the map's own "no signal" state to end, a validated network held 30 s (`noSignalState`), so the map changes at
 *   most once per return of the signal and never flips back and forth faster than the no-signal switch already does.
 *   Any fallback is retried, whatever caused it; if Trail Map still cannot load, [onStyleLoadFailed] falls back again
 *   and logs it. Rejected: retrying at once on the raw signal (it reloads at the edge of coverage), and retrying only
 *   a fallback taken with no signal (it races the first network reading at a cold start, the very S22 case).
 * - **Offline mode:** never falls back. Downloaded regions are drawn from MapLibre's offline store.
 * - The Sources and open-source pages stay as they are.
 */
internal class OwnTrailFallback(private val log: (String) -> Unit = { Log.w(OWN_TRAIL_FALLBACK_LOG_TAG, it) }) {

    // Compose state, so the caption and the style effect, which read it in composition, follow it when it flips.
    private val fellBackState = mutableStateOf(false)
    private var tileErrorsLogged = 0

    /** Whether Trail Map failed to load in this process. */
    val fellBack: Boolean get() = fellBackState.value

    /**
     * Whether Trail Map draws now: `true` unless its style has failed to load in this process. [switch] was the release
     * switch, removed by dispatch 2026-10-22-01; it stays a parameter, defaulting to `true`, only so this class's own
     * tests are untouched by that change (the dispatch: don't touch this fallback's rules).
     */
    fun ownTrail(switch: Boolean = true): Boolean = effectiveOwnTrail(switch, fellBack)

    /**
     * MapLibre failed to load a style ([message] is its own) and [request] is what this MapView last asked for. Falls
     * back, and logs one line, the first time this is Trail Map online; returns whether it fell back now.
     */
    fun onStyleLoadFailed(request: MapStyleRequest?, message: String?): Boolean {
        if (request == null || !fallsBack(request.source, request.useOfflineTiles) || fellBack) return false
        fellBackState.value = true
        log("Trail Map style failed to load ($message); drawing ${trailMapFallbackDrawn()} until the connection returns or the app restarts.")
        return true
    }

    /**
     * The map's "no signal" state has ended (`noSignalState`: a validated network held 30 s). If Trail Map has
     * fallen back, the flag clears and Trail Map is tried again, with one log line; every reader follows, since the flag
     * is Compose state. Otherwise nothing changes and nothing is logged. The tile-error count is not reset.
     */
    fun onSignalReturned() {
        if (!fellBack) return
        fellBackState.value = false
        log("Connection back: trying Forager Trail Map again.")
    }

    /**
     * A tile failed to load on [sourceId] while [request] is the current style: logged while Trail Map is showing, a
     * few lines a process. Under Topographical at night the source may be OpenTopoMap's, so the line names the source and
     * does not call it an own-Street tile.
     */
    fun onTileError(request: MapStyleRequest?, sourceId: String?, z: Int, x: Int, y: Int) {
        if (request == null || !fallsBack(request.source, request.useOfflineTiles)) return
        if (tileErrorsLogged >= OWN_TRAIL_TILE_ERRORS_LOGGED) return
        tileErrorsLogged++
        val more = if (tileErrorsLogged == OWN_TRAIL_TILE_ERRORS_LOGGED) "; further tile errors are not logged in this process" else ""
        log("Map tile failed to load while Trail Map is showing: source=$sourceId z=$z x=$x y=$y (no fallback for tiles)$more")
    }
}

/** What a MapView last asked `setStyle` for: the failure listener's message names no URL, so this says whose it is. */
internal data class MapStyleRequest(val source: MapStyleSource, val useOfflineTiles: Boolean)

/**
 * Whether a failed load of [source] falls back: only Trail Map online, alone ([TRAIL_STYLE_URL]) or under
 * Topographical at night. The raster styles and every offline style never do.
 */
internal fun fallsBack(source: MapStyleSource, useOfflineTiles: Boolean): Boolean =
    !useOfflineTiles && when (source) {
        is MapStyleSource.Uri -> source.uri == TRAIL_STYLE_URL
        is MapStyleSource.TrailWithTopoOver -> true
        is MapStyleSource.Json -> false
    }

/** The switch as the map uses it: on only while [switch] is on and Trail Map has not [fellBack]. */
internal fun effectiveOwnTrail(switch: Boolean, fellBack: Boolean): Boolean = switch && !fellBack

/**
 * What a failed Trail Map style is replaced with, for the fallback's log line: the stand-in's raster while one is
 * registered (the Street map's, "OpenStreetMap's raster"), or, with none, the owner's "Downloads, then Topo" (dispatch
 * 2026-11-03-05 D8, `trailMapFailedWithNoStandIn`, `rasterDrawn`).
 */
internal fun trailMapFallbackDrawn(standIn: Basemap? = trailMapStandIn): String =
    standIn?.let { "${it.label}'s raster" } ?: "the downloaded areas, or Topographical with none"

/** The process's one fallback: every map reads it; it clears when the signal returns ([onSignalReturned]) or with the process. */
internal val OWN_TRAIL_FALLBACK = OwnTrailFallback()

internal const val OWN_TRAIL_FALLBACK_LOG_TAG = "OwnTrailFallback"
private const val OWN_TRAIL_TILE_ERRORS_LOGGED = 3

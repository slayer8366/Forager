package com.zynergylabs.forager.app.ui.map

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.zynergylabs.forager.app.map.STREET_STYLE_URL

/**
 * The own Street's fallback (RECORD -1513). The owner chose "Fall back automatically": "If our Street fails to load, the
 * app quietly draws today's OpenStreetMap Street instead and logs it; built as a small PR before the switch flips."
 *
 * The planner's rulings on the verify-first (dispatch of 2026-10-21, PR A):
 * - **What counts as a failure:** only a failed style load, `MapView.OnDidFailLoadingMapListener` while the last style
 *   this MapView asked for is the own Street, online. Tile errors are logged ([onTileError]) and never fall back.
 *   The limit, stated plainly: the Worker serves the style with a one-day cache, so with a warm cache a Worker that is
 *   down still gives a style that loads, and only the areas not seen before stay blank. This catches a cold or
 *   expired cache, not a warm one.
 * - **What it falls back to:** [mapStyleSourceFor] with `ownStreet = false`: OpenStreetMap's raster in place of Forager
 *   Trail Map, and at Topographical night the raster style with OpenStreetMap below. The caption, the zoom limit and
 *   the navigation zoom read the same flag, so the credit is OpenStreetMap's and the limit its 19. Since dispatch
 *   2026-10-22-01 Trail Map is one of three map types and the compile-time switch is gone; these rules are unchanged.
 * - **When our Street is tried again:** at the next cold start. The flag lives for the process, so the map never flips
 *   back and forth within a session, and the user is never left on a blank map by it.
 * - **Offline mode:** never falls back. Downloaded regions are drawn from MapLibre's offline store.
 * - The Sources and open-source pages stay as they are.
 */
internal class OwnStreetFallback(private val log: (String) -> Unit = { Log.w(OWN_STREET_FALLBACK_LOG_TAG, it) }) {

    // Compose state, so the caption and the style effect, which read it in composition, follow it when it flips.
    private val fellBackState = mutableStateOf(false)
    private var tileErrorsLogged = 0

    /** Whether the own Street failed to load in this process. */
    val fellBack: Boolean get() = fellBackState.value

    /**
     * Whether Trail Map draws now: `true` unless its style has failed to load in this process. [switch] was the release
     * switch, removed by dispatch 2026-10-22-01; it stays a parameter, defaulting to `true`, only so this class's own
     * tests are untouched by that change (the dispatch: don't touch this fallback's rules).
     */
    fun ownStreet(switch: Boolean = true): Boolean = effectiveOwnStreet(switch, fellBack)

    /**
     * MapLibre failed to load a style ([message] is its own) and [request] is what this MapView last asked for. Falls
     * back, and logs one line, the first time this is the own Street online; returns whether it fell back now.
     */
    fun onStyleLoadFailed(request: MapStyleRequest?, message: String?): Boolean {
        if (request == null || !fallsBack(request.source, request.useOfflineTiles) || fellBack) return false
        fellBackState.value = true
        log("Own Street style failed to load ($message); drawing OpenStreetMap's Street until the app restarts.")
        return true
    }

    /**
     * A tile failed to load on [sourceId] while [request] is the current style: logged while the own Street is showing, a
     * few lines a process. Under Topographical at night the source may be OpenTopoMap's, so the line names the source and
     * does not call it an own-Street tile.
     */
    fun onTileError(request: MapStyleRequest?, sourceId: String?, z: Int, x: Int, y: Int) {
        if (request == null || !fallsBack(request.source, request.useOfflineTiles)) return
        if (tileErrorsLogged >= OWN_STREET_TILE_ERRORS_LOGGED) return
        tileErrorsLogged++
        val more = if (tileErrorsLogged == OWN_STREET_TILE_ERRORS_LOGGED) "; further tile errors are not logged in this process" else ""
        log("Map tile failed to load while the own Street is showing: source=$sourceId z=$z x=$x y=$y (no fallback for tiles)$more")
    }
}

/** What a MapView last asked `setStyle` for: the failure listener's message names no URL, so this says whose it is. */
internal data class MapStyleRequest(val source: MapStyleSource, val useOfflineTiles: Boolean)

/**
 * Whether a failed load of [source] falls back: only the own Street online, alone ([STREET_STYLE_URL]) or under
 * Topographical at night. The raster styles and every offline style never do.
 */
internal fun fallsBack(source: MapStyleSource, useOfflineTiles: Boolean): Boolean =
    !useOfflineTiles && when (source) {
        is MapStyleSource.Uri -> source.uri == STREET_STYLE_URL
        is MapStyleSource.StreetWithTopoOver -> true
        is MapStyleSource.Json -> false
    }

/** The switch as the map uses it: on only while [switch] is on and the own Street has not [fellBack]. */
internal fun effectiveOwnStreet(switch: Boolean, fellBack: Boolean): Boolean = switch && !fellBack

/** The process's one fallback: every map reads it, and it resets only with the process (the next cold start). */
internal val OWN_STREET_FALLBACK = OwnStreetFallback()

internal const val OWN_STREET_FALLBACK_LOG_TAG = "OwnStreetFallback"
private const val OWN_STREET_TILE_ERRORS_LOGGED = 3

package com.zynergylabs.forager.app.ui.map

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.zynergylabs.forager.app.domain.NO_SIGNAL_SWITCH_DELAY_MS
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.SIGNAL_RETURN_SWITCH_DELAY_MS
import com.zynergylabs.forager.app.domain.noSignalWithHysteresis
import com.zynergylabs.forager.app.net.AndroidNetworkSignal
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * No signal on OpenStreetMap or Topographical: the map shows Forager Trail Map from the downloaded areas until the
 * signal returns (dispatch 2026-10-22-01; RECORD -1554 for OpenStreetMap, -1556 for Topographical too). The steps:
 *
 * ```
 * OSM or Topo chosen, signal lost for 5 s  → Trail Map, drawn from the downloaded areas (if any exist; else unchanged)
 * signal back for 30 s                     → the chosen map again
 * Trail chosen                             → unchanged: it is already Trail Map
 * Trail chosen, but fallen back to OSM      → as OSM: Trail Map from the downloaded areas after 5 s
 * ```
 *
 * The last line is dispatch 2026-10-22-01 Amendment 3 (Sorrel's review): once Trail Map's style has failed to load
 * (`OwnStreetFallback`, -1513) a Trail user is looking at OpenStreetMap's raster, and losing the signal then must give
 * them what it gives any OSM user (-519: every failure has a fallback).
 *
 * **Drawn through the offline branch** ([mapStyleSourceFor] with `useOfflineTiles`), the style the downloaded regions
 * hold. Not Trail Map's online style: with no signal that load can fail, and an online failure of Trail Map's style is
 * exactly what `OwnStreetFallback` falls back on, which would swap the map to OpenStreetMap for the rest of the
 * process. The fallback never fires in offline mode, so its rules stay as they are, and nothing asks OpenStreetMap's
 * tile host for anything (its tiles are never stored, -1554).
 *
 * **Only when a downloaded area exists** ([hasDownloadedArea]; dispatch 2026-10-23-07, Elder's item 3; the owner: "Only
 * switch to Trail Map when a downloaded area exists", and for the user with none, "Keep their map as it was"). With
 * nothing downloaded the offline branch has no style to load and the map went blank (Amendment 2's stated limit, now
 * closed); the chosen map stays instead, showing whatever MapLibre has cached. "Exists" means any downloaded area on
 * the phone, not one covering the view: the owner's words, and a rule that cannot change while the user pans. A user
 * whose only areas predate Trail Map sees them in the older offline style. "Use offline tiles" ([useOfflineTiles]) is
 * the user's own setting and is not gated: it draws from the downloads as it always has.
 */
internal fun drawsFromDownloads(
    useOfflineTiles: Boolean,
    basemap: Basemap,
    noSignal: Boolean,
    hasDownloadedArea: Boolean,
    trailMapDraws: Boolean = true,
): Boolean = useOfflineTiles || (noSignal && hasDownloadedArea && (basemap != Basemap.FORAGER_TRAIL || !trailMapDraws))

/**
 * Whether the phone holds a downloaded offline area the map can draw from: a region whose tiles are here. A region
 * restored from a backup with no tiles on this phone (`isDownloaded` false, "Not downloaded" in Offline maps) has
 * nothing to draw, so it does not count. A region waiting out its delete's undo still has its tiles, so it does.
 */
internal fun hasDownloadedArea(regions: List<OfflineRegionSummary>): Boolean = regions.any { it.isDownloaded }

/**
 * The "no signal" state the maps read, after the hysteresis ([noSignalWithHysteresis]). `null` where nothing provides
 * it, which every test that composes a map without `MainActivity` relies on: no monitor means the chosen map is always
 * drawn, as before this change. `MainActivity` provides the real one ([ForagerThemeWithNoSignal]).
 */
internal val LocalNoSignal = staticCompositionLocalOf<StateFlow<Boolean>?> { null }

/**
 * Whether a downloaded offline area exists ([hasDownloadedArea]), as the maps and the "Map type" chips read it. `false`
 * where nothing provides it: with no known area the chosen map is drawn, which is the safe side (the map it would
 * replace is at least cached). `MainActivity` provides it from the Offline maps list ([ForagerThemeWithNoSignal]).
 */
internal val LocalHasDownloadedArea = compositionLocalOf { false }

/**
 * [drawsFromDownloads] for this composition: [LocalNoSignal]'s current value, `false` when none is provided, and
 * whether Trail Map draws in this process ([fallback], the app's one `OWN_STREET_FALLBACK`; a parameter so a test can
 * give it its own).
 */
@Composable
internal fun rememberDrawsFromDownloads(useOfflineTiles: Boolean, basemap: Basemap, fallback: OwnStreetFallback = OWN_STREET_FALLBACK): Boolean {
    val flow = LocalNoSignal.current ?: return useOfflineTiles
    val noSignal by flow.collectAsState()
    return drawsFromDownloads(useOfflineTiles, basemap, noSignal, LocalHasDownloadedArea.current, trailMapDraws = fallback.ownStreet())
}

/**
 * The app's real "no signal" state: [AndroidNetworkSignal] through [noSignalWithHysteresis], kept for [scope]'s life.
 * Each change is logged (CLAUDE.md: a fallback is logged when it fires).
 */
internal fun noSignalState(context: Context, scope: CoroutineScope): StateFlow<Boolean> =
    noSignalWithHysteresis(AndroidNetworkSignal(context).validated)
        .onEach { noSignal ->
            Log.i(
                NO_SIGNAL_LOG_TAG,
                if (noSignal) {
                    "No validated network (${NO_SIGNAL_SWITCH_DELAY_MS / 1000} s): OSM and Topo maps draw Forager Trail Map from the downloaded areas."
                } else {
                    "Validated network (${SIGNAL_RETURN_SWITCH_DELAY_MS / 1000} s, or at start): maps draw the chosen map type."
                },
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

/**
 * [ForagerTheme] with [noSignal] provided as [LocalNoSignal], and whether [offlineRegions] hold a downloaded area as
 * [LocalHasDownloadedArea], to everything inside it; `MainActivity`'s root.
 */
@Composable
internal fun ForagerThemeWithNoSignal(
    darkTheme: Boolean,
    noSignal: StateFlow<Boolean>,
    offlineRegions: List<OfflineRegionSummary>,
    content: @Composable () -> Unit,
) {
    ForagerTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(
            LocalNoSignal provides noSignal,
            LocalHasDownloadedArea provides hasDownloadedArea(offlineRegions),
            content = content,
        )
    }
}

internal const val NO_SIGNAL_LOG_TAG = "NoSignalTrailMap"

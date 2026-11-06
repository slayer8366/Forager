package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s four suspend
 * readers for the Journal's entry report and cards, grouped into one bundle as `CompactMapTabInputs.kt`
 * grouped [CompactMapTab]'s (RECORD -739). These were four separate parameters of the screen; each field
 * keeps that parameter's default and doc comment, and the screen unpacks the bundle into locals with the
 * old names at the top of its body, so the body reads as it did. Public because [AvailabilityScreen] is
 * public.
 */

/** What the Journal's entry report and cards read: map data, offline region, saved track paths, current location, for [AvailabilityScreen]. Every field is defaulted, as the parameters were. */
data class AvailabilityScreenEntryReportData(
    /**
     * [com.zynergylabs.forager.app.ui.log.CartographyEntryReportScreen]'s own map, Stage 2d — see that
     * composable's doc comment. Defaulted to always report nothing resolved, same reasoning as
     * `logUiState`: the many existing tests of this screen that never open a Cartography entry
     * don't need to pass a real resolver just to compile.
     */
    val getCartographyEntryMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData = { _, _ ->
        CartographyEntryMapData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    },
    /**
     * [com.zynergylabs.forager.app.ui.log.CartographyEntryReportScreen]'s own offline-map toggle, Stage 2e-i —
     * see that composable's doc comment. Defaulted for the same reason [getCartographyEntryMapData]
     * is: the many existing tests of this screen that never open a Cartography entry don't need a
     * real resolver just to compile.
     */
    val getCartographyEntryOfflineRegion: suspend (CartographyEntry, List<LatLng>) -> OfflineRegionSummary? = { _, _ -> null },
    /** F3 (owner, "C: list screen loads lazily"): one entry's saved track paths, by track id, for the Journal cards' thumbnails. Defaulted like [getCartographyEntryMapData]. */
    val getSavedTrackPaths: suspend (String) -> Map<String, List<LatLng>> = { emptyMap() },
    /**
     * [com.zynergylabs.forager.app.ui.log.CartographyEntryReportScreen]'s own fullscreen recenter button —
     * fullscreen-maps dispatch, see that composable's own doc comment, "Fullscreen." Defaulted for
     * the same reason [getCartographyEntryMapData] is.
     */
    val getCartographyEntryCurrentLocation: suspend () -> LocationResult = { LocationResult.LocationUnavailable },
)

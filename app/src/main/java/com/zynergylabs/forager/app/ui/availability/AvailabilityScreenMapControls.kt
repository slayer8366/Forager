package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldMove

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s Maps-tab
 * controls, grouped into one bundle as `CompactMapTabInputs.kt` grouped [CompactMapTab]'s (RECORD -739).
 * These were nine separate parameters of the screen; each field keeps that parameter's default and doc
 * comment, and the screen unpacks the bundle into locals with the old names at the top of its body, so
 * the body reads as it did. Public because [AvailabilityScreen] is public.
 */

/** The Maps tab's controls: fullscreen, locate, the Layers sheet and forecast store, basemap and icon cluster, for [AvailabilityScreen]. Every field is defaulted, as the parameters were. */
data class AvailabilityScreenMapControls(
    /**
     * The Maps tab entered or left fullscreen by a user action — persisted across restarts, see
     * [AvailabilityUiState.persistedMapFullscreen]. Called only from the three user-driven
     * mutation sites (the icon bar's toggle, system back, leaving the Maps tab), never from an
     * effect on the flag — see `isMapFullscreen`'s own doc comment for why. Defaulted so callers
     * that don't persist (tests) need not care.
     */
    val onMapFullscreenChanged: (Boolean) -> Unit = {},
    /**
     * The compact map icon stack's GPS/locate-me button. Distinct from the screen's `onUseCurrentLocation` —
     * see [LocateMeStatus]'s doc comment — and, like it, defers the OS permission dialog to the
     * Activity (see `MainActivity`'s `pendingLocationAction`) rather than requesting it here.
     */
    val onLocateMe: () -> Unit = {},
    /**
     * Map layers L0b. [onMapShown] runs every time the Maps tab comes into view (the Maps
     * bottom-nav tab or rail item), which
     * is how the saved records and the forecast availability stay fresh (the dispatch, B2:
     * `AvailabilityViewModel.onMapShown`). The next three are the Layers sheet's changes, and
     * [forecastCellStore] is where the Maps tab's colour fields read their cells (the app's one store,
     * `AppContainer.forecastCellStore`). All defaulted, so no other caller changes.
     */
    val onMapShown: () -> Unit = {},
    val onMapLayerVisibilityChanged: (String, Boolean) -> Unit = { _, _ -> },
    val onMapLayerOpacityChanged: (String, Float) -> Unit = { _, _ -> },
    val onColourFieldMoved: (String, ColourFieldMove) -> Unit = { _, _ -> },
    val forecastCellStore: ForecastCellStore = AbsentForecastCellStore,
    /**
     * Dispatch 2026-09-28-708: the Maps tab's basemap was picked, so the ViewModel can store it
     * ([AvailabilityViewModel.onMapModeSelected]). Defaulted, so no other caller changes.
     */
    val onMapModeSelected: (MapMode) -> Unit = {},
    /**
     * RECORD -711: a drag of the Maps tab's icon cluster ended, so the ViewModel can store its side and
     * height ([AvailabilityViewModel.onMapIconClusterPlacementChanged]). Defaulted, so no other caller changes.
     */
    val onMapIconClusterPlacementChanged: (MapIconClusterPlacement) -> Unit = {},
)

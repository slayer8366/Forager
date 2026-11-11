package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s search and trip callbacks,
 * grouped into one bundle as `CompactMapTabInputs.kt` grouped [CompactMapTab]'s (RECORD -739).
 * These were 16 separate parameters of the screen; each field keeps that parameter's default, or stays required where
 * the parameter was required, and its doc comment, and the screen unpacks the bundle into locals with the old names at
 * the top of its body, so the body reads as it did. Public because [AvailabilityScreen] is public.
 */

/** Search and trip inputs: location, radius, month, the taxon search, recent searches, Clear, the Maps and Seasonal tabs, and planned trips, for [AvailabilityScreen]. Fields without a default were required parameters and stay required; the rest keep their defaults. */
data class AvailabilityScreenSearchInputs(
    val onUseCurrentLocation: () -> Unit,
    val onManualLatChanged: (String) -> Unit,
    val onManualLngChanged: (String) -> Unit,
    val onSearchManualCoordinates: () -> Unit,
    val onRadiusChanged: (Int) -> Unit,
    val onMonthSelected: (Int) -> Unit,
    val onMapTabSelected: () -> Unit,
    val onSeasonalTabSelected: () -> Unit,
    val onTaxonSearchQueryChanged: (String) -> Unit,
    val onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    val onDismissTaxonSuggestions: () -> Unit,
    val onReopenTaxonSuggestions: () -> Unit,
    /** Called when a date and name are confirmed for a trip pin placed via [com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker]. */
    val onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    val onDeletePlannedTrip: (String) -> Unit,
    /**
     * Called when one of the recent searches is tapped; see [RecentSearchesSection]. Reached from
     * [SearchDropdown].
     */
    val onRecentSearchSelected: (CachedSearchSummary) -> Unit,
    /**
     * RECORD -723: the search bar's Clear, shown only while a search is showing (a region is set). MainActivity
     * wires it to [AvailabilityViewModel.clearSearch]. Defaulted to a no-op only so the many screen tests that
     * never show Clear need not pass it.
     */
    val onClearSearch: () -> Unit = {},
    /**
     * Dispatch 2026-11-11-01: the Maps tab's "Try again" on a failed search, and the retry when signal returns. MainActivity wires
     * it to [AvailabilityViewModel.retryFailedSearch]. Defaulted to a no-op only so the many screen tests that never fail a search
     * need not pass it, as [onClearSearch] is.
     */
    val onRetryFailedSearch: () -> Unit = {},
    /**
     * Dispatch 2026-11-11-01, C4 and D1: the phone's raw signal, [com.zynergylabs.forager.app.domain.NetworkSignal.validated], so a
     * failed search runs again when it returns ([RetryFailedSearchOnSignalReturn]). MainActivity hands it the real one; the default
     * never emits, so a screen test that does not drive the signal sees no return.
     */
    val networkValidated: Flow<Boolean> = emptyFlow(),
)

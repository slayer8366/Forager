package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.3, PR 2 (dispatch 2026-10-19-35): moved out of CompactMapTab as a plain function, called at the line where
// the tab worked this out before. Not composable, so composition is unchanged.

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * Dispatch -497: where the start is once the walker has arrived, by the same rule the HUD's
 * "Arrived" reads (arrivedAtStart); null otherwise.
 * RECORD -845: held while the display holds "Arrived" after a return ended on arrival, whatever the fix says.
 * The clock is read only when the rule needs it, as before.
 */
internal fun compactMapArrivedAt(navigation: CompactMapNavigation, uiState: AvailabilityUiState, currentTime: CurrentTimeProvider): LatLng? {
    val isNavigating = navigation.isNavigating
    val navigationTarget = navigation.navigationTarget
    return if (isNavigating && navigationTarget != null && (navigation.isArrivalHeld || arrivedAtStart(uiState.liveFix, navigationTarget, currentTime.nowEpochMillis()))) {
        LatLng(navigationTarget.lat, navigationTarget.lng)
    } else {
        null
    }
}

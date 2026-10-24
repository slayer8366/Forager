package com.zynergylabs.forager.app

import com.zynergylabs.forager.app.ui.log.CartographyViewModel

/**
 * The one place the Journal's [CartographyViewModel] is built, moved unchanged out of `MainActivity`'s ViewModel factory (nav
 * step 2, dispatch 2026-10-21-01), the pattern of [createMushroomLogViewModel].
 */
internal fun createCartographyViewModel(container: AppContainer): CartographyViewModel = CartographyViewModel(
        container.getCartographyEntriesUseCase,
        container.getCartographyDraftEntriesUseCase,
        container.createCartographyEntryUseCase,
        container.saveCartographyEntryUseCase,
        container.getCartographyEntryUseCase,
        container.commitCartographyEntryUseCase,
        container.deleteCartographyEntryUseCase,
        container.getDerivedTripUseCase,
        container.getTripReportOfflineRegionsUseCase,
        container.computeTrackStatisticsUseCase,
        container.setCartographyEntryShownOnMapUseCase,
    )

package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.SeaLevelConverter
import com.zynergylabs.forager.app.domain.SeaLevelHeight
import com.zynergylabs.forager.app.domain.ShownHeight

/**
 * Dispatch 2026-10-28-13: the strip shows [AvailabilityUiState.shownHeight], not the fix's own altitude. For screen tests
 * whose claim is the layout or the words (a four-digit height's width, feet against metres), the shown height is the
 * fix's own altitude at a zero sea-level offset, so they measure what they measured before. The live path itself is
 * tested in [AvailabilityScreenSeaLevelHeightTest].
 */
internal fun AvailabilityUiState.withFixHeight(): AvailabilityUiState = copy(shownHeight = heightOfFix(liveFix))

/** [fix]'s own altitude as the shown height, at a zero offset; see [withFixHeight]. */
internal fun heightOfFix(fix: LocationFix.Update?): ShownHeight? = when {
    fix == null -> null
    fix.altitude == null -> ShownHeight.Unavailable
    else -> ShownHeight.Known(fix.altitude!!, aboveRawMeters = 0.0)
}

/** A converter that puts sea level [offsetMeters] above the raw height, everywhere. */
internal class FixedOffsetSeaLevelConverter(private val offsetMeters: Double) : SeaLevelConverter {
    override suspend fun seaLevel(lat: Double, lng: Double, rawMeters: Double?): SeaLevelHeight =
        if (rawMeters == null) SeaLevelHeight.NoHeight else SeaLevelHeight.Known(rawMeters + offsetMeters, offsetMeters)
}

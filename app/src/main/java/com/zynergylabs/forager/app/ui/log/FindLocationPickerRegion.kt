package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region

/**
 * Where a find's Add/Change Location picker opens — find-location-at-creation dispatch, Fix 3.
 * On the device's position when one is in hand, otherwise on [fallback] (the search region, or
 * `JOURNAL_PICKER_DEFAULT_REGION` when nothing has ever been searched — the picker's behaviour
 * before this fix, unchanged for that case).
 *
 * [FIND_PICKER_DEVICE_RADIUS_KM] sets only the opening zoom (`SightingsMap` derives zoom from a
 * region's radius; the radius is never submitted anywhere): 1 km, the tightest [Region] allows,
 * because a picker opened where the user is standing is for nudging a pin by metres, not for
 * finding a town. The search region keeps its own radius, as before.
 *
 * No age bound on [deviceLocation], deliberately, unlike the fix a find is stamped with at
 * creation: here the position only frames a map the user is about to look at and confirm, so a
 * stale one costs a pan, not a wrong record.
 */
internal fun findLocationPickerRegion(deviceLocation: LatLng?, fallback: Region): Region =
    deviceLocation?.let { Region(lat = it.lat, lng = it.lng, radiusKm = FIND_PICKER_DEVICE_RADIUS_KM) } ?: fallback

internal const val FIND_PICKER_DEVICE_RADIUS_KM = Region.MIN_RADIUS_KM

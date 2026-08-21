package com.forager.app.domain

import java.util.concurrent.TimeUnit

/**
 * Whether a downloaded region is old enough to badge as stale, per the design doc's "Freshness"
 * section: no automatic expiry or deletion (an offline map that deletes itself fails exactly when
 * there's no connectivity to recover it), just a badge past [thresholdDays] so the user knows to
 * consider refreshing it. A pure function of [createdAtEpochMillis] and [nowEpochMillis] — callers
 * supply "now" (see [CurrentTimeProvider]) rather than this reading the system clock directly, so
 * the threshold is exercisable headlessly with an injected time.
 */
fun isOfflineRegionStale(
    createdAtEpochMillis: Long,
    nowEpochMillis: Long,
    thresholdDays: Int,
): Boolean {
    val ageMillis = nowEpochMillis - createdAtEpochMillis
    return ageMillis >= TimeUnit.DAYS.toMillis(thresholdDays.toLong())
}

/**
 * The default staleness threshold, "roughly 60 days" per the design doc — "make the interval a
 * setting" is [MapPreferencesRepository.getStaleThresholdDays], not a hardcoded constant read
 * everywhere; this is only that setting's initial value.
 */
const val DEFAULT_STALE_THRESHOLD_DAYS = 60

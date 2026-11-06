package com.zynergylabs.forager.app.domain

/**
 * Where a live fix came from, as the platform reported it when the fix arrived: dispatch 2026-09-28-527
 * (fix-provider), under RECORD -519. The owner: "removing reliance on network data, but not total removal
 * of collection at all."
 *
 * **Only [GPS] may act on position.** A [NETWORK] or [UNKNOWN] fix may be shown, as -510's approximate
 * position, and never decides anything: not the gated live fix, not "Arrived", not the waypoint's line, not
 * a find's location, not a recording's origin or end waypoint, not the route home, not the sundown watch's
 * walk back, not the off-track judge. [UNKNOWN] is never a guess at one of the other two: a `null`
 * provider, "fused", "passive", anything else. No such provider has been seen on the S22 (the report,
 * item 3), and this app only asks for GPS and network live, so [UNKNOWN] is the honest answer to a case
 * that has not happened yet rather than a fallback that fires.
 *
 * Carried on [LocationFix.Update] from the tracker, and beside a [com.zynergylabs.forager.app.domain.model.TrackPoint]
 * where the service hands a live fix on; **never stored**. Where there is no provider, on stored points
 * and anything built from them, the timestamp rule ([isNetworkProviderFix]) stays the test.
 */
enum class FixProvider {
    GPS,
    NETWORK,
    UNKNOWN,
}

/** `true` only for [FixProvider.GPS]: see [FixProvider]. */
val FixProvider.mayAct: Boolean get() = this == FixProvider.GPS

/**
 * `true` when the provider and the timestamp rule ([isNetworkProviderTimestamp]) give different answers for
 * one live fix: GPS stamped with milliseconds, or network on the whole second. [FixProvider.UNKNOWN] has no
 * answer to disagree with. On the S22 this happened 4 times in 1,969 walk fixes and never at the desk (the
 * report, item 5); logged so a device where the rule fails becomes visible.
 */
fun FixProvider.disagreesWithTimestampRule(timestampEpochMillis: Long): Boolean = when (this) {
    FixProvider.GPS -> isNetworkProviderTimestamp(timestampEpochMillis)
    FixProvider.NETWORK -> !isNetworkProviderTimestamp(timestampEpochMillis)
    FixProvider.UNKNOWN -> false
}

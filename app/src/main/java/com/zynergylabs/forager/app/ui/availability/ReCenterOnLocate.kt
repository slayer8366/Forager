package com.zynergylabs.forager.app.ui.availability

/**
 * Dispatch 2026-11-06-04, Amendment 2 (RECORD -2186). The owner: "Only have the ring in landscape when needing to re-center
 * the map camera during navigation, because re-center seems to struggle fitting there and the circle will be an answer to
 * that. But only in that situation would it be useful, every other situation it doesn't have a ring", and "Ring replaces the
 * pill" (the option read: "In landscape navigation the pill doesn't show; the ringed locate icon is the re-center button.
 * Portrait keeps the pill as now").
 *
 * Whether the Maps tab's locate row carries its ring and stands in for Re-center: in [landscape] (the short landscape window,
 * `MapIconClusterState.landscape`, the same flag Re-center's place beside the L (D17) is decided by, fullscreen or not), while
 * [isNavigating], once the camera has left the navigation follow ([navigationFollowing] false), which is exactly when the
 * pill would show. Its tap needs no change: while navigating the locate row already calls Re-center's own
 * `onReturnToRoute` (Ruling D, [CompactMapIconCluster]).
 */
internal fun locateRowStandsInForReCenter(landscape: Boolean, isNavigating: Boolean, navigationFollowing: Boolean): Boolean =
    landscape && isNavigating && !navigationFollowing

/** Whether the Re-center pill shows: while navigating once the camera has left the follow, except where the ringed locate row replaces it. */
internal fun reCenterPillShown(landscape: Boolean, isNavigating: Boolean, navigationFollowing: Boolean): Boolean =
    isNavigating && !navigationFollowing && !locateRowStandsInForReCenter(landscape, isNavigating, navigationFollowing)

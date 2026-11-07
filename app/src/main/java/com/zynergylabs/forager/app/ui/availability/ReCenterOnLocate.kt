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
internal fun locateRowStandsInForReCenter(landscape: Boolean, isNavigating: Boolean, navigationFollowing: Boolean, wordFitsAtBottomCentre: Boolean): Boolean =
    landscape && isNavigating && !navigationFollowing && !wordFitsAtBottomCentre

/** Whether the Re-center pill shows: while navigating once the camera has left the follow, except where the ringed locate row replaces it. */
internal fun reCenterPillShown(landscape: Boolean, isNavigating: Boolean, navigationFollowing: Boolean, wordFitsAtBottomCentre: Boolean): Boolean =
    isNavigating && !navigationFollowing && !locateRowStandsInForReCenter(landscape, isNavigating, navigationFollowing, wordFitsAtBottomCentre)

/**
 * Dispatch 2026-11-06-04, Amendment 3 (RECORD -2186). The owner: "Tablets keep the pill", then, asked what decides it, "(b) Pill
 * where it fits" (the option: "A real fit test: where the Re-center word fits at bottom centre without covering a card, the pill
 * shows there and there's no ring; where it doesn't fit, the ring"). Whether Re-center with its word, at its own place at bottom
 * centre ([word], D34's `reCenterWordRectInRoot`), overlaps none of [cards] (the route card and the navigation card in landscape
 * navigation), whether or not the route card is open. Not yet measured ([word] null: the word or the navigation card not laid out
 * yet) counts as not fitting, so the ring of Amendment 2 holds until the measurement says otherwise.
 */
internal fun reCenterWordFitsAtBottomCentre(word: androidx.compose.ui.geometry.Rect?, cards: List<androidx.compose.ui.geometry.Rect>): Boolean =
    word != null && !reCenterWordCoversCard(word, cards)

/**
 * Amendment 3: whether the shown pill goes beside the L. Where its word fits at bottom centre ([wordFits]) it stays there, D17's
 * place beside the L for a route card on the navigation card ([d17]) included; elsewhere D17 or D34 ([wordCoversCard]) as before,
 * which in landscape navigation now only applies to a pill that does not show (the ringed locate row stands in for it).
 */
internal fun reCenterGoesBesideTheL(d17: Boolean, wordCoversCard: Boolean, wordFits: Boolean): Boolean = !wordFits && (d17 || wordCoversCard)

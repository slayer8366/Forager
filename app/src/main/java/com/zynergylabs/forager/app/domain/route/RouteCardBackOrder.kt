package com.zynergylabs.forager.app.domain.route

/*
 * Dispatch 2026-11-06-05, Amendment 3 (RECORD -2187): Back after the route card's Navigate in fullscreen. The owner: "Only leave full
 * screen if they entered full screen after tapping navigate. If they entered navigation mode after full screen mode, then leave
 * navigate." Restated and confirmed ("Yes, that's it"): Back undoes whichever came last.
 *   card → Navigate → fullscreen: Back leaves fullscreen, then ends navigation, then closes the card.
 *   fullscreen → card → Navigate: Back ends navigation, then leaves fullscreen, then closes the card.
 * Navigate does not turn fullscreen on, so the order is read from what was on when Navigate was tapped. Pure Kotlin, no UI.
 */

/** What one Back does on the route card's tier while its saved route shows. */
enum class RouteCardBackStep { LEAVE_FULLSCREEN, END_NAVIGATION, CLOSE_CARD }

/**
 * The order the card's Navigate and fullscreen were entered in. [navigatedInFullscreen]: fullscreen was on when Navigate was tapped,
 * and has stayed on since, so Navigate came last. [leaveFullscreenNext]: Back ended that navigation in fullscreen, so the next Back
 * leaves fullscreen before the card closes (the owner's second order, Back 2). Leaving fullscreen by any route clears both, so
 * fullscreen entered again after Navigate counts as the later of the two.
 */
data class RouteCardBackOrder(val navigatedInFullscreen: Boolean = false, val leaveFullscreenNext: Boolean = false) {

    /** The card's Navigate tapped, with fullscreen [fullscreen] at that moment. */
    fun navigated(fullscreen: Boolean): RouteCardBackOrder = RouteCardBackOrder(navigatedInFullscreen = fullscreen)

    /** Fullscreen was left, by Back or by its control: neither order is pending any more. */
    fun fullscreenLeft(): RouteCardBackOrder = RouteCardBackOrder()

    /** Back ended the card's navigation while [fullscreen]; where Navigate came after fullscreen, fullscreen goes next. */
    fun navigationEndedByBack(fullscreen: Boolean): RouteCardBackOrder =
        RouteCardBackOrder(leaveFullscreenNext = fullscreen && navigatedInFullscreen)

    /**
     * What Back does now, with fullscreen [fullscreen] and the card's own navigation running or not ([navigatingFromCard]). With
     * neither pending this is [RouteCardBackStep.CLOSE_CARD], the card's Back as before (-1852 "cancel first" in fullscreen too).
     */
    fun step(fullscreen: Boolean, navigatingFromCard: Boolean): RouteCardBackStep = when {
        navigatingFromCard && fullscreen && !navigatedInFullscreen -> RouteCardBackStep.LEAVE_FULLSCREEN
        navigatingFromCard -> RouteCardBackStep.END_NAVIGATION
        fullscreen && leaveFullscreenNext -> RouteCardBackStep.LEAVE_FULLSCREEN
        else -> RouteCardBackStep.CLOSE_CARD
    }
}

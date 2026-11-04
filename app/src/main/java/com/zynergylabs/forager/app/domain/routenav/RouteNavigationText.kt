package com.zynergylabs.forager.app.domain.routenav

/*
 * Route navigation's words (dispatch 2026-11-03-03), the owner's, A9 (records-after-173 07ec311e3), which replaced the placeholders
 * named in the verify-first. Pinned by RouteNavigationTextTest. In the domain, not the UI, because the background service's
 * notification says them too.
 */

/** The next-stop line, on its own line under the distance (A9): "To <stop> · <i> of <n>", counting from 1. */
fun routeNextStopText(name: String, index: Int, stopCount: Int): String = "To $name · ${index + 1} of $stopCount"

/** Shown for [ROUTE_REACHED_SHOW_MILLIS] when a stop is reached (A9). */
fun routeReachedText(name: String): String = "Reached $name"

/** How long "Reached <stop>" shows (A9: "for 5 s"). */
const val ROUTE_REACHED_SHOW_MILLIS = 5_000L

/** The navigation was stopped because its route was deleted (A9), as the waypoint's is worded. */
const val ROUTE_NAVIGATION_GONE_TEXT = "Stopped navigating: that route was deleted."

/** The navigation card's route button, for TalkBack (A9): what a tap does. */
const val ROUTE_CARD_SHOW_DESCRIPTION = "Show route"
const val ROUTE_CARD_HIDE_DESCRIPTION = "Hide route"

/** The route card's button while a navigation of it is on (A7: "Navigate reads 'Stop navigating'"). */
const val ROUTE_CARD_STOP_NAVIGATING_TEXT = "Stop navigating"

/** The background service's notification (A9). Its text is [routeNextStopText]; after the last stop, [ROUTE_NAVIGATION_NOTIFICATION_ARRIVED]. */
const val ROUTE_NAVIGATION_CHANNEL_NAME = "Route navigation"
fun routeNavigationNotificationTitle(tripName: String): String = "Navigating $tripName"
const val ROUTE_NAVIGATION_NOTIFICATION_STOP = "Stop navigating"
const val ROUTE_NAVIGATION_NOTIFICATION_ARRIVED = "Arrived"

package com.zynergylabs.forager.app.ui.map.fanout

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Back for a crowded stack (dispatch 2026-10-27-07, the owner's step paths; "Back retraces the way in"):
 *
 *  - **The fan opened by the zoom:** Back folds it. That is the fan's own handler ([MarkerFanOutBackHandler]); these wait
 *    while it is open.
 *  - **The list:** a row's bubble first (Back > the list), then the list ("Back > the list closes").
 *  - **Then the camera:** "Back again > the map returns to where it was before the tap", one zoom at a time. Then the Maps
 *    tab's usual Back.
 *
 * Both are composed **only while they are the step Back is for**, as the fan's is, and for the same reason: a `BackHandler`
 * composed later is asked first, so one composed when its step comes sits above every handler already on the screen (a
 * bubble's own is always composed). Not composed while a bubble shows ([bubbleShown]), while the fan is open, or while
 * something over the map owns Back ([backEnabled] false: the Tools drawer, fullscreen, the add menu, the pin pickers, the
 * search dropdown), so those close first, as they do for the fan (dispatch 2026-09-28-293, -298).
 */
@Composable
fun CrowdedStackBackHandlers(host: CrowdedStackHost, bubbleShown: Boolean, backEnabled: Boolean) {
    val fanOpen = host.fan?.isOpen == true
    if (!backEnabled || bubbleShown || fanOpen) return
    if (host.list != null) {
        BackHandler { }
    } else if (host.cameraReturnPending) {
        BackHandler { host.stepBack() }
    }
}

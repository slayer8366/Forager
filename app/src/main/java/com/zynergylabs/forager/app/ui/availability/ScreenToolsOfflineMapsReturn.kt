package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.zynergylabs.forager.app.ui.log.JournalTopTab
import com.zynergylabs.forager.app.ui.log.RecordsSubTab

// Dispatch 2026-10-26-17 (RECORD -1682), a pure move: where the Tools drawer's "Offline maps" row was tapped, and the effect
// that forgets it once the user moves on, moved out of AvailabilityScreen's body. Each is called in the body at the line where
// it was written, so the two remembers and the effect keep their order and keys (-1484). The row itself is built in
// AvailabilityScreenJournalReturn.kt. The comments moved with the code; "above" in them means the screen's body.

/**
 * The two States [rememberToolsOfflineMapsReturn] remembers, read and written through the originals, never copies of them
 * (Stage G). Built afresh on each composition and not remembered, so it adds no slot.
 */
internal class ScreenToolsOfflineMapsReturn(
    private val toolsReturnState: MutableState<ToolsOfflineMapsReturn?>,
    private val toolsReturnArrivedState: MutableState<Boolean>,
) {
    var toolsReturn: ToolsOfflineMapsReturn?
        get() = toolsReturnState.value
        set(value) { toolsReturnState.value = value }
    var toolsReturnArrived: Boolean
        get() = toolsReturnArrivedState.value
        set(value) { toolsReturnArrivedState.value = value }
}

@Composable
internal fun rememberToolsOfflineMapsReturn(): ScreenToolsOfflineMapsReturn {
    // RECORD -868 and -871: the Tools drawer's "Offline maps" row. It closes Tools and opens Journal > Records on the
    // Offline maps chip ("Records, on the Offline maps chip (Recommended)"), through the same one-shot request the GPX
    // import above uses, and ends fullscreen for the same reason. It remembers where it was tapped, [toolsReturn], so Back
    // can retrace the way in (the owner: "Reopen Tools, then Seasonal (Recommended)"): the first Back puts the page the
    // user came from back under the drawer and reopens the drawer; the second is the drawer's own Back, which closes it on
    // that page. Once the user moves on inside the Journal, by another chip or tab, the memory is forgotten and Back works
    // as it always has from wherever they are. A second tap on the row while the memory stands keeps the first origin, so
    // Back never returns to Records itself.
    val toolsReturnState = remember { mutableStateOf<ToolsOfflineMapsReturn?>(null) }
    // Whether Records has reached the Offline maps chip since the row was tapped: until it has, the request is still on its
    // way (JournalTab consumes it, then RecordsTab selects the chip, a frame or two later), and not being there yet is not
    // the user moving on.
    val toolsReturnArrivedState = remember { mutableStateOf(false) }
    return ScreenToolsOfflineMapsReturn(toolsReturnState, toolsReturnArrivedState)
}

/**
 * The effect that marks the row's request as arrived on the Offline maps chip and then forgets the origin once the user moves
 * on. [toolsReturnSet] is the body's `toolsReturn != null`, read in the body as the effect's key was; the tab and the Journal's
 * places are read inside the flow through [tabSelections], as the body's flow read them; the effect's block is the body's,
 * unchanged.
 */
@Composable
internal fun ToolsOfflineMapsReturnWatch(
    toolsReturnSet: Boolean,
    toolsOfflineMapsReturn: ScreenToolsOfflineMapsReturn,
    tabSelections: ScreenTabSelections,
) {
    var toolsReturn by toolsOfflineMapsReturn::toolsReturn
    var toolsReturnArrived by toolsOfflineMapsReturn::toolsReturnArrived
    val compactTab by tabSelections::compactTab
    val journalScreenState = tabSelections.journalScreenState
    LaunchedEffect(toolsReturnSet) {
        if (toolsReturn == null) return@LaunchedEffect
        snapshotFlow { Triple(compactTab, journalScreenState.topTab, journalScreenState.recordsFilter) }.collect { (tab, top, filter) ->
            val onOfflineMaps = tab == CompactTab.JOURNAL && top == JournalTopTab.RECORDS && filter == RecordsSubTab.OFFLINE_MAPS
            if (onOfflineMaps) {
                toolsReturnArrived = true
            } else if (toolsReturnArrived) {
                toolsReturn = null
            }
        }
    }
}

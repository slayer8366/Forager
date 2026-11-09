package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.mapdata.AreaDetail
import com.zynergylabs.forager.app.domain.mapdata.TrailMapDetail
import com.zynergylabs.forager.app.domain.mapdata.TrailMapDetailLimits
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Offline Maps picker's detail check (known bug 14, dispatch 2026-11-02-02, A3 to A9): asks [trailMapDetail] whether the
 * Trail Map has its detail across the area the pin and radius describe, and writes the answer to
 * [AvailabilityUiState.offlineDetail] for that area only. Built inside [AvailabilityOfflineMaps] (call S7, RECORD -2150), so
 * `AvailabilityViewModel.kt` stays under 800 lines. **Whether to ask is decided by the ViewModel**
 * (`onOfflineAreaSettled`, `onOfflineMapsSignalReturned`); this runs it.
 *
 * - **The wait and the latest area:** a check starts [TrailMapDetailLimits.SETTLE_DEBOUNCE_MILLIS] after its area settles, and
 *   a newer area cancels it, waiting or running ("latest wins", RECORD -2150).
 * - **Answers already known** ([TrailMapDetail.known]) are written at once, with no wait: they cost no request, and waiting
 *   would make the line blink off and on with each pan inside the same tiles, which the session cache exists to stop.
 * - **No answer** (no signal, a failed or late answer): no line, and one log line saying why (A6; the owner, RECORD -2144:
 *   "With no signal it can't ask, so no warning, logged"). An earlier answer for the same area is cleared, so nothing stands
 *   in for the answer that did not come.
 */
internal class AvailabilityOfflineDetail(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val trailMapDetail: TrailMapDetail,
    private val errorLog: ErrorLog,
) {
    /** The last area that settled: what signal coming back checks again. */
    var lastSettled: Region? = null
        private set

    private var running: Job? = null

    fun ask(area: Region) {
        lastSettled = area
        running?.cancel()
        val box = routeAreaFor(area)
        trailMapDetail.known(box)?.let { answer ->
            show(area, answer)
            return
        }
        running = scope().launch {
            delay(TrailMapDetailLimits.SETTLE_DEBOUNCE_MILLIS)
            val answer = withTimeoutOrNull(TrailMapDetailLimits.CHECK_TIMEOUT_MILLIS) { trailMapDetail.check(box) }
                ?: AreaDetail.NotRun("no answer within ${TrailMapDetailLimits.CHECK_TIMEOUT_MILLIS / 1_000} s")
            show(area, answer)
        }
    }

    /** The area settled with no signal: it is not asked. */
    fun noteNoSignal(area: Region) {
        lastSettled = area
        running?.cancel()
        show(area, AreaDetail.NotRun("no signal"))
    }

    private fun show(area: Region, answer: AreaDetail) {
        when (answer) {
            AreaDetail.SomeOutside -> updateState { it.copy(offlineDetail = OfflineDetailAnswer(area, someOutside = true)) }
            AreaDetail.AllInside -> updateState { it.copy(offlineDetail = OfflineDetailAnswer(area, someOutside = false)) }
            is AreaDetail.NotRun -> {
                updateState { state -> if (state.offlineDetail?.area == area) state.copy(offlineDetail = null) else state }
                errorLog.w(TAG, "Offline maps detail check not run: ${answer.reason}.", OfflineDetailCheckNotRun(answer.reason))
            }
        }
    }
}

/**
 * The map server's answer for one area of the picker (known bug 14): [someOutside] when some of a download of [area] would be
 * blank. The picker shows the line only while [area] is the area on screen, so an answer for an earlier area never shows.
 */
data class OfflineDetailAnswer(val area: Region, val someOutside: Boolean)

/** What [ErrorLog] is handed for a check that did not run: the reason, as the log line's throwable (CLAUDE.md: never silent). */
private class OfflineDetailCheckNotRun(reason: String) : Exception(reason)

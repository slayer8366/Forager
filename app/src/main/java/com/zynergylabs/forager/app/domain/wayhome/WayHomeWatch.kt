package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HOP_FAR_ENTER_ABOVE_METERS
import com.zynergylabs.forager.app.domain.HOP_FAR_LEAVE_BELOW_METERS
import com.zynergylabs.forager.app.domain.LIVE_FIX_MAX_ACCURACY_METERS
import com.zynergylabs.forager.app.domain.mayAct
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What [WayHomeWatch] shows for [trackId]: the way home of [generation], or `null` [result] while the first one of a
 * return is still being worked out (the track is shown meanwhile, "as today"). [line] is [result]'s line when it is
 * along trails.
 */
data class WayHomeShown(val trackId: String, val generation: Long, val result: WayHomeResult?, val line: WayHomeLine? = null)

/**
 * The way home along trails for the recording being returned on (dispatch 2026-10-24-10 Amendment 1, PR B; RECORD
 * -1613): worked out at Return, held for the life of the process, re-routed once when the walker strays, and read by
 * the HUD and by sundown, so both use the route shown (RECORD -1589, "app-wide").
 *
 * ## Why it lives here (RECORD -1613, the owner: "Yes, keep working (Recommended)")
 *
 * Held by `AppContainer`, as `ReturnWatch` and `SundownWatch` are, because the return and the sundown alerts outlive
 * the Activity. A second Return, Stop and a waypoint pause cancel its work; the Activity going away does not.
 *
 * ## Who calls what
 *
 * - **The recording ViewModel** calls [find] from its Return ([find] is its decision, RECORD -1450) and [stop] when the
 *   return or the recording ends.
 * - **The recording service** calls [onFix] for every raw fix, and [end] when it stops or is destroyed, as it does for
 *   the other watches.
 * - **Readers:** the ViewModel collects [shown]; the HUD's tick and sundown read [progressFor].
 *
 * ## The job
 *
 * At most one. [find] cancels the one in flight (`Job.cancel`, which does not wait) and starts the next under a new
 * [WayHomeShown.generation]; a job publishes only if its generation is still current, so a cancelled one never shows.
 * A cancelled routing call waits, off every caller's thread, for the engine to stop before it gives up the one-route
 * lock (`BRouterTrailRouter`; Sorrel's L-389-3), so the next job's first engine call can wait that long. Silent.
 *
 * ## Straying (RECORD -1589, "Once, plus if you stray"; the stray rules, RECORD -1613)
 *
 * A GPS fix within the live-fix gate more than [WAY_HOME_STRAY_METERS] from the line starts one re-route on the track
 * as now stored. Three things stop a loop: one job at a time; once per stray (the next stray counts only after a fix
 * within [WAY_HOME_STRAY_REARM_METERS] of the line shown); and a re-route that falls back to the track ends re-routing
 * for the return. A stray that starts nothing is logged once.
 *
 * ## Threads
 *
 * [onFix] comes from the service's collector, [find] and [stop] from the main thread, the job's end from [scope].
 * Everything is read and written under [lock]; [shown] is a `StateFlow`, safe from any thread.
 */
class WayHomeWatch(
    private val scope: CoroutineScope,
    /** The track through the read seam (`TrackRepository.getById`), as `SundownWatch` reads it. */
    private val readTrack: suspend (String) -> Result<Track?>,
    /** [FindWayHomeUseCase] in production. */
    private val findWayHome: suspend (Track, Waypoint?) -> WayHomeResult,
    /** Whether the walker is returning on this track now (`ReturnWatch`'s state); arrival ends it. */
    private val isReturning: (String) -> Boolean,
    private val errorLog: ErrorLog,
) {
    private val lock = Any()

    private val _shown = MutableStateFlow<WayHomeShown?>(null)
    val shown: StateFlow<WayHomeShown?> = _shown.asStateFlow()

    // Read and written only while holding [lock].
    private var trackId: String? = null
    private var origin: Waypoint? = null
    private var generation = 0L
    private var job: Job? = null
    private var progress: WayHomeProgress? = null
    private var armed = true
    private var reroutingEnded = false
    private var loggedWhy: String? = null

    /**
     * The walker tapped Return on [trackId], with [origin] its start marker: the track is shown, and the way home is
     * worked out. Cancels anything in flight, for this or any track.
     */
    fun find(trackId: String, origin: Waypoint?) = synchronized(lock) {
        this.trackId = trackId
        this.origin = origin
        reroutingEnded = false
        resetProgress()
        launchJob(trackId, origin, firstOfReturn = true)
    }

    /** The return or the recording on [trackId] has ended: the work is cancelled and nothing is shown. Ignored for another track. */
    fun stop(trackId: String) = synchronized(lock) {
        if (trackId != this.trackId) return@synchronized
        forget()
    }

    /** The service stopped [trackId], or, with `null`, was destroyed: as [stop]. */
    fun end(trackId: String?) = synchronized(lock) {
        if (trackId != null && trackId != this.trackId) return@synchronized
        forget()
    }

    /** Where the walker is on the way home shown for [trackId], or `null` when none is shown, they are not returning, or no fix has placed them yet. */
    fun progressFor(trackId: String): WayHomeProgress? = synchronized(lock) {
        if (trackId != this.trackId || !isReturning(trackId)) return@synchronized null
        progress
    }

    /** One raw fix from the service: the walker's place on the line, and the stray judge. See the class doc. */
    fun onFix(point: TrackPoint, provider: FixProvider) = synchronized(lock) {
        val id = trackId ?: return@synchronized
        val shownNow = _shown.value ?: return@synchronized
        val line = shownNow.line ?: return@synchronized
        if (!isReturning(id) || !provider.mayAct) return@synchronized
        if ((point.accuracyMeters ?: 0f) > LIVE_FIX_MAX_ACCURACY_METERS) return@synchronized
        val current = LatLng(point.lat, point.lng)
        val previous = (progress as? WayHomeProgress.Placed)?.place
        val next = placeOnWayHome(line, current, previous)
        if (next is WayHomeProgress.OffLine && progress !is WayHomeProgress.OffLine) {
            errorLog.w(TAG, "The walker is over the far band from the way home; sundown reads the track until they are back on it.", WayHomeFallback("off the line"))
        }
        progress = next
        judgeStray(id, line.metersFrom(current))
    }

    /** Call with [lock] held. */
    private fun judgeStray(id: String, metersFromLine: Double) {
        if (metersFromLine < WAY_HOME_STRAY_REARM_METERS) {
            armed = true
            loggedWhy = null
            return
        }
        if (metersFromLine <= WAY_HOME_STRAY_METERS) return
        val why = when {
            reroutingEnded -> "a re-route this return fell back to the track"
            job != null -> "a way home is already being worked out"
            !armed -> "this stray has already re-routed once"
            else -> null
        }
        if (why != null) {
            if (why != loggedWhy) errorLog.w(TAG, "The walker strayed from the way home and it was not re-routed: $why.", WayHomeFallback(why))
            loggedWhy = why
            return
        }
        armed = false
        launchJob(id, origin, firstOfReturn = false)
    }

    /** Call with [lock] held. */
    private fun launchJob(id: String, origin: Waypoint?, firstOfReturn: Boolean) {
        job?.cancel()
        val mine = ++generation
        if (firstOfReturn) _shown.value = WayHomeShown(id, mine, null)
        job = scope.launch {
            val result = try {
                work(id, origin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorLog.w(TAG, "Working out the way home failed; the track is the way home.", e)
                WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND)
            }
            synchronized(lock) {
                if (trackId != id) return@synchronized
                job = null
                val line = (result as? WayHomeResult.AlongTrails)?.let { WayHomeLine(it.wayHome) }
                if (!firstOfReturn && line == null) reroutingEnded = true
                // A new line places the walker afresh. After a re-route the stray stays spent until a fix comes back
                // within the re-arm figure of the line shown: once per stray.
                progress = null
                _shown.value = WayHomeShown(id, mine, result, line)
            }
        }
    }

    private suspend fun work(id: String, origin: Waypoint?): WayHomeResult {
        val track = readTrack(id).getOrElse { error ->
            errorLog.w(TAG, "Couldn't read track '$id' for the way home; the track is the way home.", error)
            return WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND)
        } ?: run {
            errorLog.w(TAG, "Track '$id' was not found for the way home; the track is the way home.", WayHomeFallback("no track"))
            return WayHomeResult.TrackOnly(null)
        }
        return findWayHome(track, origin)
    }

    /** Call with [lock] held. */
    private fun resetProgress() {
        progress = null
        armed = true
        loggedWhy = null
    }

    /** Call with [lock] held. */
    private fun forget() {
        job?.cancel()
        job = null
        generation++
        trackId = null
        origin = null
        reroutingEnded = false
        resetProgress()
        _shown.value = null
    }

    private class WayHomeFallback(reason: String) : Exception(reason)

    private companion object {
        const val TAG = "WayHomeWatch"
    }
}

/**
 * How far from the way home shown a walker has strayed before it re-routes, once (RECORD -1589, "more than 50 m from
 * the line"; RECORD -1613). The hop's far band ([HOP_FAR_ENTER_ABOVE_METERS]), the figure at which the HUD withholds
 * the route too. **Provisional.**
 */
const val WAY_HOME_STRAY_METERS = HOP_FAR_ENTER_ABOVE_METERS

/** Back within this of the line shown, the next stray may re-route again (RECORD -1613; the far band's leave figure). **Provisional.** */
const val WAY_HOME_STRAY_REARM_METERS = HOP_FAR_LEAVE_BELOW_METERS

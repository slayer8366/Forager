package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.UUID

/**
 * RECORD -2192 (the owner: "Fix: remember framing (Recommended)", "After the restart the map carries on following you; nothing
 * else changes"): the Maps tab's [MapCameraMemory], kept across an activity recreation for one field only,
 * [MapCameraMemory.appliedCameraRequestId].
 *
 * MainActivity does not handle font-scale or night-mode changes itself, so either recreates it. The memory was a plain
 * `remember`, so the recreated map had no record that a saved route's fit (`savedRouteCameraRequest`, whose id stays the same
 * while the route shows) was already applied: it took the fit as new, ended following and framed the route, and the navigation
 * view saved "follow broken" for good (at font 2.0 the frame went to the whole world). With the id kept, the recreated map
 * takes that fit as applied, as a map returning from a tab change already did (dispatch 2026-10-30-08, Amendment 5, S-1). A
 * route opened later has a new id and still frames.
 *
 * Only the id is saved: the rest of the memory (the saved camera, the search frame's id, the cover, the heading, the crowded
 * stacks) stays session-only and is lost on a recreation as before, since the owner's ruling was that nothing else changes.
 * A `null` id saves nothing, and a fresh memory is made, as before.
 *
 * **Same process only.** Saved instance state also comes back after the process is killed in the background, but the ids it
 * would bring back do not mean anything then: a saved route's id counts its opens in `RoutePlottingViewModel.openCount`, which
 * starts again at 0 in a new process, so the first route opened after a process restore could carry the restored id and not be
 * framed. The saved value therefore carries [PROCESS_TOKEN], and a value saved by another process restores nothing (a fresh
 * memory, as before this fix). The id thus lives exactly as long as the route plotting ViewModel's count it names.
 */
@Composable
internal fun rememberMapCameraMemory(): MapCameraMemory = rememberSaveable(saver = MapCameraMemoryAcrossRecreation) { MapCameraMemory() }

/** Made once per process: a value saved with another process's token is from before a process restore. */
private val PROCESS_TOKEN: String = UUID.randomUUID().toString()

private const val TOKEN_END = '\n'

internal val MapCameraMemoryAcrossRecreation: Saver<MapCameraMemory, String> = mapCameraMemorySaver(PROCESS_TOKEN)

/** The saver for a process whose token is [token]; separate so a test can stand in a value saved by another process. */
internal fun mapCameraMemorySaver(token: String): Saver<MapCameraMemory, String> = Saver(
    save = { memory -> memory.appliedCameraRequestId?.let { id -> "$token$TOKEN_END$id" } },
    restore = { saved ->
        val savedToken = saved.substringBefore(TOKEN_END)
        if (savedToken != token) null else MapCameraMemory().apply { appliedCameraRequestId = saved.substringAfter(TOKEN_END) }
    },
)

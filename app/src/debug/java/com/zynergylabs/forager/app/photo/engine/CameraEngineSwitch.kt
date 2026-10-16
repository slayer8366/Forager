package com.zynergylabs.forager.app.photo.engine

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.photo.CameraEngine
import com.zynergylabs.forager.app.photo.CameraEngineSeam

/** [CameraXTimed] is today's CameraX engine with the prototype's measurements around it, for comparison (adb only). */
internal enum class CameraEngineChoice(val id: String) { CameraX("camerax"), Platform("platform"), CameraXTimed("camerax-timed") }

/**
 * The debug-only engine switch (dispatch 2026-10-16-02: "selected by a debug-only switch, through adb and a Settings debug
 * row"). CameraX is the default, and choosing CameraX removes the factory from the seam entirely, so the dialog's path is
 * then byte for byte the one main has (`CameraEngineSeam.create` with nothing installed).
 *
 * **Held for the process only, not stored.** The planner sets it over adb, or on the Diagnostics page, for a device
 * check; a reinstall or a force-stop puts CameraX back. Persisting it across restarts is a per-case decision (CLAUDE.md, UX
 * defaults) that nobody has made for a dev-only switch, and a remembered prototype is the riskier default. A change takes
 * effect at the next camera open: the dialog reads the factory once per open (`InAppCameraHost.kt`).
 */
internal object CameraEngineSwitch {
    var choice: CameraEngineChoice by mutableStateOf(CameraEngineChoice.CameraX)
        private set

    /** The device check's forced fallback: the platform engine gives up at open, before touching the camera. */
    @Volatile
    var forceFallback: Boolean = false

    fun select(choice: CameraEngineChoice, platformFactory: (Context) -> CameraEngine = ::platformCameraEngine) {
        this.choice = choice
        CameraEngineSeam.factory = when (choice) {
            CameraEngineChoice.CameraX -> null
            CameraEngineChoice.Platform -> platformFactory
            CameraEngineChoice.CameraXTimed -> ::timedCameraXEngine
        }
        Log.i(ENGINE_LOG_TAG, "engine=switch event=selected engine=${choice.id} force_fallback=$forceFallback (takes effect at the next camera open)")
    }
}

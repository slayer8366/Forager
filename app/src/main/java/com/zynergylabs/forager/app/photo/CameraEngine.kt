package com.zynergylabs.forager.app.photo

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * DEV BRANCH ONLY (dispatch 2026-10-16-02, RECORD -1083): the one seam the camera-engine prototype needs in main's files.
 * Main and release are not changed by it; this file exists only in a dev-only commit.
 *
 * The camera dialog's slot (`ui/log/InAppCameraHost.kt`) built a `CameraXCaptureSession` directly and drew its
 * `Viewfinder`, a member of that class rather than of the interface (a composable on the interface would force every fake
 * to be one). So choosing an engine needs one thing the dialog can hold: a session and its viewfinder together. That is
 * [CameraEngine]. [CameraEngineSeam.create] is the one factory; with nothing installed it returns today's CameraX engine,
 * unchanged, which is the default every build has. Only the debug build's engine switch ever installs a factory.
 */

/** A camera session and the viewfinder that draws it. Built once per open camera, as the CameraX session was. */
internal interface CameraEngine {
    val session: CameraCaptureSession

    /** [held]: the camera's content is held in the device's natural frame (`NaturalOrientationHold`, RECORD -985). */
    @Composable
    fun Viewfinder(modifier: Modifier, held: Boolean)
}

/** The engine factory. CameraX unless a debug build has installed another; read once per camera open. */
internal object CameraEngineSeam {
    /** Null in every build until the debug build's switch sets it. A factory that returns null means CameraX too. */
    @Volatile
    var factory: ((Context) -> CameraEngine?)? = null

    fun create(context: Context): CameraEngine = factory?.invoke(context) ?: CameraXEngine(CameraXCaptureSession(context))
}

/** Today's engine, exactly as the slot built it: one [CameraXCaptureSession] and its own viewfinder. */
internal class CameraXEngine(override val session: CameraXCaptureSession) : CameraEngine {
    @Composable
    override fun Viewfinder(modifier: Modifier, held: Boolean) = session.Viewfinder(modifier, held = held)
}

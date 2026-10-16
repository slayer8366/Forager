package com.zynergylabs.forager.app.photo.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The prototype's adb trigger (dispatch 2026-10-16-02). Debug builds only, declared in `src/debug/AndroidManifest.xml`,
 * guarded by DUMP like the probes' receivers. Built in the next commit.
 */
class CameraEngineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent): Unit = TODO("receiver: built in the next commit")

    companion object {
        const val ACTION_ENGINE = "com.zynergylabs.forager.app.debug.CAMERA_ENGINE"
        const val ACTION_BURST = "com.zynergylabs.forager.app.debug.CAMERA_ENGINE_BURST"
        const val ACTION_SUMMARY = "com.zynergylabs.forager.app.debug.CAMERA_ENGINE_SUMMARY"
        const val EXTRA_ENGINE = "engine"
        const val EXTRA_FORCE_FALLBACK = "force_fallback"
    }
}

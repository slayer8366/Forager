package com.zynergylabs.forager.app.photo.engine

import android.content.Context
import com.zynergylabs.forager.app.photo.CameraEngine

internal enum class CameraEngineChoice(val id: String) { CameraX("camerax"), Platform("platform") }

/**
 * The debug-only engine switch (dispatch 2026-10-16-02: "selected by a debug-only switch, through adb and a Settings debug
 * row"). CameraX is the default. Built in the next commit.
 */
internal object CameraEngineSwitch {
    val choice: CameraEngineChoice get() = TODO("switch: built in the next commit")

    /** The device check's forced fallback: the platform engine gives up at open, before touching the camera. */
    @Volatile
    var forceFallback: Boolean = false

    fun select(choice: CameraEngineChoice, platformFactory: (Context) -> CameraEngine): Unit = TODO("switch: built in the next commit")
}

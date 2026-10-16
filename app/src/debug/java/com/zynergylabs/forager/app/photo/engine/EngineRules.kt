package com.zynergylabs.forager.app.photo.engine

import com.zynergylabs.forager.app.domain.CameraFacing
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.photo.FlashMode

/*
 * The engine's rules, plain Kotlin (dispatch 2026-10-16-02). Each one restates something today's CameraX camera decides
 * inside the library, read from the pinned 1.6.2 artifacts, so the prototype can match it; each has a test.
 */

/** `Surface.ROTATION_0..3` as degrees. */
internal fun surfaceRotationDegrees(surfaceRotation: Int): Int = TODO("engine rules: built in the next commit")

/**
 * The `JPEG_ORIENTATION` a shot asks for: the sensor's orientation relative to the target rotation, exactly as CameraX's
 * `CameraOrientationUtil.getRelativeImageRotation(dest, source, isOppositeFacing)` computes it for `ImageCapture`.
 */
internal fun jpegOrientationDegrees(sensorOrientation: Int, surfaceRotation: Int, facing: CameraFacing): Int =
    TODO("engine rules: built in the next commit")

/** The JPEG size: today's `standardPhotoSizeSelector` (4032x3024 bound, closest lower then higher, 4:3 first). */
internal fun chooseJpegSize(sizes: List<PixelSize>): PixelSize? = TODO("engine rules: built in the next commit")

/** The viewfinder size: at most 1920x1080 in area, the photo's aspect where offered. */
internal fun choosePreviewSize(sizes: List<PixelSize>, photo: PixelSize): PixelSize? = TODO("engine rules: built in the next commit")

internal data class FillScale(val scaleX: Float, val scaleY: Float)

/**
 * The scale that makes the viewfinder fill its view and crop the overflow, centred: `PreviewView`'s default `FILL_CENTER`,
 * which is today's framing. A `TextureView` stretches the camera's buffer, already turned upright for the device's natural
 * frame, to its own bounds; this undoes the stretch on one axis so the picture keeps its aspect and covers the view.
 */
internal fun fillCenterScale(viewWidth: Int, viewHeight: Int, bufferWidth: Int, bufferHeight: Int, sensorOrientation: Int): FillScale =
    TODO("engine rules: built in the next commit")

internal enum class AeMode { On, OnAutoFlash, OnAlwaysFlash }

/** What a [FlashMode] puts on the requests: decision B's table (`CameraCaptureSession.kt`), in platform terms. */
internal data class FlashRequest(val aeMode: AeMode, val torch: Boolean)

internal fun flashRequest(mode: FlashMode): FlashRequest = TODO("engine rules: built in the next commit")

/** Whether a still in [mode] first runs the AE precapture sequence, as CameraX's capture pipeline decides it. */
internal fun needsPrecapture(mode: FlashMode, latestAeState: Int?): Boolean = TODO("engine rules: built in the next commit")

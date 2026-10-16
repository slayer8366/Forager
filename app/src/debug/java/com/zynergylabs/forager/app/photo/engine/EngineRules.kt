package com.zynergylabs.forager.app.photo.engine

import com.zynergylabs.forager.app.domain.CameraFacing
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.STANDARD_RAW_SELECTOR_BOUND
import com.zynergylabs.forager.app.photo.FlashMode
import kotlin.math.abs

/*
 * The engine's rules, plain Kotlin (dispatch 2026-10-16-02). Each one restates something today's CameraX camera decides
 * inside the library, read from the pinned 1.6.2 artifacts (javap, 2026-10-16), so the prototype can match it; each has a
 * test in `EngineRulesTest`.
 */

/** `Surface.ROTATION_0..3` as degrees (CameraX's `CameraOrientationUtil.surfaceRotationToDegrees`). */
internal fun surfaceRotationDegrees(surfaceRotation: Int): Int = when (surfaceRotation) {
    0 -> 0
    1 -> 90
    2 -> 180
    3 -> 270
    else -> throw IllegalArgumentException("not a surface rotation: $surfaceRotation")
}

/**
 * The `JPEG_ORIENTATION` a shot asks for: the sensor's orientation relative to the target rotation, exactly as CameraX's
 * `CameraOrientationUtil.getRelativeImageRotation(dest, source, isOppositeFacing)` computes it for `ImageCapture` (READ,
 * camera-core 1.6.2: `(source - dest + 360) % 360` for a back camera, `(source + dest) % 360` for a front one).
 */
internal fun jpegOrientationDegrees(sensorOrientation: Int, surfaceRotation: Int, facing: CameraFacing): Int {
    val dest = surfaceRotationDegrees(surfaceRotation)
    return when (facing) {
        CameraFacing.Back -> (sensorOrientation + dest) % 360
        CameraFacing.Front -> (sensorOrientation + dest) % 360
    }
}

/**
 * The JPEG size: today's `standardPhotoSizeSelector` (`CameraXCaptureSession.kt`), restated. The 4032x3024 bound, the
 * largest size within it, else the smallest above it (`FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER`), among the 4:3 sizes
 * first, as CameraX's default aspect ratio is 4:3 with fallback to any. How CameraX ranks two sizes of equal area inside
 * its own selector is not restated; on both test phones the 4:3 list has one size at the bound (INFERRED from -996, -998).
 */
internal fun chooseJpegSize(sizes: List<PixelSize>): PixelSize? {
    if (sizes.isEmpty()) return null
    val fourByThree = sizes.filter { abs(it.width.toDouble() / it.height - 4.0 / 3.0) < 0.01 }
    val pool = fourByThree.ifEmpty { sizes }
    val bound = STANDARD_RAW_SELECTOR_BOUND
    val lower = pool.filter { it.width <= bound.width && it.height <= bound.height }
    return lower.maxByOrNull { it.width.toLong() * it.height } ?: pool.minByOrNull { it.width.toLong() * it.height }
}

/** The viewfinder size: at most 1920x1080 in area, the photo's aspect where offered (the platform burst probe's rule). */
internal fun choosePreviewSize(sizes: List<PixelSize>, photo: PixelSize): PixelSize? {
    val bounded = sizes.filter { it.width.toLong() * it.height <= 1920L * 1080L }
    val aspect = photo.width.toDouble() / photo.height
    val sameAspect = bounded.filter { abs(it.width.toDouble() / it.height - aspect) < 0.01 }
    return sameAspect.ifEmpty { bounded }.maxByOrNull { it.width.toLong() * it.height }
}

internal data class FillScale(val scaleX: Float, val scaleY: Float)

/**
 * The scale that makes the viewfinder fill its view and crop the overflow, centred: `PreviewView`'s default `FILL_CENTER`,
 * which is today's framing. A `TextureView` stretches the camera's buffer, already turned upright for the device's natural
 * frame by the camera (the platform applies the sensor orientation to a SurfaceTexture stream), to its own bounds; this
 * undoes the stretch on one axis so the picture keeps its aspect and covers the view. Applied about the view's centre.
 */
internal fun fillCenterScale(viewWidth: Int, viewHeight: Int, bufferWidth: Int, bufferHeight: Int, sensorOrientation: Int): FillScale {
    if (viewWidth <= 0 || viewHeight <= 0 || bufferWidth <= 0 || bufferHeight <= 0) return FillScale(1f, 1f)
    val sideways = sensorOrientation % 180 != 0
    val contentWidth = if (sideways) bufferHeight else bufferWidth
    val contentHeight = if (sideways) bufferWidth else bufferHeight
    val contentAspect = contentWidth.toFloat() / contentHeight
    val viewAspect = viewWidth.toFloat() / viewHeight
    return if (viewAspect > contentAspect) {
        // The view is wider than the picture: the width fills it and the height overflows.
        FillScale(scaleX = 1f, scaleY = (viewWidth / contentAspect) / viewHeight)
    } else {
        // The view is taller: the height fills it and the width overflows.
        FillScale(scaleX = (viewHeight * contentAspect) / viewWidth, scaleY = 1f)
    }
}

internal enum class AeMode { On, OnAutoFlash, OnAlwaysFlash }

/** What a [FlashMode] puts on the requests: decision B's table (`CameraCaptureSession.kt`), in platform terms. */
internal data class FlashRequest(val aeMode: AeMode, val torch: Boolean)

internal fun flashRequest(mode: FlashMode): FlashRequest = when (mode) {
    FlashMode.Off -> FlashRequest(AeMode.On, torch = false)
    FlashMode.Auto -> FlashRequest(AeMode.OnAutoFlash, torch = false)
    FlashMode.On -> FlashRequest(AeMode.OnAlwaysFlash, torch = false)
    FlashMode.Torch -> FlashRequest(AeMode.On, torch = true)
}

/**
 * Whether a still in [mode] first runs the AE precapture sequence, as CameraX's capture pipeline decides it (READ,
 * camera-camera2 1.6.2 `CapturePipelineImpl.defaultCapture`: with a flash unit and the physical flash required, the
 * precapture-and-capture path, else a plain capture; required means flash mode ON, or AUTO with the latest frame's AE state
 * FLASH_REQUIRED). With no frame read yet, AUTO does not fire.
 */
internal fun needsPrecapture(mode: FlashMode, latestAeState: Int?): Boolean = when (mode) {
    FlashMode.On -> true
    FlashMode.Auto -> latestAeState == PlatformStates.AE_FLASH_REQUIRED
    FlashMode.Off, FlashMode.Torch -> false
}

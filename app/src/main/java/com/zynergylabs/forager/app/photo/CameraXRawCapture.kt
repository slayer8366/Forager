package com.zynergylabs.forager.app.photo

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.os.Build
import android.util.Rational
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import com.zynergylabs.forager.app.domain.RawFrameReadings
import com.zynergylabs.forager.app.domain.raw.SRational
import com.zynergylabs.forager.app.domain.raw.URational
import java.io.File
import java.nio.ByteOrder
import kotlinx.coroutines.delay
import android.hardware.camera2.CameraCaptureSession as PlatformCaptureSession

/*
 * The RAW half of the camera adapter (RAW pipeline stage 2, dispatch 2026-10-15-01 as amended by -1009 and -1011): the RAW
 * capture's use case, the capture results its frames need, and the copy of one RAW frame and its metadata into
 * [RawFrameReadings]. Like `CameraXCapabilityReader.kt`, this file holds the experimental camera2 interop and its opt-ins,
 * so nothing outside the data layer touches it (the stage 1 precedent, RECORD -975's abort condition).
 *
 * ## What was read from the pinned CameraX 1.6.2 artifacts (javap, 2026-10-15), and what is inferred
 *
 * - **In-memory RAW+JPEG is allowed.** `ImageCapture.takePictureInternal` throws "Simultaneous capture RAW and JPEG needs
 *   two output file options" only when an on-disk callback is given without the second file; an in-memory callback passes.
 *   READ.
 * - **The in-memory callback fires once per format.** `ProcessingNode.processInputPacket` posts `onFinalResult(ImageProxy)`
 *   for every packet; `RequestWithCallback.markComplete` holds the request open until both formats are marked done, but
 *   still delivers each image; `TakePictureRequest.onResult` calls `onCaptureSuccess`. So `onCaptureSuccess` is called
 *   twice, once with the RAW_SENSOR image and once with the JPEG. READ; the order of the two is not fixed and is not
 *   assumed.
 * - **The JPEG arrives as its own bytes.** For a JPEG packet with no effect and no metadata quirk, `processInMemoryCapture`
 *   passes the image through (`JpegImage2Result`), so plane 0 is the encoded JPEG. READ. That is not quite the on-disk
 *   path's file: CameraX's `JpegBytes2Disk` also runs its own EXIF update. Here the JPG gets the shot's intended
 *   orientation reapplied ([reapplyIntendedOrientation]) and is scrubbed on persist, as every camera JPG is.
 * - **The capture result reaches us through camera2 interop.** CameraX's own RAW path reads it from an internal type
 *   (`Packet.getCameraCaptureResult`, restricted). The public route is a session capture callback set with
 *   `Camera2Interop.Extender` (experimental): it is added with `SessionConfig.Builder.addCameraCaptureCallback`, which
 *   puts it on the single (still) requests too, and `ImageCapture.takePictureInternal` passes those to the request. READ.
 *   That the platform's `TotalCaptureResult` arrives with a `SENSOR_TIMESTAMP` equal to the RAW image's
 *   `imageInfo.timestamp` is INFERRED (both are the sensor's start-of-exposure time) and is what the device run confirms.
 * - **The lens-shading map** is only in a result when the request asks for it; the RAW capture asks with
 *   `STATISTICS_LENS_SHADING_MAP_MODE_ON` (the platform's own writer writes the gain maps only when the map is there).
 *   Whether asking changes anything else on a given phone is device-only.
 */

/**
 * The RAW+JPEG capture: today's capture settings (minimise latency, the standard photo size, `standardPhotoSizeSelector`)
 * with `OUTPUT_FORMAT_RAW_JPEG`, the capture-result callback, and the lens-shading map asked for. A new use case for a new
 * path; the JPG capture's own builder is untouched (CLAUDE.md, building).
 */
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
internal fun buildRawJpegImageCapture(results: CaptureResultCollector): ImageCapture {
    val builder = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(standardPhotoSizeSelector())
        .setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
    Camera2Interop.Extender(builder)
        .setSessionCaptureCallback(results)
        .setCaptureRequestOption(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON)
    return builder.build()
}

/**
 * Keeps the most recent capture results by sensor timestamp, so a RAW frame can find the result that describes it. The
 * callback also sees every preview frame, so only the last [CAPACITY] are kept.
 */
internal class CaptureResultCollector : PlatformCaptureSession.CaptureCallback() {
    private val results = object : LinkedHashMap<Long, TotalCaptureResult>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, TotalCaptureResult>?): Boolean = size > CAPACITY
    }

    override fun onCaptureCompleted(session: PlatformCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
        val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        synchronized(results) { results[timestamp] = result }
    }

    /** The result for the frame exposed at [timestampNanos], waiting up to [timeoutMillis] for it to arrive; null if it never does. */
    suspend fun await(timestampNanos: Long, timeoutMillis: Long = 1_000L): TotalCaptureResult? {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        while (true) {
            synchronized(results) { results[timestampNanos] }?.let { return it }
            if (System.nanoTime() >= deadline) return null
            delay(POLL_MILLIS)
        }
    }

    private companion object {
        const val CAPACITY = 32
        const val POLL_MILLIS = 20L
    }
}

/** One RAW frame's samples, copied out of the image before it is closed, and its sensor timestamp. */
internal class RawPlane(val width: Int, val height: Int, val samples: ShortArray, val timestampNanos: Long)

/**
 * Copies a RAW_SENSOR image's one plane into a row-major [ShortArray], honouring the row stride (a row may be padded).
 * The samples are 16-bit in the platform's byte order. Throws on anything that is not a one-plane RAW_SENSOR image with
 * two bytes a pixel: the caller treats that as no DNG, never as a guessed layout.
 */
internal fun copyRawPlane(image: ImageProxy): RawPlane {
    check(image.format == ImageFormat.RAW_SENSOR) { "not a RAW_SENSOR image (format ${image.format})" }
    val plane = image.planes.singleOrNull() ?: error("a RAW_SENSOR image with ${image.planes.size} planes")
    check(plane.pixelStride == 2) { "RAW_SENSOR pixel stride ${plane.pixelStride}, not 2" }
    val width = image.width
    val height = image.height
    val samples = ShortArray(width * height)
    val buffer = plane.buffer.duplicate().order(ByteOrder.nativeOrder())
    for (row in 0 until height) {
        buffer.position(row * plane.rowStride)
        buffer.asShortBuffer().get(samples, row * width, width)
    }
    return RawPlane(width, height, samples, image.imageInfo.timestamp)
}

/** Writes a JPEG image's encoded bytes, plane 0, to [destination]. */
internal fun writeJpegImage(image: ImageProxy, destination: File) {
    check(image.format == ImageFormat.JPEG) { "not a JPEG image (format ${image.format})" }
    val buffer = image.planes.first().buffer.duplicate()
    buffer.rewind()
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    destination.parentFile?.mkdirs()
    destination.writeBytes(bytes)
}

/**
 * Copies the frame and every value the DNG needs out of the camera's characteristics and this frame's capture result.
 * Each key is the one the platform's own writer reads (AOSP `DngCreator`, read 2026-10-15); keys that only exist on a
 * later API level are read only there, and are null below it.
 */
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
internal fun readRawFrame(cameraInfo: CameraInfo, plane: RawPlane, result: TotalCaptureResult, orientation: Int): RawFrameReadings {
    val camera = Camera2CameraInfo.from(cameraInfo)
    fun <T> characteristic(key: CameraCharacteristics.Key<T>): T? = runCatching { camera.getCameraCharacteristic(key) }.getOrNull()
    val shading = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP)
    return RawFrameReadings(
        width = plane.width,
        height = plane.height,
        samples = plane.samples,
        colorFilterArrangement = characteristic(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT),
        staticBlackLevel = characteristic(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)?.let { pattern -> IntArray(4).also { pattern.copyTo(it, 0) } },
        dynamicBlackLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL) else null,
        staticWhiteLevel = characteristic(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL),
        dynamicWhiteLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL) else null,
        preCorrectionActiveArray = characteristic(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
            ?.let { intArrayOf(it.left, it.top, it.right, it.bottom) },
        referenceIlluminant1 = characteristic(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1),
        referenceIlluminant2 = characteristic(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.toInt(),
        colorTransform1 = characteristic(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)?.toSRationals(),
        colorTransform2 = characteristic(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)?.toSRationals(),
        forwardMatrix1 = characteristic(CameraCharacteristics.SENSOR_FORWARD_MATRIX1)?.toSRationals(),
        forwardMatrix2 = characteristic(CameraCharacteristics.SENSOR_FORWARD_MATRIX2)?.toSRationals(),
        calibrationTransform1 = characteristic(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)?.toSRationals(),
        calibrationTransform2 = characteristic(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)?.toSRationals(),
        neutralColorPoint = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT)?.map { URational(it.numerator.toLong(), it.denominator.toLong()) },
        noiseProfile = result.get(CaptureResult.SENSOR_NOISE_PROFILE)?.map { it.first to it.second },
        lensShadingColumns = shading?.columnCount,
        lensShadingRows = shading?.rowCount,
        lensShadingGains = shading?.let { map -> FloatArray(map.gainFactorCount).also { map.copyGainFactors(it, 0) } },
        postRawSensitivityBoost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST),
        orientation = orientation,
    )
}

private fun ColorSpaceTransform.toSRationals(): List<SRational> {
    val elements = arrayOfNulls<Rational>(9)
    copyElements(elements, 0)
    return elements.map { r -> SRational(r!!.numerator, r.denominator) }
}

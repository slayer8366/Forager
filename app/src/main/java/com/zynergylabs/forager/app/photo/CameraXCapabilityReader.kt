package com.zynergylabs.forager.app.photo

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaCodecList
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.core.graphics.createBitmap
import com.zynergylabs.forager.app.domain.Camera2Readings
import com.zynergylabs.forager.app.domain.CameraCapabilityReadings
import com.zynergylabs.forager.app.domain.CameraFacing
import com.zynergylabs.forager.app.domain.CameraReading
import com.zynergylabs.forager.app.domain.CameraXRawListing
import com.zynergylabs.forager.app.domain.EncoderReadings
import com.zynergylabs.forager.app.domain.ExposureStateReading
import com.zynergylabs.forager.app.domain.PhysicalCameraReadings
import com.zynergylabs.forager.app.domain.PixelRect
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.PngProbeReading
import java.io.ByteArrayOutputStream

/*
 * The RAW pipeline's stage 1 probe, the reading half (dispatch 2026-10-13-04). The second file in
 * this project to import `androidx.camera`, beside `CameraXCaptureSession.kt`; nothing outside
 * `photo/` does. It copies what the camera library and the platform report into the plain types in
 * `domain/CameraCapabilities.kt`, where the rules that turn readings into answers live and are
 * tested.
 *
 * Every read is caught and kept as a [CameraReading.Failed] with its exception, so a read that
 * throws reaches the log as an unknown with the reason, never as a silent no (CLAUDE.md, errors and
 * failure paths).
 *
 * ## The experimental opt-in, and what does and does not check it
 *
 * `Camera2CameraInfo` is annotated `@ExperimentalCamera2Interop` at class level in CameraX 1.6.2,
 * and that marker is meta-annotated `androidx.annotation.RequiresOptIn` (read from the pinned AAR
 * with `javap -v`). [readCamera2] is the one function that touches it and carries the opt-in,
 * `androidx.annotation.OptIn`, so the experimental surface stays in this file. **The opt-in is
 * checked by Android lint, not by the Kotlin compiler, and CI runs no lint** (`.github/workflows/
 * ci.yml` runs `assembleDebug` and `testDebugUnitTest`), so a green CI run does not show the opt-in
 * is correct. Nothing else here is experimental: `ImageCapture.getImageCaptureCapabilities`, its
 * `OUTPUT_FORMAT_RAW`/`OUTPUT_FORMAT_RAW_JPEG` and `ExposureState` carry no marker in 1.6.2.
 *
 * Device-only, all of it: Robolectric has no camera. `CameraXCapabilityReaderTest` drives these
 * functions with a stub camera-info object, which reaches the real unwrap-failure path and nothing
 * else.
 */

/** Reads one camera. Called on the main thread at bind; the reads are of characteristics the library already holds, except possibly the physical cameras' (device-only). */
internal fun readCameraCapabilities(cameraInfo: CameraInfo, facing: CameraFacing): CameraCapabilityReadings {
    return CameraCapabilityReadings(
        facing = facing,
        cameraXRaw = reading {
            val formats = ImageCapture.getImageCaptureCapabilities(cameraInfo).supportedOutputFormats
            CameraXRawListing(
                raw = ImageCapture.OUTPUT_FORMAT_RAW in formats,
                rawJpeg = ImageCapture.OUTPUT_FORMAT_RAW_JPEG in formats,
            )
        },
        camera2 = readCamera2(cameraInfo),
        exposureState = reading {
            val state = cameraInfo.exposureState
            ExposureStateReading(
                supported = state.isExposureCompensationSupported,
                lower = state.exposureCompensationRange.lower,
                upper = state.exposureCompensationRange.upper,
                stepNumerator = state.exposureCompensationStep.numerator,
                stepDenominator = state.exposureCompensationStep.denominator,
            )
        },
        physicalCameras = reading {
            cameraInfo.physicalCameraInfos.map { physical ->
                PhysicalCameraReadings(cameraId = readCameraId(physical), camera2 = readCamera2(physical))
            }
        },
    )
}

@OptIn(markerClass = [ExperimentalCamera2Interop::class])
private fun readCameraId(cameraInfo: CameraInfo): CameraReading<String> =
    reading { Camera2CameraInfo.from(cameraInfo).cameraId }

/** The camera2 characteristics. `Camera2CameraInfo.from` throws `IllegalArgumentException` for an object it cannot unwrap (read, 1.6.2); that is kept as the reason. */
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
private fun readCamera2(cameraInfo: CameraInfo): CameraReading<Camera2Readings> = reading {
    val camera2 = Camera2CameraInfo.from(cameraInfo)
    Camera2Readings(
        capabilities = camera2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList(),
        exposureTimeRangeNanos = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            ?.let { it.lower to it.upper },
        sensitivityRange = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            ?.let { it.lower to it.upper },
        pixelArray = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            ?.let { PixelSize(it.width, it.height) },
        activeArray = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            ?.let { PixelRect(it.left, it.top, it.right, it.bottom) },
        preCorrectionActiveArray = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
            ?.let { PixelRect(it.left, it.top, it.right, it.bottom) },
        colorFilterArrangement = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT),
        // getOutputSizes(int) returns null for a format the map does not list: no RAW sizes, which is a no, not an unknown.
        rawSensorOutputSizes = camera2.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.let { map -> map.getOutputSizes(ImageFormat.RAW_SENSOR)?.map { PixelSize(it.width, it.height) }.orEmpty() },
    )
}

/**
 * The phone's encoders. Not per camera: run once per process, off the main thread
 * ([CameraXCaptureSession] owns when), because listing the codecs is a platform query whose cost on
 * a real phone is unmeasured.
 */
internal fun readEncoders(): EncoderReadings = EncoderReadings(
    sdkInt = Build.VERSION.SDK_INT,
    encoderMimeTypes = reading {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder }
            .flatMap { it.supportedTypes.asList() }
            .map { it.lowercase() }
            .toSet()
    },
    png = reading { probePng() },
)

/** Compresses a real 1×1 bitmap and checks what came out starts with the PNG signature: evidence, not the platform's promise. */
private fun probePng(): PngProbeReading {
    val bitmap = createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    try {
        val out = ByteArrayOutputStream()
        val returned = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray()
        val signed = bytes.size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }
        return PngProbeReading(compressReturned = returned, bytesWritten = bytes.size, pngSignature = signed)
    } finally {
        bitmap.recycle()
    }
}

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

/** One read, kept: its value, or the exception that stopped it, named and with its message. */
private inline fun <T> reading(block: () -> T): CameraReading<T> =
    runCatching(block).fold(
        onSuccess = { CameraReading.Read(it) },
        onFailure = { CameraReading.Failed("${it::class.java.simpleName}: ${it.message}") },
    )

package com.zynergylabs.forager.app.domain

/*
 * What the bound camera, and the phone's encoders, can do: the RAW pipeline's stage 1 probe
 * (dispatch 2026-10-13-04, RECORD -975). Nothing a user sees reads this yet; later stages decide
 * what to show from it (RECORD -974: phones without RAW support do not show the RAW options).
 *
 * Two halves, both plain Kotlin with no Android imports, so every rule is unit-testable headless:
 *
 * - **Readings** (`CameraCapabilityReadings`, `EncoderReadings`): what the camera library and the
 *   platform reported, copied out of their types, each read either a value or the reason it
 *   failed. Filled by `photo/CameraXCapabilityReader.kt`, the only place that touches the
 *   library for this.
 * - **The model** (`CameraCapabilities`, `EncoderCapabilities`): every answer [Probed.Supported],
 *   [Probed.Unsupported] or [Probed.Unknown] with its reason. Never a guessed value (CLAUDE.md,
 *   errors and failure paths). The mapping functions below are the rules, and each rule has a test
 *   in `CameraCapabilityMappingTest`.
 *
 * A reported range is what the camera says it can do, not what is safe to use (CLAUDE.md,
 * architecture). This stage applies no operating limit; that belongs to the stage that uses them.
 */

/** One answer from the probe. Every one carries why: a basis for a yes or a no, a reason for an unknown. */
sealed interface Probed<out T> {
    /** The camera or platform reported this, and [basis] says what was read. */
    data class Supported<out T>(val value: T, val basis: String) : Probed<T>

    /** The camera or platform reported that it cannot, and [basis] says what was read. */
    data class Unsupported(val basis: String) : Probed<Nothing>

    /** Nothing readable settled it either way. [reason] says what was missing or what failed. */
    data class Unknown(val reason: String) : Probed<Nothing>
}

/** A read from the camera library or the platform: the value it returned, or why it failed. */
sealed interface CameraReading<out T> {
    data class Read<out T>(val value: T) : CameraReading<T>
    data class Failed(val reason: String) : CameraReading<Nothing>
}

enum class CameraFacing { Back, Front }

data class PixelSize(val width: Int, val height: Int) {
    override fun toString() = "${width}x$height"
}

/** A rectangle in sensor pixels: left, top, right, bottom, as the platform's own rectangle states it. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    override fun toString() = "$left,$top,$right,$bottom"
}

/** The sensor's colour filter, in the order of the platform's own constants 0 to 6. */
enum class ColorFilterArrangement { RGGB, GRBG, GBRG, BGGR, RGB, Mono, NearInfrared }

data class ManualExposure(val exposureTimeNanos: LongRange, val iso: IntRange) {
    override fun toString() = "exposure ${exposureTimeNanos.first}..${exposureTimeNanos.last} ns, ISO ${iso.first}..${iso.last}"
}

/** The exposure-compensation range in steps, and one step in EV as a fraction. */
data class ExposureCompensation(val minIndex: Int, val maxIndex: Int, val stepNumerator: Int, val stepDenominator: Int) {
    override fun toString() = "$minIndex..$maxIndex step $stepNumerator/$stepDenominator EV"
}

/** One physical camera behind a logical one: the compact line the planner asked for. */
data class PhysicalCameraCapabilities(
    val cameraId: Probed<String>,
    val raw: Probed<Unit>,
    val manualSensor: Probed<Unit>,
    val rawSizes: Probed<List<PixelSize>>,
)

data class CameraCapabilities(
    val facing: CameraFacing,
    /** RAW output, one DNG. CameraX 1.6.2 lists RAW and RAW+JPEG together, so the two always agree; both are kept because later stages choose between them. */
    val rawOutput: Probed<Unit>,
    val rawPlusJpegOutput: Probed<Unit>,
    /** MANUAL_SENSOR, for HDR brackets by exposure time and ISO. */
    val manualExposure: Probed<ManualExposure>,
    /** The HDR fallback where there is no manual exposure. */
    val exposureCompensation: Probed<ExposureCompensation>,
    val rawSizes: Probed<List<PixelSize>>,
    val pixelArray: Probed<PixelSize>,
    val activeArray: Probed<PixelRect>,
    val preCorrectionActiveArray: Probed<PixelRect>,
    val colorFilter: Probed<ColorFilterArrangement>,
    val physicalCameras: Probed<List<PhysicalCameraCapabilities>>,
)

data class EncoderCapabilities(
    val heif: Probed<Unit>,
    /** Not a HEIF encoder: what a HEIF writer could fall back to where there is no HEIC image encoder. Reported for stage 3. */
    val hevcVideo: Probed<Unit>,
    val png: Probed<Unit>,
)

// ── Readings ────────────────────────────────────────────────────────────────────────────────

/** Whether CameraX's own capabilities call listed the two RAW output formats. */
data class CameraXRawListing(val raw: Boolean, val rawJpeg: Boolean)

/**
 * The camera2 characteristics the probe reads, copied out of the platform's types. A null field
 * is a key the camera did not report. Capability codes are the platform's: 1 MANUAL_SENSOR, 3 RAW.
 */
data class Camera2Readings(
    val capabilities: List<Int>?,
    val exposureTimeRangeNanos: Pair<Long, Long>?,
    val sensitivityRange: Pair<Int, Int>?,
    val pixelArray: PixelSize?,
    val activeArray: PixelRect?,
    val preCorrectionActiveArray: PixelRect?,
    val colorFilterArrangement: Int?,
    /** Null when the camera reports no stream configuration map; empty when the map lists no RAW_SENSOR sizes. */
    val rawSensorOutputSizes: List<PixelSize>?,
)

data class ExposureStateReading(
    val supported: Boolean,
    val lower: Int,
    val upper: Int,
    val stepNumerator: Int,
    val stepDenominator: Int,
)

data class PhysicalCameraReadings(
    val cameraId: CameraReading<String>,
    val camera2: CameraReading<Camera2Readings>,
)

data class CameraCapabilityReadings(
    val facing: CameraFacing,
    val cameraXRaw: CameraReading<CameraXRawListing>,
    val camera2: CameraReading<Camera2Readings>,
    val exposureState: CameraReading<ExposureStateReading>,
    val physicalCameras: CameraReading<List<PhysicalCameraReadings>>,
)

/** What a 1×1 bitmap compressed to PNG produced. */
data class PngProbeReading(val compressReturned: Boolean, val bytesWritten: Int, val pngSignature: Boolean)

data class EncoderReadings(
    val sdkInt: Int,
    /** The MIME types every encoder on the device lists, lower case. */
    val encoderMimeTypes: CameraReading<Set<String>>,
    val png: CameraReading<PngProbeReading>,
)

// ── The rules ───────────────────────────────────────────────────────────────────────────────

const val CAPABILITY_MANUAL_SENSOR = 1
const val CAPABILITY_RAW = 3

/** The platform's HEIC image MIME type, from API 28 (`MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC`). */
const val MIME_HEIC_IMAGE = "image/vnd.android.heic"
const val MIME_HEVC_VIDEO = "video/hevc"
const val HEIC_MIN_SDK = 28

fun mapCameraCapabilities(readings: CameraCapabilityReadings): CameraCapabilities = CameraCapabilities(
    facing = readings.facing,
    rawOutput = mapRawOutput(readings.cameraXRaw, readings.camera2) { it.raw },
    rawPlusJpegOutput = mapRawOutput(readings.cameraXRaw, readings.camera2) { it.rawJpeg },
    manualExposure = mapManualExposure(readings.camera2),
    exposureCompensation = mapExposureCompensation(readings.exposureState),
    rawSizes = mapRawSizes(readings.camera2),
    pixelArray = mapReported(readings.camera2, "pixel array size") { it.pixelArray },
    activeArray = mapReported(readings.camera2, "active array") { it.activeArray },
    preCorrectionActiveArray = mapReported(readings.camera2, "pre-correction active array") { it.preCorrectionActiveArray },
    colorFilter = mapColorFilter(readings.camera2),
    physicalCameras = mapPhysicalCameras(readings.physicalCameras),
)

/**
 * CameraX's listing decides a yes. A no is cross-checked against camera2, because CameraX 1.6.2
 * also answers no, silently, for a camera-info object it cannot see inside
 * (`ImageCapture$ImageCaptureCapabilitiesImpl.isRawSupported`, read from the pinned artifact).
 */
internal fun mapRawOutput(
    cameraXRaw: CameraReading<CameraXRawListing>,
    camera2: CameraReading<Camera2Readings>,
    listed: (CameraXRawListing) -> Boolean,
): Probed<Unit> {
    val listing = when (cameraXRaw) {
        is CameraReading.Failed -> return Probed.Unknown("CameraX's output formats could not be read: ${cameraXRaw.reason}")
        is CameraReading.Read -> cameraXRaw.value
    }
    if (listed(listing)) return Probed.Supported(Unit, "CameraX lists the output format")
    val characteristics = when (camera2) {
        is CameraReading.Failed -> return Probed.Unknown("CameraX lists no RAW output, and camera2 could not be read to confirm it: ${camera2.reason}")
        is CameraReading.Read -> camera2.value
    }
    val capabilities = characteristics.capabilities
        ?: return Probed.Unknown("CameraX lists no RAW output, and the camera reports no capability list to confirm it")
    return if (CAPABILITY_RAW in capabilities) {
        Probed.Unknown("CameraX lists no RAW output, but the camera lists the RAW capability")
    } else {
        Probed.Unsupported("CameraX lists no RAW output, and the camera does not list the RAW capability")
    }
}

internal fun mapManualExposure(camera2: CameraReading<Camera2Readings>): Probed<ManualExposure> {
    val characteristics = when (camera2) {
        is CameraReading.Failed -> return Probed.Unknown("camera2 could not be read: ${camera2.reason}")
        is CameraReading.Read -> camera2.value
    }
    val capabilities = characteristics.capabilities
        ?: return Probed.Unknown("the camera reports no capability list")
    if (CAPABILITY_MANUAL_SENSOR !in capabilities) return Probed.Unsupported("the camera does not list MANUAL_SENSOR")
    val time = characteristics.exposureTimeRangeNanos
        ?: return Probed.Unknown("MANUAL_SENSOR is listed but no exposure-time range is reported")
    val iso = characteristics.sensitivityRange
        ?: return Probed.Unknown("MANUAL_SENSOR is listed but no sensitivity range is reported")
    if (time.first <= 0 || time.first > time.second) {
        return Probed.Unknown("MANUAL_SENSOR is listed but the exposure-time range ${time.first}..${time.second} ns is not a range")
    }
    if (iso.first <= 0 || iso.first > iso.second) {
        return Probed.Unknown("MANUAL_SENSOR is listed but the sensitivity range ${iso.first}..${iso.second} is not a range")
    }
    return Probed.Supported(
        ManualExposure(time.first..time.second, iso.first..iso.second),
        "the camera lists MANUAL_SENSOR with exposure-time and sensitivity ranges",
    )
}

internal fun mapExposureCompensation(state: CameraReading<ExposureStateReading>): Probed<ExposureCompensation> {
    val reading = when (state) {
        is CameraReading.Failed -> return Probed.Unknown("CameraX's exposure state could not be read: ${state.reason}")
        is CameraReading.Read -> state.value
    }
    if (!reading.supported) return Probed.Unsupported("CameraX reports exposure compensation unsupported")
    if (reading.stepNumerator == 0 || reading.stepDenominator == 0) {
        return Probed.Unknown("CameraX reports exposure compensation supported with a step of ${reading.stepNumerator}/${reading.stepDenominator}")
    }
    if (reading.lower >= reading.upper) {
        return Probed.Unknown("CameraX reports exposure compensation supported with an empty range ${reading.lower}..${reading.upper}")
    }
    return Probed.Supported(
        ExposureCompensation(reading.lower, reading.upper, reading.stepNumerator, reading.stepDenominator),
        "CameraX's exposure state",
    )
}

internal fun mapRawSizes(camera2: CameraReading<Camera2Readings>): Probed<List<PixelSize>> {
    val characteristics = when (camera2) {
        is CameraReading.Failed -> return Probed.Unknown("camera2 could not be read: ${camera2.reason}")
        is CameraReading.Read -> camera2.value
    }
    val sizes = characteristics.rawSensorOutputSizes
        ?: return Probed.Unknown("the camera reports no stream configuration map")
    if (sizes.isEmpty()) return Probed.Unsupported("the stream configuration map lists no RAW_SENSOR sizes")
    return Probed.Supported(
        sizes.sortedByDescending { it.width.toLong() * it.height },
        "the stream configuration map's RAW_SENSOR sizes",
    )
}

/** A characteristic that is a plain value: reported, or not. Not reported is unknown, not unsupported. */
internal fun <T : Any> mapReported(
    camera2: CameraReading<Camera2Readings>,
    name: String,
    value: (Camera2Readings) -> T?,
): Probed<T> {
    val characteristics = when (camera2) {
        is CameraReading.Failed -> return Probed.Unknown("camera2 could not be read: ${camera2.reason}")
        is CameraReading.Read -> camera2.value
    }
    val reported = value(characteristics) ?: return Probed.Unknown("the camera does not report its $name")
    return Probed.Supported(reported, "the camera's characteristics")
}

internal fun mapColorFilter(camera2: CameraReading<Camera2Readings>): Probed<ColorFilterArrangement> {
    val characteristics = when (camera2) {
        is CameraReading.Failed -> return Probed.Unknown("camera2 could not be read: ${camera2.reason}")
        is CameraReading.Read -> camera2.value
    }
    val code = characteristics.colorFilterArrangement
        ?: return Probed.Unknown("the camera does not report its colour filter arrangement")
    val arrangement = ColorFilterArrangement.entries.getOrNull(code)
        ?: return Probed.Unknown("the camera reports an unrecognised colour filter arrangement $code")
    return Probed.Supported(arrangement, "the camera's characteristics")
}

/**
 * Physical cameras read through camera2 only: CameraX's own capabilities call cannot see inside a
 * physical camera's info object in 1.6.2 (it is not CameraX's internal type), so it would answer
 * no for every one of them whatever the hardware does.
 */
internal fun mapPhysicalCameras(physical: CameraReading<List<PhysicalCameraReadings>>): Probed<List<PhysicalCameraCapabilities>> {
    val cameras = when (physical) {
        is CameraReading.Failed -> return Probed.Unknown("the physical cameras could not be read: ${physical.reason}")
        is CameraReading.Read -> physical.value
    }
    if (cameras.isEmpty()) return Probed.Unsupported("the camera reports no physical cameras")
    return Probed.Supported(
        cameras.map { camera ->
            PhysicalCameraCapabilities(
                cameraId = when (val id = camera.cameraId) {
                    is CameraReading.Failed -> Probed.Unknown("the camera id could not be read: ${id.reason}")
                    is CameraReading.Read -> Probed.Supported(id.value, "camera2's camera id")
                },
                raw = mapCapabilityListed(camera.camera2, CAPABILITY_RAW, "RAW"),
                manualSensor = mapCapabilityListed(camera.camera2, CAPABILITY_MANUAL_SENSOR, "MANUAL_SENSOR"),
                rawSizes = mapRawSizes(camera.camera2),
            )
        },
        "the camera's physical cameras",
    )
}

internal fun mapCapabilityListed(camera2: CameraReading<Camera2Readings>, capability: Int, name: String): Probed<Unit> {
    val characteristics = when (camera2) {
        is CameraReading.Failed -> return Probed.Unknown("camera2 could not be read: ${camera2.reason}")
        is CameraReading.Read -> camera2.value
    }
    val capabilities = characteristics.capabilities
        ?: return Probed.Unknown("the camera reports no capability list")
    return if (capability in capabilities) {
        Probed.Supported(Unit, "the camera lists $name")
    } else {
        Probed.Unsupported("the camera does not list $name")
    }
}

fun mapEncoderCapabilities(readings: EncoderReadings): EncoderCapabilities = EncoderCapabilities(
    heif = mapHeif(readings.sdkInt, readings.encoderMimeTypes),
    hevcVideo = mapEncoderListed(readings.encoderMimeTypes, MIME_HEVC_VIDEO),
    png = mapPng(readings.png),
)

internal fun mapHeif(sdkInt: Int, mimeTypes: CameraReading<Set<String>>): Probed<Unit> {
    if (sdkInt < HEIC_MIN_SDK) return Probed.Unsupported("Android $sdkInt has no HEIC image type; it arrived in API $HEIC_MIN_SDK")
    return mapEncoderListed(mimeTypes, MIME_HEIC_IMAGE)
}

internal fun mapEncoderListed(mimeTypes: CameraReading<Set<String>>, mime: String): Probed<Unit> {
    val types = when (mimeTypes) {
        is CameraReading.Failed -> return Probed.Unknown("the encoder list could not be read: ${mimeTypes.reason}")
        is CameraReading.Read -> mimeTypes.value
    }
    return if (mime in types) {
        Probed.Supported(Unit, "an encoder lists $mime")
    } else {
        Probed.Unsupported("no encoder lists $mime")
    }
}

/** Evidence over guarantee (planner, 2026-10-14): PNG is supported because a real compression produced one, not because the platform promises it. */
internal fun mapPng(png: CameraReading<PngProbeReading>): Probed<Unit> {
    val reading = when (png) {
        is CameraReading.Failed -> return Probed.Unknown("compressing a 1x1 bitmap to PNG threw: ${png.reason}")
        is CameraReading.Read -> png.value
    }
    if (!reading.compressReturned) return Probed.Unsupported("compressing a 1x1 bitmap to PNG returned false")
    if (!reading.pngSignature) {
        return Probed.Unknown("compressing a 1x1 bitmap to PNG returned true but wrote ${reading.bytesWritten} bytes without a PNG signature")
    }
    return Probed.Supported(Unit, "a 1x1 bitmap compressed to ${reading.bytesWritten} bytes with a PNG signature")
}

// ── Log lines ───────────────────────────────────────────────────────────────────────────────

/**
 * The lines logged at bind: the camera's own line, then one per physical camera. [probeId] and the
 * facing are on every line so the encoder line, logged later from another thread, can be matched
 * to them. [bound] is false for a camera read without being bound (the front camera).
 */
fun CameraCapabilities.toLogLines(probeId: Int, bound: Boolean): List<String> {
    val head = "probe=$probeId facing=${facing.label()}${if (bound) "" else " unbound"}"
    val physicalCount = when (val p = physicalCameras) {
        is Probed.Supported -> "supported(${p.value.size})"
        else -> p.logText()
    }
    val main = "$head raw=${rawOutput.logText()} rawJpeg=${rawPlusJpegOutput.logText()} " +
        "manualExposure=${manualExposure.logText()} evComp=${exposureCompensation.logText()} " +
        "rawSizes=${rawSizes.logText()} pixelArray=${pixelArray.logText()} activeArray=${activeArray.logText()} " +
        "preCorrectionArray=${preCorrectionActiveArray.logText()} cfa=${colorFilter.logText()} physical=$physicalCount"
    val physical = (physicalCameras as? Probed.Supported<List<PhysicalCameraCapabilities>>)?.value.orEmpty().mapIndexed { index, camera ->
        "$head physical[$index] id=${camera.cameraId.logText()} raw=${camera.raw.logText()} " +
            "manualSensor=${camera.manualSensor.logText()} rawSizes=${camera.rawSizes.logText()}"
    }
    return listOf(main) + physical
}

/** The encoder line: read once per process, logged for each probe that waited for it. */
fun EncoderCapabilities.toLogLine(probeId: Int, facing: CameraFacing): String {
    return "probe=$probeId facing=${facing.label()} encoders heif=${heif.logText()} hevcVideo=${hevcVideo.logText()} png=${png.logText()}"
}

private fun CameraFacing.label() = when (this) {
    CameraFacing.Back -> "back"
    CameraFacing.Front -> "front"
}

/** A yes shows its value, or its basis when there is no value; a no shows its basis; an unknown shows its reason. */
private fun Probed<*>.logText(): String = when (this) {
    is Probed.Supported<*> -> when (val v = value) {
        Unit -> "supported($basis)"
        is List<*> -> "supported(${v.joinToString(" ")})"
        else -> "supported($v)"
    }
    is Probed.Unsupported -> "unsupported($basis)"
    is Probed.Unknown -> "unknown($reason)"
}

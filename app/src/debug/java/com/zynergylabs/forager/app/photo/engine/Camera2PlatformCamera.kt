package com.zynergylabs.forager.app.photo.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Rational
import android.view.Surface
import com.zynergylabs.forager.app.domain.Camera2Readings
import com.zynergylabs.forager.app.domain.CameraCapabilityReadings
import com.zynergylabs.forager.app.domain.CameraFacing
import com.zynergylabs.forager.app.domain.CameraReading
import com.zynergylabs.forager.app.domain.CameraXRawListing
import com.zynergylabs.forager.app.domain.ExposureStateReading
import com.zynergylabs.forager.app.domain.PhysicalCameraReadings
import com.zynergylabs.forager.app.domain.PixelRect
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.Probed
import com.zynergylabs.forager.app.domain.RawFrameReadings
import com.zynergylabs.forager.app.domain.mapCameraCapabilities
import com.zynergylabs.forager.app.domain.rawCaptureSize
import com.zynergylabs.forager.app.domain.raw.SRational
import com.zynergylabs.forager.app.domain.raw.URational
import java.io.File
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Camera engine A's one camera2 file (dispatch 2026-10-16-02). Everything here is device-only: Robolectric has no camera
 * HAL, so not one line of this file runs in any test. The engine's decisions are tested in `PlatformCameraEngineTest`; what
 * this file does with them is the device check's. Written against the AOSP camera2 documentation and the two probes on dev
 * that ran on both phones (`diagnostics/burst/PlatformBurstCameraImpl.kt`, `diagnostics/sensor/PlatformPinCamera.kt`, read,
 * not edited), whose open, configure, burst and close sequences this follows.
 *
 * Threads: camera callbacks arrive on one camera thread ([CameraThread]); everything the engine sees is posted to the main
 * thread ([main]); the suspend functions resume on the caller's dispatcher, the engine's main one.
 */

/** One camera thread for the process: an engine per camera open would otherwise leave a thread behind each time. */
private object CameraThread {
    val handler: Handler by lazy { Handler(HandlerThread("CameraEngineA").apply { start() }.looper) }
    val executor: Executor by lazy { Executor { handler.post(it) } }
}

@SuppressLint("MissingPermission") // the dialog opens only once the camera permission is granted
internal class Camera2PlatformCamera(context: Context) : PlatformCamera {

    private val manager: CameraManager = context.getSystemService(CameraManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val handler get() = CameraThread.handler

    @Volatile private var chars: CameraCharacteristics? = null
    @Volatile private var rawChars: CameraCharacteristics? = null
    @Volatile private var device: CameraDevice? = null
    @Volatile private var deviceClosed: CompletableDeferred<Unit>? = null
    @Volatile private var session: CameraCaptureSession? = null
    @Volatile private var sessionClosed: CompletableDeferred<Unit>? = null
    @Volatile private var previewSurface: Surface? = null
    @Volatile private var jpegReader: ImageReader? = null
    @Volatile private var rawReader: ImageReader? = null
    @Volatile private var rawSource: RawSource = RawSource.None
    @Volatile private var latestResult: TotalCaptureResult? = null
    @Volatile private var frameSink: ((PreviewFrame) -> Unit)? = null
    @Volatile private var afContinuous = false
    @Volatile private var lensShadingMapOn = false

    // The lens chooser (dispatch 2026-10-16-04).
    @Volatile private var lensChars: Map<String, CameraCharacteristics> = emptyMap()
    /** The lens whose physical streams the session carries, or null for the logical streams of before. */
    @Volatile private var streamingLens: Lens? = null
    /** SCALER_CROP_REGION is a per-lens request key (availablePhysicalCameraRequestKeys): on the S22, not on the S26 (the dumpsys tables, READ). */
    @Volatile private var cropPerLens = false
    @Volatile private var logicalZoomRange: android.util.Range<Float>? = null

    // ── Describe ──────────────────────────────────────────────────────────────────────────────────────────────────────

    override fun describe(): CameraDescription = try {
        describeOrThrow()
    } catch (failure: Exception) {
        CameraDescription.NoCamera("reading the camera failed: ${failure::class.java.simpleName}: ${failure.message}")
    }

    private fun describeOrThrow(): CameraDescription {
        val ids = manager.cameraIdList.toList()
        fun facingOf(id: String) = runCatching { manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) }.getOrNull()
        // CameraX's DEFAULT_BACK_CAMERA, else the front (CameraXCaptureSession.open): the first back camera listed.
        val id = ids.firstOrNull { facingOf(it) == CameraMetadata.LENS_FACING_BACK }
            ?: ids.firstOrNull { facingOf(it) == CameraMetadata.LENS_FACING_FRONT }
            ?: return CameraDescription.NoCamera("no back or front camera is listed (camera IDs $ids)")
        val c = manager.getCameraCharacteristics(id)
        chars = c
        val facing = if (c.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT) CameraFacing.Front else CameraFacing.Back
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return CameraDescription.NoCamera("camera $id reports no stream configuration map")
        val jpeg = chooseJpegSize(map.getOutputSizes(ImageFormat.JPEG)?.map { PixelSize(it.width, it.height) }.orEmpty())
            ?: return CameraDescription.NoCamera("camera $id lists no JPEG size")
        val preview = choosePreviewSize(map.getOutputSizes(SurfaceTexture::class.java)?.map { PixelSize(it.width, it.height) }.orEmpty(), jpeg)
            ?: return CameraDescription.NoCamera("camera $id lists no viewfinder size at or under 1920x1080")
        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        val hasRaw = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in caps
        val readings = CameraCapabilityReadings(
            facing = facing,
            // Not CameraX's listing here: the platform engine reads the RAW capability itself. Restated below as the basis.
            cameraXRaw = CameraReading.Read(CameraXRawListing(raw = hasRaw, rawJpeg = hasRaw)),
            camera2 = CameraReading.Read(camera2Readings(c)),
            exposureState = runCatching {
                val range = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                val step = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                CameraReading.Read(
                    ExposureStateReading(
                        supported = range != null && range.lower != range.upper,
                        lower = range?.lower ?: 0,
                        upper = range?.upper ?: 0,
                        stepNumerator = step?.numerator ?: 0,
                        stepDenominator = step?.denominator ?: 0,
                    ),
                )
            }.getOrElse { CameraReading.Failed("${it::class.java.simpleName}: ${it.message}") },
            physicalCameras = CameraReading.Read(physicalIds(c).map { pid ->
                PhysicalCameraReadings(
                    cameraId = CameraReading.Read(pid),
                    camera2 = runCatching { CameraReading.Read(camera2Readings(manager.getCameraCharacteristics(pid))) }
                        .getOrElse { CameraReading.Failed("${it::class.java.simpleName}: ${it.message}") },
                )
            }),
        )
        val rawBasis = if (hasRaw) {
            Probed.Supported(Unit, "the camera lists the RAW capability (read by the platform engine, not CameraX)")
        } else {
            Probed.Unsupported("the camera does not list the RAW capability")
        }
        val capabilities = mapCameraCapabilities(readings).copy(rawOutput = rawBasis, rawPlusJpegOutput = rawBasis)
        val raw = rawCaptureSize(capabilities)
        val notes = mutableListOf<String>()
        val (mainWide, mainWideNote) = chooseMainWide(id, c, raw, notes)
        val (lenses, lensNote) = readLensSet(id, c)
        val nrModes = c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.toList().orEmpty()
        afContinuous = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true
        lensShadingMapOn = c.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)
            ?.contains(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON) == true
        notes += "hardware level ${c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)}, capabilities $caps, " +
            "AF continuous-picture ${if (afContinuous) "offered" else "not offered"}, lens-shading map ${if (lensShadingMapOn) "offered" else "not offered"}, " +
            "noise reduction modes $nrModes"
        return CameraDescription.Described(
            CameraFacts(
                cameraId = id,
                facing = facing,
                sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
                hasFlashUnit = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                jpegSize = jpeg,
                previewSize = preview,
                rawSize = raw,
                mainWidePhysicalId = mainWide,
                mainWideNote = mainWideNote,
                noiseReductionOffOffered = CaptureRequest.NOISE_REDUCTION_MODE_OFF in nrModes,
                capabilities = capabilities,
                notes = notes,
                lenses = lenses,
                lensNote = lensNote,
            ),
        )
    }

    private fun camera2Readings(c: CameraCharacteristics) = Camera2Readings(
        capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList(),
        exposureTimeRangeNanos = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { it.lower to it.upper },
        sensitivityRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { it.lower to it.upper },
        pixelArray = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let { PixelSize(it.width, it.height) },
        activeArray = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let { PixelRect(it.left, it.top, it.right, it.bottom) },
        preCorrectionActiveArray = c.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)?.let { PixelRect(it.left, it.top, it.right, it.bottom) },
        colorFilterArrangement = c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT),
        rawSensorOutputSizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.let { map -> map.getOutputSizes(ImageFormat.RAW_SENSOR)?.map { PixelSize(it.width, it.height) }.orEmpty() },
    )

    private fun physicalIds(c: CameraCharacteristics): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) c.physicalCameraIds.sorted() else emptyList()

    /**
     * The lens chooser's lenses behind the logical camera [id] (dispatch 2026-10-16-04), read from characteristics by the
     * shared rules ([buildLensSet]). Null with the reason when there is no lens set: the engine then runs as before.
     */
    private fun readLensSet(id: String, c: CameraCharacteristics): Pair<LensSet?, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null to "the lens chooser needs API 28 (physical streams)"
        val physical = physicalIds(c)
        if (physical.isEmpty()) return null to "camera $id is not a logical multi-camera"
        val chars = physical.mapNotNull { pid -> runCatching { pid to manager.getCameraCharacteristics(pid) }.getOrNull() }.toMap()
        lensChars = chars
        cropPerLens = runCatching { c.availablePhysicalCameraRequestKeys?.contains(CaptureRequest.SCALER_CROP_REGION) == true }.getOrDefault(false)
        logicalZoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
        val cropNote = "crop region per lens ${if (cropPerLens) "offered" else "not offered (the logical zoom ratio is asked instead)"}, logical zoom range $logicalZoomRange"
        return when (val outcome = buildLensSet(lensReading(id, c), chars.map { (pid, pc) -> lensReading(pid, pc) })) {
            is LensSetOutcome.Built -> outcome.set to (outcome.notes + cropNote).joinToString("; ")
            is LensSetOutcome.NoLenses -> null to outcome.reason
        }
    }

    private fun lensReading(id: String, c: CameraCharacteristics): LensReading {
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        fun sizes(format: Int) = map?.getOutputSizes(format)?.map { PixelSize(it.width, it.height) }.orEmpty()
        return LensReading(
            cameraId = id,
            back = c.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK,
            focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
            physicalSizeMm = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let { listOf(it.width, it.height) },
            cfa = c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT),
            jpegSizes = sizes(ImageFormat.JPEG),
            previewSizes = map?.getOutputSizes(SurfaceTexture::class.java)?.map { PixelSize(it.width, it.height) }.orEmpty(),
            rawCapable = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true,
            rawSizes = sizes(ImageFormat.RAW_SENSOR),
            activeArray = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let { PixelSize(it.width(), it.height()) },
            minFocusDiopters = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE),
            sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION),
        )
    }

    /**
     * The main wide behind a logical camera (RECORD -1079): the one physical camera whose focal length, sensor size and
     * colour filter are the logical camera's own, since a logical camera's static lens and sensor metadata describe its
     * default active physical camera (CameraMetadata, LOGICAL_MULTI_CAMERA). The same rule as the sensor watch's
     * `MainWideChooser` (diagnostics/sensor/SensorWatchModel.kt), restated here rather than called, so this branch does
     * not compile against a file the camera-reach coder is changing; reported as a shared helper. It must also offer the
     * RAW size the session binds at, or the physical stream could not carry it.
     */
    private fun chooseMainWide(id: String, c: CameraCharacteristics, raw: PixelSize?, notes: MutableList<String>): Pair<String?, String> {
        val physical = physicalIds(c)
        if (physical.isEmpty()) return null to "camera $id is not a logical multi-camera (or the platform is below API 28)"
        if (raw == null) return null to "no RAW stream to pin"
        val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        val size = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val cfa = c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
        if (focal == null || size == null || cfa == null) return null to "the logical camera's own focal length, sensor size or colour filter is not reported"
        val matches = physical.filter { pid ->
            runCatching {
                val p = manager.getCameraCharacteristics(pid)
                val pSize = p.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                p.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.any { abs(it - focal) < 0.01f } == true &&
                    pSize != null && abs(pSize.width - size.width) < 0.01f && abs(pSize.height - size.height) < 0.01f &&
                    p.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) == cfa
            }.getOrDefault(false)
        }
        notes += "physical cameras $physical; the logical camera's own lens: focal $focal mm, sensor ${size.width}x${size.height} mm, CFA $cfa; matches $matches"
        val chosen = matches.singleOrNull() ?: return null to "${matches.size} physical cameras match the logical camera's own lens; none is pinned"
        val rawSizes = runCatching {
            manager.getCameraCharacteristics(chosen).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.RAW_SENSOR)?.map { PixelSize(it.width, it.height) }.orEmpty()
        }.getOrDefault(emptyList())
        if (raw !in rawSizes) return null to "physical camera $chosen does not list the RAW size $raw (lists $rawSizes)"
        return chosen to "physical camera $chosen has the logical camera's own lens"
    }

    // ── Open ──────────────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun open(facts: CameraFacts, events: (DeviceEvent) -> Unit): OpenOutcome = suspendCancellableCoroutine { continuation ->
        val closed = CompletableDeferred<Unit>()
        deviceClosed = closed
        val callback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                // Kept before resuming, so a release always finds it, even if the open was cancelled meanwhile.
                device = camera
                if (continuation.isActive) continuation.resume(OpenOutcome.Opened) else camera.close()
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                if (device === camera) device = null
                if (continuation.isActive) {
                    continuation.resume(OpenOutcome.InUse("onDisconnected while opening"))
                } else {
                    main.post { events(DeviceEvent.Disconnected) }
                }
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                if (device === camera) device = null
                val name = deviceErrorName(error)
                val inUse = error == CameraDevice.StateCallback.ERROR_CAMERA_IN_USE || error == CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE
                if (continuation.isActive) {
                    continuation.resume(if (inUse) OpenOutcome.InUse("onError $name") else OpenOutcome.Failed("onError $name"))
                } else {
                    main.post { events(DeviceEvent.Error(name, inUse)) }
                }
            }

            override fun onClosed(camera: CameraDevice) {
                closed.complete(Unit)
            }
        }
        try {
            manager.openCamera(facts.cameraId, callback, handler)
        } catch (failure: CameraAccessException) {
            val inUse = failure.reason == CameraAccessException.CAMERA_IN_USE || failure.reason == CameraAccessException.MAX_CAMERAS_IN_USE
            val reason = "openCamera threw CameraAccessException reason ${failure.reason}: ${failure.message}"
            continuation.resume(if (inUse) OpenOutcome.InUse(reason) else OpenOutcome.Failed(reason))
        } catch (failure: Exception) {
            continuation.resume(OpenOutcome.Failed("openCamera threw ${failure::class.java.simpleName}: ${failure.message}"))
        }
    }

    // ── Configure ─────────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun configure(facts: CameraFacts, preview: PreviewTarget, plan: StreamPlan): ConfigureOutcome {
        val opened = device ?: return ConfigureOutcome.Failed("no device is open")
        val target = preview as? Camera2PreviewTarget ?: return ConfigureOutcome.Failed("the viewfinder's surface is not a camera2 one")
        val surface = target.surface
        plan.lens?.let { lens -> return configureLens(opened, target, lens, plan.raw) }
        streamingLens = null
        closeSessionAndWait()
        closeReaders()
        previewSurface = surface
        val jpeg = ImageReader.newInstance(facts.jpegSize.width, facts.jpegSize.height, ImageFormat.JPEG, JPEG_MAX_IMAGES)
        jpegReader = jpeg
        val raw = facts.rawSize?.takeIf { plan.raw }?.let { ImageReader.newInstance(it.width, it.height, ImageFormat.RAW_SENSOR, RAW_MAX_IMAGES) }
        rawReader = raw
        val candidates = buildList {
            if (raw != null) {
                val pin = facts.mainWidePhysicalId
                if (pin != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(RawSource.Physical(pin))
                add(RawSource.Logical)
            }
            add(RawSource.None)
        }
        val notes = mutableListOf<String>()
        for (candidate in candidates) {
            val outputs = outputsFor(surface, jpeg, raw, candidate)
            val query = querySupport(opened, outputs)
            notes += "${label(candidate)}: isSessionConfigurationSupported $query"
            if (query == "not supported") continue
            val configured = createSession(opened, outputs)
            if (configured == null) {
                notes += "${label(candidate)}: onConfigureFailed"
                continue
            }
            session = configured
            rawSource = candidate
            rawChars = when (candidate) {
                is RawSource.Physical -> runCatching { manager.getCameraCharacteristics(candidate.physicalId) }.getOrNull() ?: chars
                else -> chars
            }
            if (candidate == RawSource.None) {
                raw?.close()
                rawReader = null
            }
            return ConfigureOutcome.Configured(candidate, notes.joinToString("; "))
        }
        return ConfigureOutcome.Failed("no stream combination configured (${notes.joinToString("; ")})")
    }

    /**
     * The lens chooser's session: the viewfinder, the JPEG and (where the lens has one and the session takes it) the RAW
     * stream, all physical streams of [lens] on the logical camera, at the lens's own sizes. The viewfinder surface is the
     * same one throughout, so on a lens switch the view keeps its last frame until the new lens draws (RECORD -1107).
     */
    private suspend fun configureLens(opened: CameraDevice, target: Camera2PreviewTarget, lens: Lens, wantRaw: Boolean): ConfigureOutcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return ConfigureOutcome.Failed("physical streams need API 28")
        closeSessionAndWait()
        closeReaders()
        target.setBufferSize(lens.previewSize.width, lens.previewSize.height)
        val surface = target.surface
        previewSurface = surface
        val jpeg = ImageReader.newInstance(lens.jpegSize.width, lens.jpegSize.height, ImageFormat.JPEG, JPEG_MAX_IMAGES)
        jpegReader = jpeg
        val raw = lens.rawSize?.takeIf { wantRaw }?.let { ImageReader.newInstance(it.width, it.height, ImageFormat.RAW_SENSOR, RAW_MAX_IMAGES) }
        rawReader = raw
        val notes = mutableListOf<String>()
        for (withRaw in listOfNotNull(true.takeIf { raw != null }, false)) {
            val outputs = buildList {
                add(OutputConfiguration(surface).also { it.setPhysicalCameraId(lens.physicalId) })
                add(OutputConfiguration(jpeg.surface).also { it.setPhysicalCameraId(lens.physicalId) })
                if (withRaw && raw != null) add(OutputConfiguration(raw.surface).also { it.setPhysicalCameraId(lens.physicalId) })
            }
            val label = "lens ${lens.physicalId} ${if (withRaw) "with" else "without"} RAW"
            val query = querySupport(opened, outputs)
            notes += "$label: isSessionConfigurationSupported $query"
            if (query == "not supported") continue
            val configured = createSession(opened, outputs)
            if (configured == null) {
                notes += "$label: onConfigureFailed"
                continue
            }
            session = configured
            streamingLens = lens
            val source = if (withRaw) RawSource.Physical(lens.physicalId) else RawSource.None
            rawSource = source
            rawChars = lensChars[lens.physicalId] ?: chars
            if (!withRaw) {
                raw?.close()
                rawReader = null
            }
            return ConfigureOutcome.Configured(source, notes.joinToString("; "))
        }
        streamingLens = null
        return ConfigureOutcome.Failed("lens ${lens.physicalId}: no stream combination configured (${notes.joinToString("; ")})")
    }

    /**
     * A zoom on the streaming lens: its own crop region where the phone takes one per lens (the S22), otherwise the logical
     * camera's zoom ratio, which the platform says a physical stream's view may not be narrower than (LOGICAL_MULTI_CAMERA;
     * the S26, where no per-lens key is listed). What the camera did is read back from each result ([cropAchieved]).
     */
    private fun applyCrop(builder: CaptureRequest.Builder, crop: LensCrop?) {
        crop ?: return
        val region = crop.region
        if (cropPerLens && region != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setPhysicalCameraKey(CaptureRequest.SCALER_CROP_REGION, android.graphics.Rect(region.left, region.top, region.right, region.bottom), crop.lensId)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val asked = crop.zoom.toFloat()
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, logicalZoomRange?.clamp(asked) ?: asked)
        }
    }

    @SuppressLint("NewApi") // only called with a lens crop, which only exists from API 28
    private fun newRequest(opened: CameraDevice, template: Int, crop: LensCrop?): CaptureRequest.Builder =
        if (crop != null && cropPerLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            opened.createCaptureRequest(template, setOf(crop.lensId))
        } else {
            opened.createCaptureRequest(template)
        }

    /** The streaming lens's own result, where the platform gives one. */
    private fun lensResult(result: TotalCaptureResult): CaptureResult? = streamingLens?.let { physicalResult(result, it.physicalId) }

    /** How far the lens was actually cropped: its active array's width over the crop region its own result reports. */
    private fun cropAchieved(result: TotalCaptureResult): Double? {
        val lens = streamingLens ?: return null
        val active = lens.activeArray ?: return null
        val region = lensResult(result)?.get(CaptureResult.SCALER_CROP_REGION) ?: return null
        return if (region.width() > 0) active.width.toDouble() / region.width() else null
    }

    private fun outputsFor(surface: Surface, jpeg: ImageReader, raw: ImageReader?, source: RawSource): List<OutputConfiguration> = buildList {
        add(OutputConfiguration(surface))
        add(OutputConfiguration(jpeg.surface))
        if (raw != null && source != RawSource.None) {
            val output = OutputConfiguration(raw.surface)
            if (source is RawSource.Physical && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) output.setPhysicalCameraId(source.physicalId)
            add(output)
        }
    }

    private fun querySupport(opened: CameraDevice, outputs: List<OutputConfiguration>): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "not asked (needs API 29)"
        return try {
            val config = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, CameraThread.executor, NoOpSessionCallback)
            if (opened.isSessionConfigurationSupported(config)) "supported" else "not supported"
        } catch (failure: Exception) {
            "unknown (${failure::class.java.simpleName}: ${failure.message})"
        }
    }

    @Suppress("DEPRECATION") // createCaptureSessionByOutputConfigurations is the API 26-27 form; SessionConfiguration from 28.
    private suspend fun createSession(opened: CameraDevice, outputs: List<OutputConfiguration>): CameraCaptureSession? {
        val closed = CompletableDeferred<Unit>()
        val result = withTimeoutOrNull(CONFIGURE_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<CameraCaptureSession?> { continuation ->
                val callback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (continuation.isActive) continuation.resume(session) else session.close()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onClosed(session: CameraCaptureSession) {
                        closed.complete(Unit)
                    }
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        opened.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, CameraThread.executor, callback))
                    } else {
                        opened.createCaptureSessionByOutputConfigurations(outputs, callback, handler)
                    }
                } catch (failure: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
        if (result != null) sessionClosed = closed
        return result
    }

    // ── Requests ──────────────────────────────────────────────────────────────────────────────────────────────────────

    private fun apply3A(builder: CaptureRequest.Builder, controls: PreviewControls) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        if (afContinuous) builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        val flash = flashRequest(controls.flash)
        builder.set(
            CaptureRequest.CONTROL_AE_MODE,
            when (flash.aeMode) {
                AeMode.On -> CaptureRequest.CONTROL_AE_MODE_ON
                AeMode.OnAutoFlash -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                AeMode.OnAlwaysFlash -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
            },
        )
        builder.set(CaptureRequest.FLASH_MODE, if (flash.torch) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AE_LOCK, controls.aeLock)
        builder.set(CaptureRequest.CONTROL_AWB_LOCK, controls.awbLock)
    }

    private fun previewRequest(controls: PreviewControls): CaptureRequest.Builder? {
        val opened = device ?: return null
        val surface = previewSurface ?: return null
        return newRequest(opened, CameraDevice.TEMPLATE_PREVIEW, controls.lensCrop).apply {
            addTarget(surface)
            apply3A(this, controls)
            applyCrop(this, controls.lensCrop)
        }
    }

    private val repeatingCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            latestResult = result
            val sink = frameSink ?: return
            val own = lensResult(result)
            val frame = PreviewFrame(
                sensorTimestampNanos = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: SystemClock.elapsedRealtimeNanos(),
                aeState = result.get(CaptureResult.CONTROL_AE_STATE),
                awbState = result.get(CaptureResult.CONTROL_AWB_STATE),
                activePhysicalId = activeId(result),
                focusDiopters = own?.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: result.get(CaptureResult.LENS_FOCUS_DISTANCE),
                afState = own?.get(CaptureResult.CONTROL_AF_STATE) ?: result.get(CaptureResult.CONTROL_AF_STATE),
                cropAchieved = cropAchieved(result),
            )
            main.post { sink(frame) }
        }
    }

    override fun startRepeating(controls: PreviewControls, onFrame: (PreviewFrame) -> Unit): Result<Unit> = runCatching {
        val running = session ?: error("no session is configured")
        val builder = previewRequest(controls) ?: error("no device or viewfinder surface")
        frameSink = onFrame
        running.setRepeatingRequest(builder.build(), repeatingCallback, handler)
        Unit
    }

    override suspend fun precapture(controls: PreviewControls, timeoutMillis: Long): PrecaptureOutcome {
        val running = session ?: return PrecaptureOutcome.Failed("no session")
        val builder = previewRequest(controls) ?: return PrecaptureOutcome.Failed("no device or viewfinder surface")
        builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
        val start = SystemClock.elapsedRealtime()
        runCatching { running.capture(builder.build(), null, handler) }.onFailure { return PrecaptureOutcome.Failed("the trigger was refused: $it") }
        // The sequence reads PRECAPTURE while it runs, then settles; a camera that never reports PRECAPTURE has settled
        // once a frame after the trigger reads converged, locked or flash-required.
        var sawPrecapture = false
        val settled = withTimeoutOrNull(timeoutMillis) {
            while (true) {
                val ae = latestResult?.get(CaptureResult.CONTROL_AE_STATE)
                if (ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE) sawPrecapture = true
                val waited = SystemClock.elapsedRealtime() - start
                val settledNow = ae != null && ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE && AE_SETTLED.contains(ae)
                if ((sawPrecapture || waited > PRECAPTURE_MIN_WAIT_MILLIS) && settledNow) break
                delay(POLL_MILLIS)
            }
        } != null
        val ae = latestResult?.get(CaptureResult.CONTROL_AE_STATE)
        return if (settled) PrecaptureOutcome.Settled(ae, SystemClock.elapsedRealtime() - start) else PrecaptureOutcome.TimedOut(ae)
    }

    override fun endPrecapture(controls: PreviewControls) {
        val running = session ?: return
        val builder = previewRequest(controls) ?: return
        builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL)
        runCatching { running.capture(builder.build(), null, handler) }
    }

    private fun stillRequest(still: StillControls, targets: List<Surface>): CaptureRequest? {
        val opened = device ?: return null
        return newRequest(opened, CameraDevice.TEMPLATE_STILL_CAPTURE, still.lensCrop).apply {
            applyCrop(this, still.lensCrop)
            targets.forEach { addTarget(it) }
            previewSurface?.let { addTarget(it) } // the viewfinder keeps drawing through the shot
            apply3A(this, PreviewControls(still.flash, aeLock = still.aeAwbLocked, awbLock = still.aeAwbLocked))
            set(CaptureRequest.JPEG_ORIENTATION, still.jpegOrientationDegrees)
            set(CaptureRequest.JPEG_QUALITY, JPEG_QUALITY)
            if (still.noiseReductionOff) set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
            if (lensShadingMapOn && targets.any { it == rawReader?.surface }) {
                set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON)
            }
        }.build()
    }

    override suspend fun captureJpeg(still: StillControls, destination: File): Result<JpegFrame> = runCatching {
        val running = session ?: error("no session")
        val reader = jpegReader ?: error("no JPEG stream")
        drain(reader)
        val request = stillRequest(still, listOf(reader.surface)) ?: error("no device")
        val shot = StillShot(expectRaw = false)
        reader.setOnImageAvailableListener({ r -> shot.onJpeg(r, destination) }, handler)
        running.capture(request, shot, handler)
        val done = withTimeoutOrNull(STILL_TIMEOUT_MILLIS) { shot.done.await() } ?: error("no JPEG within $STILL_TIMEOUT_MILLIS ms")
        reader.setOnImageAvailableListener(null, null)
        done.failure?.let { error(it) }
        JpegFrame(done.jpegWidth, done.jpegHeight, done.result?.let { activeId(it) }, done.result?.let { cropAchieved(it) })
    }

    override suspend fun captureRawJpeg(still: StillControls, jpegDestination: File): Result<RawJpegFrame> = runCatching {
        val running = session ?: error("no session")
        val jpeg = jpegReader ?: error("no JPEG stream")
        val raw = rawReader ?: error("no RAW stream")
        val source = rawSource
        drain(jpeg)
        drain(raw)
        val request = stillRequest(still, listOf(jpeg.surface, raw.surface)) ?: error("no device")
        val shot = StillShot(expectRaw = true)
        jpeg.setOnImageAvailableListener({ r -> shot.onJpeg(r, jpegDestination) }, handler)
        raw.setOnImageAvailableListener({ r -> shot.onRaw(r) }, handler)
        running.capture(request, shot, handler)
        val done = withTimeoutOrNull(STILL_TIMEOUT_MILLIS) { shot.done.await() } ?: error("no RAW and JPEG pair within $STILL_TIMEOUT_MILLIS ms")
        jpeg.setOnImageAvailableListener(null, null)
        raw.setOnImageAvailableListener(null, null)
        done.failure?.let { error(it) }
        val total = done.result
        val rawResult: CaptureResult? = when (source) {
            is RawSource.Physical -> total?.let { physicalResult(it, source.physicalId) }
            else -> total
        }
        val describing = rawChars ?: chars
        val samples = done.rawSamples
        val size = done.rawSize
        val unavailable = when {
            samples == null || size == null -> "the RAW frame could not be copied: ${done.rawCopyFailure}"
            rawResult == null -> if (source is RawSource.Physical) "no physical capture result for camera ${source.physicalId}" else "no capture result for the frame"
            describing == null -> "no characteristics for the RAW camera"
            else -> null
        }
        RawJpegFrame(
            jpeg = JpegFrame(done.jpegWidth, done.jpegHeight, total?.let { activeId(it) }, total?.let { cropAchieved(it) }),
            rawSource = source,
            rawActivePhysicalId = when (source) {
                is RawSource.Physical -> source.physicalId.takeIf { rawResult != null }
                else -> total?.let { activeId(it) }
            },
            noiseReductionModeReported = rawResult?.get(CaptureResult.NOISE_REDUCTION_MODE),
            readings = if (unavailable == null) readingsFor(describing!!, size!!, samples!!, rawResult!!) else null,
            readingsUnavailable = unavailable,
        )
    }

    override suspend fun lockExposure(controls: PreviewControls, timeoutMillis: Long): LockOutcome {
        val running = session ?: return LockOutcome.NotLocked("no session", null, null)
        val builder = previewRequest(controls) ?: return LockOutcome.NotLocked("no device or viewfinder surface", null, null)
        runCatching { running.setRepeatingRequest(builder.build(), repeatingCallback, handler) }
            .onFailure { return LockOutcome.NotLocked("the locked request was refused: $it", null, null) }
        var locked: TotalCaptureResult? = null
        withTimeoutOrNull(timeoutMillis) {
            while (true) {
                val r = latestResult
                if (r != null && r.get(CaptureResult.CONTROL_AE_STATE) == CaptureResult.CONTROL_AE_STATE_LOCKED &&
                    r.get(CaptureResult.CONTROL_AWB_STATE) == CaptureResult.CONTROL_AWB_STATE_LOCKED
                ) {
                    locked = r
                    break
                }
                delay(POLL_MILLIS)
            }
        }
        val last = latestResult
        val lockedResult = locked
        return if (lockedResult != null) {
            LockOutcome.Locked(lockedResult.get(CaptureResult.SENSOR_EXPOSURE_TIME), lockedResult.get(CaptureResult.SENSOR_SENSITIVITY))
        } else {
            LockOutcome.NotLocked("AE and AWB did not both read LOCKED within $timeoutMillis ms", last?.get(CaptureResult.CONTROL_AE_STATE), last?.get(CaptureResult.CONTROL_AWB_STATE))
        }
    }

    override suspend fun burst(count: Int, still: StillControls, timeoutMillis: Long): Result<BurstFrames> = runCatching {
        val running = session ?: error("no session")
        val raw = rawReader ?: error("no RAW stream")
        val source = rawSource
        drain(raw)
        val request = stillRequest(still, listOf(raw.surface)) ?: error("no device")
        val arrivals = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
        val results = Collections.synchronizedMap(HashMap<Long, TotalCaptureResult>())
        val failures = Collections.synchronizedList(mutableListOf<String>())
        // Each frame is closed as it arrives: the burst measures rate and timing, and its frames are discarded.
        raw.setOnImageAvailableListener({ r ->
            val at = SystemClock.elapsedRealtimeNanos()
            runCatching { r.acquireNextImage() }.getOrNull()?.let { image ->
                arrivals += image.timestamp to at
                image.close()
            }
        }, handler)
        val callback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                latestResult = result
                result.get(CaptureResult.SENSOR_TIMESTAMP)?.let { results[it] = result }
            }

            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                failures += "frame ${failure.frameNumber} failed (reason ${failure.reason}, image captured ${failure.wasImageCaptured()})"
            }

            override fun onCaptureBufferLost(session: CameraCaptureSession, request: CaptureRequest, target: Surface, frameNumber: Long) {
                if (target == raw.surface) failures += "frame $frameNumber's RAW buffer was lost"
            }
        }
        val submitted = SystemClock.elapsedRealtimeNanos()
        // captureBurst: "captured in the minimum amount of time possible, and will not be interleaved with requests
        // submitted by other capture or repeat calls" (CameraCaptureSession); exactly [count] frames, the probe's choice.
        running.captureBurst(List(count) { request }, callback, handler)
        withTimeoutOrNull(timeoutMillis) {
            while (arrivals.size + failures.size < count || results.size + failures.size < count) delay(POLL_MILLIS)
        }
        raw.setOnImageAvailableListener(null, null)
        val frames = synchronized(arrivals) { arrivals.toList() }.sortedBy { it.first }.map { (timestamp, at) ->
            val result = results[timestamp]
            val describing: CaptureResult? = when (source) {
                is RawSource.Physical -> result?.let { physicalResult(it, source.physicalId) }
                else -> result
            }
            BurstFrame(
                sensorTimestampNanos = timestamp,
                arrivedNanos = at,
                activePhysicalId = when (source) {
                    is RawSource.Physical -> source.physicalId.takeIf { describing != null }
                    else -> result?.let { activeId(it) }
                },
                noiseReductionMode = describing?.get(CaptureResult.NOISE_REDUCTION_MODE),
            )
        }
        BurstFrames(submittedNanos = submitted, frames = frames, failures = synchronized(failures) { failures.toList() })
    }

    // ── Close ─────────────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun closeSession() {
        closeSessionAndWait()
        previewSurface = null
    }

    private suspend fun closeSessionAndWait() {
        val running = session ?: return
        session = null
        frameSink = null
        runCatching { running.close() }
        withTimeoutOrNull(SESSION_CLOSE_TIMEOUT_MILLIS) { sessionClosed?.await() }
        sessionClosed = null
    }

    override suspend fun release(): ReleaseOutcome {
        closeSessionAndWait()
        previewSurface = null
        val opened = device
        device = null
        var closed = true
        var detail: String? = null
        if (opened != null) {
            runCatching { opened.close() }.onFailure { detail = "closing the device threw $it" }
            if (withTimeoutOrNull(DEVICE_CLOSE_TIMEOUT_MILLIS) { deviceClosed?.await() } == null) {
                closed = false
                detail = (detail?.let { "$it; " } ?: "") + "the device did not report onClosed within $DEVICE_CLOSE_TIMEOUT_MILLIS ms"
            }
        }
        closeReaders()
        rawSource = RawSource.None
        streamingLens = null
        latestResult = null
        return ReleaseOutcome(closed, detail)
    }

    private fun closeReaders() {
        runCatching { jpegReader?.close() }
        runCatching { rawReader?.close() }
        jpegReader = null
        rawReader = null
    }

    override fun watchAvailability(cameraId: String, onAvailable: () -> Unit): () -> Unit {
        var fired = false
        val callback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(id: String) {
                if (id != cameraId || fired) return
                fired = true
                manager.unregisterAvailabilityCallback(this)
                onAvailable()
            }
        }
        manager.registerAvailabilityCallback(callback, main)
        return { if (!fired) runCatching { manager.unregisterAvailabilityCallback(callback) } }
    }

    override fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()

    override fun heapUsedBytes(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    // ── Internals ─────────────────────────────────────────────────────────────────────────────────────────────────────

    /** One still's images and result, on the camera thread; [done] completes when everything expected has arrived or failed. */
    private inner class StillShot(private val expectRaw: Boolean) : CameraCaptureSession.CaptureCallback() {
        val done = CompletableDeferred<StillDone>()
        @Volatile private var result: TotalCaptureResult? = null
        @Volatile private var jpegSize: Pair<Int, Int>? = null
        @Volatile private var jpegFailure: String? = null
        @Volatile private var rawSamples: ShortArray? = null
        @Volatile private var rawSize: PixelSize? = null
        @Volatile private var rawFailure: String? = null
        @Volatile private var rawArrived = false

        fun onJpeg(reader: ImageReader, destination: File) {
            val image = runCatching { reader.acquireNextImage() }.getOrNull() ?: return
            try {
                writeJpeg(image, destination)
                jpegSize = image.width to image.height
            } catch (failure: Exception) {
                jpegFailure = "the JPEG could not be written: $failure"
            } finally {
                image.close()
            }
            finishIfComplete()
        }

        fun onRaw(reader: ImageReader) {
            val image = runCatching { reader.acquireNextImage() }.getOrNull() ?: return
            try {
                rawSamples = copyRawImage(image)
                rawSize = PixelSize(image.width, image.height)
            } catch (failure: Exception) {
                rawFailure = "$failure"
            } finally {
                image.close()
                rawArrived = true
            }
            finishIfComplete()
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            latestResult = result
            this.result = result
            finishIfComplete()
        }

        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
            done.complete(StillDone(failure = "the capture failed (reason ${failure.reason}, image captured ${failure.wasImageCaptured()})"))
        }

        private fun finishIfComplete() {
            jpegFailure?.let { done.complete(StillDone(failure = it)); return }
            val size = jpegSize ?: return
            if (result == null) return
            if (expectRaw && !rawArrived) return
            done.complete(
                StillDone(
                    result = result,
                    jpegWidth = size.first,
                    jpegHeight = size.second,
                    rawSamples = rawSamples,
                    rawSize = rawSize,
                    rawCopyFailure = rawFailure,
                ),
            )
        }
    }

    private class StillDone(
        val failure: String? = null,
        val result: TotalCaptureResult? = null,
        val jpegWidth: Int = 0,
        val jpegHeight: Int = 0,
        val rawSamples: ShortArray? = null,
        val rawSize: PixelSize? = null,
        val rawCopyFailure: String? = null,
    )

    private fun drain(reader: ImageReader) {
        while (true) {
            val stale = runCatching { reader.acquireNextImage() }.getOrNull() ?: return
            stale.close()
        }
    }

    private fun activeId(result: CaptureResult): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) else null

    @Suppress("DEPRECATION") // getPhysicalCameraResults is the API 28-30 form; getPhysicalCameraTotalResults from 31.
    private fun physicalResult(result: TotalCaptureResult, physicalId: String): CaptureResult? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> result.physicalCameraTotalResults[physicalId]
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> result.physicalCameraResults[physicalId]
        else -> null
    }

    private fun label(source: RawSource) = when (source) {
        RawSource.None -> "no RAW"
        RawSource.Logical -> "logical RAW"
        is RawSource.Physical -> "physical RAW on ${source.physicalId}"
    }

    private object NoOpSessionCallback : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) = Unit
        override fun onConfigureFailed(session: CameraCaptureSession) = Unit
    }

    companion object {
        const val JPEG_MAX_IMAGES = 2
        /** Room for a single shot and a burst whose frames are closed as they arrive. */
        const val RAW_MAX_IMAGES = 4
        /** CameraX's JPEG quality in MINIMIZE_LATENCY (`ImageCapture`'s default, 95). */
        const val JPEG_QUALITY: Byte = 95
        const val CONFIGURE_TIMEOUT_MILLIS = 3_000L
        const val STILL_TIMEOUT_MILLIS = 5_000L
        const val SESSION_CLOSE_TIMEOUT_MILLIS = 1_000L
        const val DEVICE_CLOSE_TIMEOUT_MILLIS = 3_000L
        const val PRECAPTURE_MIN_WAIT_MILLIS = 200L
        const val POLL_MILLIS = 10L

        val AE_SETTLED = setOf(
            CaptureResult.CONTROL_AE_STATE_CONVERGED,
            CaptureResult.CONTROL_AE_STATE_LOCKED,
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED,
        )

        fun deviceErrorName(error: Int): String = when (error) {
            CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "ERROR_CAMERA_IN_USE"
            CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "ERROR_MAX_CAMERAS_IN_USE"
            CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "ERROR_CAMERA_DISABLED"
            CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "ERROR_CAMERA_DEVICE"
            CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "ERROR_CAMERA_SERVICE"
            else -> "error $error"
        }
    }
}

/** A JPEG image's encoded bytes, plane 0, to [destination]. */
private fun writeJpeg(image: Image, destination: File) {
    check(image.format == ImageFormat.JPEG) { "not a JPEG image (format ${image.format})" }
    val buffer = image.planes.first().buffer.duplicate()
    buffer.rewind()
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    destination.parentFile?.mkdirs()
    destination.writeBytes(bytes)
}

/** A RAW_SENSOR image's one plane, row-major, honouring the row stride (the fast-burst probe's `copyRawImage`). */
private fun copyRawImage(image: Image): ShortArray {
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
    return samples
}

/** The DNG input's builder for one frame, called once the orientation is known. */
private fun readingsFor(chars: CameraCharacteristics, size: PixelSize, samples: ShortArray, result: CaptureResult): (Int) -> RawFrameReadings =
    { orientation -> rawReadings(chars, size, samples, result, orientation) }

/** Every value the DNG needs, read as main's `readRawFrame` reads them, from camera2's own types (the burst probe's twin). */
private fun rawReadings(chars: CameraCharacteristics, size: PixelSize, samples: ShortArray, result: CaptureResult, orientation: Int): RawFrameReadings {
    val shading = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP)
    return RawFrameReadings(
        width = size.width,
        height = size.height,
        samples = samples,
        colorFilterArrangement = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT),
        staticBlackLevel = chars.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)?.let { pattern -> IntArray(4).also { pattern.copyTo(it, 0) } },
        dynamicBlackLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL) else null,
        staticWhiteLevel = chars.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL),
        dynamicWhiteLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL) else null,
        preCorrectionActiveArray = chars.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)?.let { intArrayOf(it.left, it.top, it.right, it.bottom) },
        referenceIlluminant1 = chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1),
        referenceIlluminant2 = chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.toInt(),
        colorTransform1 = chars.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)?.toSRationals(),
        colorTransform2 = chars.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)?.toSRationals(),
        forwardMatrix1 = chars.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1)?.toSRationals(),
        forwardMatrix2 = chars.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2)?.toSRationals(),
        calibrationTransform1 = chars.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)?.toSRationals(),
        calibrationTransform2 = chars.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)?.toSRationals(),
        neutralColorPoint = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT)?.map { URational(it.numerator.toLong(), it.denominator.toLong()) },
        noiseProfile = result.get(CaptureResult.SENSOR_NOISE_PROFILE)?.map { it.first to it.second },
        lensShadingColumns = shading?.columnCount,
        lensShadingRows = shading?.rowCount,
        lensShadingGains = shading?.let { m -> FloatArray(m.gainFactorCount).also { m.copyGainFactors(it, 0) } },
        postRawSensitivityBoost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST),
        orientation = orientation,
    )
}

private fun ColorSpaceTransform.toSRationals(): List<SRational> {
    val elements = arrayOfNulls<Rational>(9)
    copyElements(elements, 0)
    return elements.map { r -> SRational(r!!.numerator, r.denominator) }
}

/**
 * The viewfinder's stream: a `TextureView`'s own SurfaceTexture, at the viewfinder size, and the Surface the session draws
 * into. Released only once both the session has stopped drawing into it ([release], from the engine) and the view has let
 * go of it ([onViewGone], from `onSurfaceTextureDestroyed`, which returns false so the view does not release it itself):
 * releasing it under either one still using it is the abandoned-surface failure.
 */
internal class Camera2PreviewTarget(private val texture: SurfaceTexture) : PreviewTarget {
    val surface: Surface = Surface(texture)

    /** The stream's buffer size: the lens chooser's lenses each configure at their own viewfinder size. */
    @Synchronized
    fun setBufferSize(width: Int, height: Int) {
        if (!released) texture.setDefaultBufferSize(width, height)
    }
    private var sessionDone = false
    private var viewDone = false
    private var released = false

    @Synchronized
    override fun release() {
        sessionDone = true
        releaseIfFree()
    }

    @Synchronized
    fun onViewGone() {
        viewDone = true
        releaseIfFree()
    }

    private fun releaseIfFree() {
        if (released || !sessionDone || !viewDone) return
        released = true
        surface.release()
        texture.release()
    }
}

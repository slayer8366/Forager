package com.zynergylabs.forager.app.photo.engine

import com.zynergylabs.forager.app.domain.CameraCapabilities
import com.zynergylabs.forager.app.domain.CameraFacing
import com.zynergylabs.forager.app.domain.ColorFilterArrangement
import com.zynergylabs.forager.app.domain.ExposureCompensation
import com.zynergylabs.forager.app.domain.ManualExposure
import com.zynergylabs.forager.app.domain.PixelRect
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.Probed
import com.zynergylabs.forager.app.domain.RawFrameReadings
import com.zynergylabs.forager.app.domain.raw.SRational
import com.zynergylabs.forager.app.domain.raw.URational
import com.zynergylabs.forager.app.photo.PhotoMetadataScrubTest
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A platform camera for the engine's tests. It models the one thing "dead" means for this engine: whether the device is
 * held ([holding]) after a path ends. Every call is recorded in [calls], in order, so a test can say what the engine asked
 * for and in what sequence (a lock before the burst, a precapture before a flash shot, no second open for a burst).
 *
 * Frames are posted on [scope], never delivered inside the engine's own call, as the real camera's arrive later on the
 * main thread. Stills write the 40x20 JPEG fixture with the HAL's NORMAL tag, so the engine's orientation reapply has a
 * real file to rewrite and the test reads the tag back from the file.
 */
internal class FakePlatformCamera(
    private val scope: CoroutineScope,
    var description: CameraDescription = CameraDescription.Described(facts()),
    var openOutcome: OpenOutcome = OpenOutcome.Opened,
    var configureOutcome: ConfigureOutcome = ConfigureOutcome.Configured(RawSource.Physical(MAIN_WIDE), "fake: physical RAW stream on $MAIN_WIDE"),
    /** Whether starting the repeating request produces a first frame. False models a session that never streams. */
    var streams: Boolean = true,
    var precaptureOutcome: PrecaptureOutcome = PrecaptureOutcome.Settled(PlatformStates.AE_CONVERGED, waitedMillis = 120),
    var lockOutcome: LockOutcome = LockOutcome.Locked(exposureTimeNanos = 16_666_666L, iso = 200),
) : PlatformCamera {

    val calls = mutableListOf<String>()
    var holding = false
        private set
    var sessionConfigured = false
        private set
    var clockNanos = 1_000_000_000L
    val repeating = mutableListOf<PreviewControls>()
    val stills = mutableListOf<StillControls>()
    var failNextCapture: String? = null
    var heapBytes = 64L shl 20

    /** How long the open takes on the fake clock, so the open-time line has a figure to report. */
    var openTakesNanos = 0L

    /** How long after the repeating request the first frame lands, on the fake clock. */
    var firstFrameAfterNanos = 0L

    /** When set, the open waits for it: an open still pending when the dialog closes. */
    var openGate: CompletableDeferred<Unit>? = null

    /** When set, a configure waits for it: a lens switch still under way (dispatch 2026-10-16-04). */
    var configureGate: CompletableDeferred<Unit>? = null

    /** Every stream plan asked for, in order: which lens each session carried. */
    val plans = mutableListOf<StreamPlan>()

    /** The lens the session carries, or null for the logical streams. */
    var configuredLens: Lens? = null
        private set

    /** What each viewfinder frame reports: the streaming lens's focus and AF state, and the crop the camera achieved. */
    var frameFocusDiopters: Float? = null
    var frameAfState: Int? = null
    var frameCropAchieved: Double? = null

    /** The zoom form and the request-and-result reading each frame reports (dispatch 2026-10-17-01). */
    var frameZoomFormUsed: ZoomForm? = null
    var frameZoomDiag: FrameZoomDiag? = null

    /** What each still's own result reports, as the frame fields above. */
    var stillCropAchieved: Double? = null
    var stillZoomFormUsed: ZoomForm? = null
    var stillZoomDiag: FrameZoomDiag? = null

    private var events: ((DeviceEvent) -> Unit)? = null
    private var onFrame: ((PreviewFrame) -> Unit)? = null
    private var availability: (() -> Unit)? = null
    private var frameCounter = 0L

    override fun describe(): CameraDescription {
        calls += "describe"
        return description
    }

    override suspend fun open(facts: CameraFacts, events: (DeviceEvent) -> Unit): OpenOutcome {
        calls += "open:${facts.cameraId}"
        this.events = events
        openGate?.await()
        clockNanos += openTakesNanos
        val outcome = openOutcome
        if (outcome == OpenOutcome.Opened) holding = true
        return outcome
    }

    override suspend fun configure(facts: CameraFacts, preview: PreviewTarget, plan: StreamPlan): ConfigureOutcome {
        calls += "configure:raw=${plan.raw}" + (plan.lens?.let { ":lens=${it.physicalId}" } ?: "")
        plans += plan
        configureGate?.await()
        val lens = plan.lens
        val outcome = configureOutcome.let { configured ->
            when {
                configured !is ConfigureOutcome.Configured -> configured
                !plan.raw -> ConfigureOutcome.Configured(RawSource.None, "fake: no RAW stream asked for")
                lens != null -> ConfigureOutcome.Configured(RawSource.Physical(lens.physicalId), "fake: lens ${lens.physicalId}'s physical streams")
                else -> configured
            }
        }
        configuredLens = if (outcome is ConfigureOutcome.Configured) lens else null
        sessionConfigured = outcome is ConfigureOutcome.Configured
        return outcome
    }

    override fun startRepeating(controls: PreviewControls, onFrame: (PreviewFrame) -> Unit): Result<Unit> {
        calls += "repeat:$controls"
        repeating += controls
        this.onFrame = onFrame
        if (streams) {
            scope.launch {
                clockNanos += firstFrameAfterNanos
                deliverFrame()
            }
        }
        return Result.success(Unit)
    }

    /** One viewfinder frame, 33.3 ms after the last, as the running repeating request would produce it. */
    fun deliverFrame(aeState: Int? = PlatformStates.AE_CONVERGED, activePhysicalId: String? = MAIN_WIDE) {
        frameCounter += 1
        onFrame?.invoke(
            PreviewFrame(
                sensorTimestampNanos = frameCounter * FRAME_NANOS,
                aeState = aeState,
                awbState = 2,
                activePhysicalId = activePhysicalId,
                focusDiopters = frameFocusDiopters,
                afState = frameAfState,
                cropAchieved = frameCropAchieved,
                zoomFormUsed = frameZoomFormUsed,
                zoomDiag = frameZoomDiag,
            ),
        )
    }

    override suspend fun precapture(controls: PreviewControls, timeoutMillis: Long): PrecaptureOutcome {
        calls += "precapture:${controls.flash}"
        return precaptureOutcome
    }

    override fun endPrecapture(controls: PreviewControls) {
        calls += "endPrecapture"
    }

    override suspend fun captureJpeg(still: StillControls, destination: File): Result<JpegFrame> {
        calls += "jpeg:${still.jpegOrientationDegrees}"
        stills += still
        failNextCapture?.let { reason ->
            failNextCapture = null
            return Result.failure(IllegalStateException(reason))
        }
        writeFixture(destination)
        return Result.success(stillFrame())
    }

    override suspend fun captureRawJpeg(still: StillControls, jpegDestination: File): Result<RawJpegFrame> {
        calls += "rawjpeg:${still.jpegOrientationDegrees}:nrOff=${still.noiseReductionOff}"
        stills += still
        failNextCapture?.let { reason ->
            failNextCapture = null
            return Result.failure(IllegalStateException(reason))
        }
        writeFixture(jpegDestination)
        return Result.success(
            RawJpegFrame(
                jpeg = stillFrame(),
                rawSource = RawSource.Physical(MAIN_WIDE),
                rawActivePhysicalId = MAIN_WIDE,
                noiseReductionModeReported = 0,
                readings = { orientation -> rawReadings(orientation) },
                readingsUnavailable = null,
            ),
        )
    }

    private fun stillFrame() = JpegFrame(JPEG.width, JPEG.height, MAIN_WIDE, stillCropAchieved, stillZoomFormUsed, stillZoomDiag)

    override suspend fun lockExposure(controls: PreviewControls, timeoutMillis: Long): LockOutcome {
        calls += "lock:ae=${controls.aeLock},awb=${controls.awbLock}"
        repeating += controls
        return lockOutcome
    }

    override suspend fun burst(count: Int, still: StillControls, timeoutMillis: Long): Result<BurstFrames> {
        calls += "burst:$count:locked=${still.aeAwbLocked}:nrOff=${still.noiseReductionOff}"
        stills += still
        val submitted = clockNanos
        val frames = List(count) { i ->
            BurstFrame(
                sensorTimestampNanos = 5_000_000_000L + i * FRAME_NANOS,
                arrivedNanos = submitted + 40_000_000L + i * FRAME_NANOS,
                activePhysicalId = MAIN_WIDE,
                noiseReductionMode = 0,
            )
        }
        return Result.success(BurstFrames(submittedNanos = submitted, frames = frames, failures = emptyList()))
    }

    override suspend fun closeSession() {
        calls += "closeSession"
        sessionConfigured = false
    }

    override suspend fun release(): ReleaseOutcome {
        calls += "release"
        holding = false
        sessionConfigured = false
        onFrame = null
        return ReleaseOutcome(closed = true)
    }

    override fun watchAvailability(cameraId: String, onAvailable: () -> Unit): () -> Unit {
        calls += "watchAvailability:$cameraId"
        availability = onAvailable
        return { availability = null }
    }

    /** Another app takes the camera: the platform's `onDisconnected`. The real camera drops the device; so does this. */
    fun disconnect() {
        holding = false
        sessionConfigured = false
        events?.invoke(DeviceEvent.Disconnected)
    }

    /** The other app lets it go. */
    fun becomeAvailable() {
        availability?.invoke()
    }

    override fun nowNanos(): Long = clockNanos

    override fun heapUsedBytes(): Long = heapBytes

    companion object {
        const val MAIN_WIDE = "5"
        const val FRAME_NANOS = 33_333_333L
        val JPEG = PixelSize(40, 20)

        fun writeFixture(file: File) {
            file.parentFile?.mkdirs()
            file.writeBytes(Base64.getDecoder().decode(PhotoMetadataScrubTest.JPEG_40X20_BASE64))
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                saveAttributes()
            }
        }

        fun capabilities(raw: Boolean = true): CameraCapabilities {
            val rawProbe: Probed<Unit> = if (raw) Probed.Supported(Unit, "fake") else Probed.Unsupported("fake: no RAW")
            return CameraCapabilities(
                facing = CameraFacing.Back,
                rawOutput = rawProbe,
                rawPlusJpegOutput = rawProbe,
                manualExposure = Probed.Supported(ManualExposure(10_000L..100_000_000L, 50..3200), "fake"),
                exposureCompensation = Probed.Supported(ExposureCompensation(-12, 12, 1, 6), "fake"),
                rawSizes = if (raw) Probed.Supported(listOf(PixelSize(4000, 3000)), "fake") else Probed.Unsupported("fake: no RAW"),
                pixelArray = Probed.Supported(PixelSize(4000, 3000), "fake"),
                activeArray = Probed.Supported(PixelRect(0, 0, 4000, 3000), "fake"),
                preCorrectionActiveArray = Probed.Supported(PixelRect(0, 0, 4000, 3000), "fake"),
                colorFilter = Probed.Supported(ColorFilterArrangement.GRBG, "fake"),
                physicalCameras = Probed.Supported(emptyList(), "fake"),
            )
        }

        /** [facts] with a lens set: the lens chooser's camera (dispatch 2026-10-16-04). */
        fun lensFacts(lenses: LensSet = LensFixtures.s26Set()) = facts().copy(lenses = lenses)

        fun facts(
            hasFlashUnit: Boolean = true,
            raw: Boolean = true,
            sensorOrientation: Int = 90,
            facing: CameraFacing = CameraFacing.Back,
        ) = CameraFacts(
            cameraId = "0",
            facing = facing,
            sensorOrientation = sensorOrientation,
            hasFlashUnit = hasFlashUnit,
            jpegSize = JPEG,
            previewSize = PixelSize(1440, 1080),
            rawSize = if (raw) PixelSize(4000, 3000) else null,
            mainWidePhysicalId = MAIN_WIDE,
            mainWideNote = "fake: the main wide is $MAIN_WIDE",
            noiseReductionOffOffered = true,
            capabilities = capabilities(raw),
        )

        private val identity = List(9) { SRational(if (it % 4 == 0) 1 else 0, 1) }

        /** The domain test's valid 8x6 frame (`RawFrameMappingTest`), at [orientation]. */
        fun rawReadings(orientation: Int) = RawFrameReadings(
            width = 8,
            height = 6,
            samples = ShortArray(48) { it.toShort() },
            colorFilterArrangement = 1,
            staticBlackLevel = intArrayOf(64, 65, 66, 67),
            dynamicBlackLevel = null,
            staticWhiteLevel = 1023,
            dynamicWhiteLevel = null,
            preCorrectionActiveArray = intArrayOf(0, 0, 8, 6),
            referenceIlluminant1 = 21,
            referenceIlluminant2 = 17,
            colorTransform1 = List(9) { SRational(it + 1, 1024) },
            colorTransform2 = List(9) { SRational(-(it + 1), 512) },
            forwardMatrix1 = identity,
            forwardMatrix2 = identity,
            calibrationTransform1 = identity,
            calibrationTransform2 = identity,
            neutralColorPoint = listOf(URational(1, 2), URational(1, 1), URational(2, 3)),
            noiseProfile = listOf(1e-5 to 2e-7, 3e-5 to 4e-7, 5e-5 to 6e-7, 7e-5 to 8e-7),
            lensShadingColumns = 2,
            lensShadingRows = 2,
            lensShadingGains = FloatArray(16) { 1f + it / 10f },
            postRawSensitivityBoost = 100,
            orientation = orientation,
        )
    }
}

/** A viewfinder stream for the tests. */
internal class FakePreviewTarget(val name: String) : PreviewTarget {
    var released = false
        private set

    override fun release() {
        released = true
    }
}

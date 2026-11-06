package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.CameraCapabilityFixtures.fullCamera2
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The capability probe's rules (dispatch 2026-10-13-04): every reading the camera library or the
 * platform can return, mapped to supported, unsupported or unknown with its reason. Plain JVM
 * tests: the rules are plain Kotlin, and Robolectric has no camera to read anyway.
 *
 * Each assertion is on the whole answer, reason text included, because the reason is the output:
 * it is what the device check reads to tell "the phone cannot" from "the probe could not tell".
 *
 * Grouped as the revert checks are: RAW, manual exposure, exposure compensation, geometry and
 * colour filter, physical cameras, encoders, and the log lines.
 */
class CameraCapabilityMappingTest {

    private fun readings(
        cameraXRaw: CameraReading<CameraXRawListing> = CameraReading.Read(CameraXRawListing(raw = true, rawJpeg = true)),
        camera2: CameraReading<Camera2Readings> = CameraReading.Read(fullCamera2),
        exposureState: CameraReading<ExposureStateReading> = CameraReading.Read(ExposureStateReading(true, -12, 12, 1, 6)),
        physicalCameras: CameraReading<List<PhysicalCameraReadings>> = CameraReading.Read(emptyList()),
    ) = CameraCapabilityReadings(CameraFacing.Back, cameraXRaw, camera2, exposureState, physicalCameras)

    private fun camera2(edit: Camera2Readings.() -> Camera2Readings): CameraReading<Camera2Readings> = CameraReading.Read(fullCamera2.edit())

    private val notListed = CameraReading.Read(CameraXRawListing(raw = false, rawJpeg = false))

    // ── RAW ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `RAW that CameraX lists is supported, whatever camera2 says`() {
        val probed = mapCameraCapabilities(readings(camera2 = CameraReading.Failed("IllegalArgumentException: no")))

        assertEquals(Probed.Supported(Unit, "CameraX lists the output format"), probed.rawOutput)
        assertEquals(Probed.Supported(Unit, "CameraX lists the output format"), probed.rawPlusJpegOutput)
    }

    @Test
    fun `RAW and RAW+JPEG each follow their own entry in CameraX's listing`() {
        val probed = mapCameraCapabilities(
            readings(
                cameraXRaw = CameraReading.Read(CameraXRawListing(raw = true, rawJpeg = false)),
                camera2 = camera2 { copy(capabilities = listOf(0)) },
            ),
        )

        assertEquals(Probed.Supported(Unit, "CameraX lists the output format"), probed.rawOutput)
        assertEquals(
            Probed.Unsupported("CameraX lists no RAW output, and the camera does not list the RAW capability"),
            probed.rawPlusJpegOutput,
        )
    }

    @Test
    fun `RAW is unknown when CameraX's output formats cannot be read`() {
        val probed = mapCameraCapabilities(readings(cameraXRaw = CameraReading.Failed("IllegalStateException: gone")))

        assertEquals(Probed.Unknown("CameraX's output formats could not be read: IllegalStateException: gone"), probed.rawOutput)
    }

    @Test
    fun `RAW that CameraX does not list is unknown when camera2 cannot be read to confirm it`() {
        val probed = mapCameraCapabilities(readings(cameraXRaw = notListed, camera2 = CameraReading.Failed("IllegalArgumentException: no")))

        assertEquals(
            Probed.Unknown("CameraX lists no RAW output, and camera2 could not be read to confirm it: IllegalArgumentException: no"),
            probed.rawOutput,
        )
    }

    @Test
    fun `RAW that CameraX does not list is unknown when the camera reports no capability list`() {
        val probed = mapCameraCapabilities(readings(cameraXRaw = notListed, camera2 = camera2 { copy(capabilities = null) }))

        assertEquals(
            Probed.Unknown("CameraX lists no RAW output, and the camera reports no capability list to confirm it"),
            probed.rawOutput,
        )
    }

    @Test
    fun `RAW that CameraX does not list but camera2 does is unknown, not unsupported`() {
        val probed = mapCameraCapabilities(readings(cameraXRaw = notListed, camera2 = camera2 { copy(capabilities = listOf(0, CAPABILITY_RAW)) }))

        assertEquals(Probed.Unknown("CameraX lists no RAW output, but the camera lists the RAW capability"), probed.rawOutput)
    }

    @Test
    fun `RAW that neither CameraX nor camera2 lists is unsupported`() {
        val probed = mapCameraCapabilities(readings(cameraXRaw = notListed, camera2 = camera2 { copy(capabilities = listOf(0, CAPABILITY_MANUAL_SENSOR)) }))

        assertEquals(
            Probed.Unsupported("CameraX lists no RAW output, and the camera does not list the RAW capability"),
            probed.rawOutput,
        )
    }

    // ── Manual exposure ──────────────────────────────────────────────────────────────────────

    @Test
    fun `manual exposure with MANUAL_SENSOR and both ranges is supported with the ranges`() {
        assertEquals(
            Probed.Supported(
                ManualExposure(10_000L..30_000_000_000L, 50..3_200),
                "the camera lists MANUAL_SENSOR with exposure-time and sensitivity ranges",
            ),
            mapCameraCapabilities(readings()).manualExposure,
        )
    }

    @Test
    fun `manual exposure is unknown when camera2 cannot be read`() {
        assertEquals(
            Probed.Unknown("camera2 could not be read: IllegalArgumentException: no"),
            mapCameraCapabilities(readings(camera2 = CameraReading.Failed("IllegalArgumentException: no"))).manualExposure,
        )
    }

    @Test
    fun `manual exposure is unknown when the camera reports no capability list`() {
        assertEquals(
            Probed.Unknown("the camera reports no capability list"),
            mapCameraCapabilities(readings(camera2 = camera2 { copy(capabilities = null) })).manualExposure,
        )
    }

    @Test
    fun `manual exposure is unsupported when MANUAL_SENSOR is not listed`() {
        assertEquals(
            Probed.Unsupported("the camera does not list MANUAL_SENSOR"),
            mapCameraCapabilities(readings(camera2 = camera2 { copy(capabilities = listOf(0, CAPABILITY_RAW)) })).manualExposure,
        )
    }

    @Test
    fun `manual exposure is unknown when MANUAL_SENSOR is listed without an exposure-time range`() {
        assertEquals(
            Probed.Unknown("MANUAL_SENSOR is listed but no exposure-time range is reported"),
            mapCameraCapabilities(readings(camera2 = camera2 { copy(exposureTimeRangeNanos = null) })).manualExposure,
        )
    }

    @Test
    fun `manual exposure is unknown when MANUAL_SENSOR is listed without a sensitivity range`() {
        assertEquals(
            Probed.Unknown("MANUAL_SENSOR is listed but no sensitivity range is reported"),
            mapCameraCapabilities(readings(camera2 = camera2 { copy(sensitivityRange = null) })).manualExposure,
        )
    }

    @Test
    fun `manual exposure is unknown when the exposure-time range is inverted`() {
        assertEquals(
            Probed.Unknown("MANUAL_SENSOR is listed but the exposure-time range 500..100 ns is not a range"),
            mapCameraCapabilities(readings(camera2 = camera2 { copy(exposureTimeRangeNanos = 500L to 100L) })).manualExposure,
        )
    }

    @Test
    fun `manual exposure is unknown when the sensitivity range starts at zero`() {
        assertEquals(
            Probed.Unknown("MANUAL_SENSOR is listed but the sensitivity range 0..800 is not a range"),
            mapCameraCapabilities(readings(camera2 = camera2 { copy(sensitivityRange = 0 to 800) })).manualExposure,
        )
    }

    // ── Exposure compensation, the HDR fallback ──────────────────────────────────────────────

    @Test
    fun `exposure compensation CameraX reports with a range and a step is supported`() {
        assertEquals(
            Probed.Supported(ExposureCompensation(-12, 12, 1, 6), "CameraX's exposure state"),
            mapCameraCapabilities(readings()).exposureCompensation,
        )
    }

    @Test
    fun `exposure compensation is unknown when CameraX's exposure state cannot be read`() {
        assertEquals(
            Probed.Unknown("CameraX's exposure state could not be read: IllegalStateException: gone"),
            mapCameraCapabilities(readings(exposureState = CameraReading.Failed("IllegalStateException: gone"))).exposureCompensation,
        )
    }

    @Test
    fun `exposure compensation CameraX reports unsupported is unsupported`() {
        assertEquals(
            Probed.Unsupported("CameraX reports exposure compensation unsupported"),
            mapCameraCapabilities(readings(exposureState = CameraReading.Read(ExposureStateReading(false, 0, 0, 0, 1)))).exposureCompensation,
        )
    }

    @Test
    fun `exposure compensation reported supported with a zero step is unknown`() {
        assertEquals(
            Probed.Unknown("CameraX reports exposure compensation supported with a step of 0/1"),
            mapCameraCapabilities(readings(exposureState = CameraReading.Read(ExposureStateReading(true, -12, 12, 0, 1)))).exposureCompensation,
        )
    }

    @Test
    fun `exposure compensation reported supported with an empty range is unknown`() {
        assertEquals(
            Probed.Unknown("CameraX reports exposure compensation supported with an empty range 0..0"),
            mapCameraCapabilities(readings(exposureState = CameraReading.Read(ExposureStateReading(true, 0, 0, 1, 3)))).exposureCompensation,
        )
    }

    // ── Geometry and colour filter ───────────────────────────────────────────────────────────

    @Test
    fun `RAW sizes are supported largest first`() {
        assertEquals(
            Probed.Supported(listOf(PixelSize(4_000, 3_000), PixelSize(2_000, 1_500)), "the stream configuration map's RAW_SENSOR sizes"),
            mapCameraCapabilities(readings()).rawSizes,
        )
    }

    @Test
    fun `RAW sizes are unsupported when the map lists none`() {
        assertEquals(
            Probed.Unsupported("the stream configuration map lists no RAW_SENSOR sizes"),
            mapCameraCapabilities(readings(camera2 = camera2 { copy(rawSensorOutputSizes = emptyList()) })).rawSizes,
        )
    }

    @Test
    fun `RAW sizes are unknown when there is no stream configuration map`() {
        assertEquals(
            Probed.Unknown("the camera reports no stream configuration map"),
            mapCameraCapabilities(readings(camera2 = camera2 { copy(rawSensorOutputSizes = null) })).rawSizes,
        )
    }

    @Test
    fun `geometry is unknown, every field, when camera2 cannot be read`() {
        val probed = mapCameraCapabilities(readings(camera2 = CameraReading.Failed("IllegalArgumentException: no")))
        val unreadable = Probed.Unknown("camera2 could not be read: IllegalArgumentException: no")

        assertEquals(unreadable, probed.rawSizes)
        assertEquals(unreadable, probed.pixelArray)
        assertEquals(unreadable, probed.activeArray)
        assertEquals(unreadable, probed.preCorrectionActiveArray)
        assertEquals(unreadable, probed.colorFilter)
    }

    @Test
    fun `the sensor's arrays are supported as reported`() {
        val probed = mapCameraCapabilities(readings())

        assertEquals(Probed.Supported(PixelSize(4_080, 3_072), "the camera's characteristics"), probed.pixelArray)
        assertEquals(Probed.Supported(PixelRect(8, 8, 4_072, 3_064), "the camera's characteristics"), probed.activeArray)
        assertEquals(Probed.Supported(PixelRect(0, 0, 4_080, 3_072), "the camera's characteristics"), probed.preCorrectionActiveArray)
    }

    @Test
    fun `a sensor array the camera does not report is unknown, not unsupported`() {
        val probed = mapCameraCapabilities(readings(camera2 = camera2 { copy(pixelArray = null, activeArray = null, preCorrectionActiveArray = null) }))

        assertEquals(Probed.Unknown("the camera does not report its pixel array size"), probed.pixelArray)
        assertEquals(Probed.Unknown("the camera does not report its active array"), probed.activeArray)
        assertEquals(Probed.Unknown("the camera does not report its pre-correction active array"), probed.preCorrectionActiveArray)
    }

    @Test
    fun `every colour filter code the platform defines maps to its arrangement`() {
        val expected = listOf(
            0 to ColorFilterArrangement.RGGB,
            1 to ColorFilterArrangement.GRBG,
            2 to ColorFilterArrangement.GBRG,
            3 to ColorFilterArrangement.BGGR,
            4 to ColorFilterArrangement.RGB,
            5 to ColorFilterArrangement.Mono,
            6 to ColorFilterArrangement.NearInfrared,
        )
        for ((code, arrangement) in expected) {
            assertEquals(
                "code $code",
                Probed.Supported(arrangement, "the camera's characteristics"),
                mapCameraCapabilities(readings(camera2 = camera2 { copy(colorFilterArrangement = code) })).colorFilter,
            )
        }
    }

    @Test
    fun `a colour filter code the platform does not define is unknown`() {
        assertEquals(
            Probed.Unknown("the camera reports an unrecognised colour filter arrangement 7"),
            mapCameraCapabilities(readings(camera2 = camera2 { copy(colorFilterArrangement = 7) })).colorFilter,
        )
    }

    @Test
    fun `a colour filter the camera does not report is unknown`() {
        assertEquals(
            Probed.Unknown("the camera does not report its colour filter arrangement"),
            mapCameraCapabilities(readings(camera2 = camera2 { copy(colorFilterArrangement = null) })).colorFilter,
        )
    }

    // ── Physical cameras ─────────────────────────────────────────────────────────────────────

    @Test
    fun `physical cameras are unknown when they cannot be read`() {
        assertEquals(
            Probed.Unknown("the physical cameras could not be read: IllegalStateException: gone"),
            mapCameraCapabilities(readings(physicalCameras = CameraReading.Failed("IllegalStateException: gone"))).physicalCameras,
        )
    }

    @Test
    fun `a camera with no physical cameras reports unsupported`() {
        assertEquals(
            Probed.Unsupported("the camera reports no physical cameras"),
            mapCameraCapabilities(readings(physicalCameras = CameraReading.Read(emptyList()))).physicalCameras,
        )
    }

    @Test
    fun `each physical camera reports its id, RAW, MANUAL_SENSOR and RAW sizes from camera2`() {
        val physical = listOf(
            PhysicalCameraReadings(CameraReading.Read("2"), CameraReading.Read(fullCamera2)),
            PhysicalCameraReadings(
                CameraReading.Read("3"),
                CameraReading.Read(fullCamera2.copy(capabilities = listOf(0, CAPABILITY_MANUAL_SENSOR), rawSensorOutputSizes = emptyList())),
            ),
            PhysicalCameraReadings(CameraReading.Failed("IllegalArgumentException: id"), CameraReading.Failed("IllegalArgumentException: no")),
            PhysicalCameraReadings(CameraReading.Read("5"), CameraReading.Read(fullCamera2.copy(capabilities = null))),
        )

        assertEquals(
            Probed.Supported(
                listOf(
                    PhysicalCameraCapabilities(
                        cameraId = Probed.Supported("2", "camera2's camera id"),
                        raw = Probed.Supported(Unit, "the camera lists RAW"),
                        manualSensor = Probed.Supported(Unit, "the camera lists MANUAL_SENSOR"),
                        rawSizes = Probed.Supported(listOf(PixelSize(4_000, 3_000), PixelSize(2_000, 1_500)), "the stream configuration map's RAW_SENSOR sizes"),
                    ),
                    PhysicalCameraCapabilities(
                        cameraId = Probed.Supported("3", "camera2's camera id"),
                        raw = Probed.Unsupported("the camera does not list RAW"),
                        manualSensor = Probed.Supported(Unit, "the camera lists MANUAL_SENSOR"),
                        rawSizes = Probed.Unsupported("the stream configuration map lists no RAW_SENSOR sizes"),
                    ),
                    PhysicalCameraCapabilities(
                        cameraId = Probed.Unknown("the camera id could not be read: IllegalArgumentException: id"),
                        raw = Probed.Unknown("camera2 could not be read: IllegalArgumentException: no"),
                        manualSensor = Probed.Unknown("camera2 could not be read: IllegalArgumentException: no"),
                        rawSizes = Probed.Unknown("camera2 could not be read: IllegalArgumentException: no"),
                    ),
                    PhysicalCameraCapabilities(
                        cameraId = Probed.Supported("5", "camera2's camera id"),
                        raw = Probed.Unknown("the camera reports no capability list"),
                        manualSensor = Probed.Unknown("the camera reports no capability list"),
                        rawSizes = Probed.Supported(listOf(PixelSize(4_000, 3_000), PixelSize(2_000, 1_500)), "the stream configuration map's RAW_SENSOR sizes"),
                    ),
                ),
                "the camera's physical cameras",
            ),
            mapCameraCapabilities(readings(physicalCameras = CameraReading.Read(physical))).physicalCameras,
        )
    }

    // ── Encoders ─────────────────────────────────────────────────────────────────────────────

    private val goodPng = CameraReading.Read(PngProbeReading(compressReturned = true, bytesWritten = 68, pngSignature = true))

    private fun encoders(
        sdkInt: Int = 36,
        mimeTypes: CameraReading<Set<String>> = CameraReading.Read(setOf("video/avc", MIME_HEVC_VIDEO, MIME_HEIC_IMAGE)),
        png: CameraReading<PngProbeReading> = goodPng,
    ) = mapEncoderCapabilities(EncoderReadings(sdkInt, mimeTypes, png))

    @Test
    fun `HEIF is supported when an encoder lists the HEIC image type`() {
        assertEquals(Probed.Supported(Unit, "an encoder lists image/vnd.android.heic"), encoders().heif)
    }

    @Test
    fun `HEIF is unsupported below API 28 even when an encoder lists the type`() {
        assertEquals(Probed.Unsupported("Android 27 has no HEIC image type; it arrived in API 28"), encoders(sdkInt = 27).heif)
    }

    @Test
    fun `HEIF at API 28 is decided by the encoder list`() {
        assertEquals(Probed.Supported(Unit, "an encoder lists image/vnd.android.heic"), encoders(sdkInt = 28).heif)
        assertEquals(
            Probed.Unsupported("no encoder lists image/vnd.android.heic"),
            encoders(sdkInt = 28, mimeTypes = CameraReading.Read(setOf("video/avc", MIME_HEVC_VIDEO))).heif,
        )
    }

    @Test
    fun `HEIF and HEVC are unknown when the encoder list cannot be read`() {
        val probed = encoders(mimeTypes = CameraReading.Failed("RuntimeException: codecs"))

        assertEquals(Probed.Unknown("the encoder list could not be read: RuntimeException: codecs"), probed.heif)
        assertEquals(Probed.Unknown("the encoder list could not be read: RuntimeException: codecs"), probed.hevcVideo)
    }

    @Test
    fun `the HEVC video encoder is reported on its own`() {
        assertEquals(Probed.Supported(Unit, "an encoder lists video/hevc"), encoders(mimeTypes = CameraReading.Read(setOf(MIME_HEVC_VIDEO))).hevcVideo)
        assertEquals(Probed.Unsupported("no encoder lists video/hevc"), encoders(mimeTypes = CameraReading.Read(setOf(MIME_HEIC_IMAGE))).hevcVideo)
    }

    @Test
    fun `PNG is supported on the evidence of a real compression`() {
        assertEquals(Probed.Supported(Unit, "a 1x1 bitmap compressed to 68 bytes with a PNG signature"), encoders().png)
    }

    @Test
    fun `PNG is unsupported when compression returns false`() {
        assertEquals(
            Probed.Unsupported("compressing a 1x1 bitmap to PNG returned false"),
            encoders(png = CameraReading.Read(PngProbeReading(compressReturned = false, bytesWritten = 0, pngSignature = false))).png,
        )
    }

    @Test
    fun `PNG is unknown when compression returns true but writes no PNG`() {
        assertEquals(
            Probed.Unknown("compressing a 1x1 bitmap to PNG returned true but wrote 12 bytes without a PNG signature"),
            encoders(png = CameraReading.Read(PngProbeReading(compressReturned = true, bytesWritten = 12, pngSignature = false))).png,
        )
    }

    @Test
    fun `PNG is unknown when compression throws`() {
        assertEquals(
            Probed.Unknown("compressing a 1x1 bitmap to PNG threw: IllegalStateException: recycled"),
            encoders(png = CameraReading.Failed("IllegalStateException: recycled")).png,
        )
    }

    // ── Log lines ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the bound camera logs one line, then one per physical camera, each with the probe id and facing`() {
        assertEquals(
            listOf(
                "probe=4 facing=back raw=supported(CameraX lists the output format) rawJpeg=supported(CameraX lists the output format) " +
                    "manualExposure=supported(exposure 10000..30000000000 ns, ISO 50..3200) evComp=supported(-12..12 step 1/6 EV) " +
                    "rawSizes=supported(4000x3000 2000x1500) pixelArray=supported(4080x3072) activeArray=supported(8,8,4072,3064) " +
                    "preCorrectionArray=supported(0,0,4080,3072) cfa=supported(GRBG) physical=supported(2)",
                "probe=4 facing=back physical[0] id=supported(2) raw=supported(the camera lists RAW) " +
                    "manualSensor=supported(the camera lists MANUAL_SENSOR) rawSizes=supported(4000x3000)",
                "probe=4 facing=back physical[1] id=supported(3) raw=unsupported(the camera does not list RAW) " +
                    "manualSensor=unknown(the camera reports no capability list) rawSizes=unsupported(the stream configuration map lists no RAW_SENSOR sizes)",
            ),
            CameraCapabilityFixtures.everythingSupported.toLogLines(probeId = 4, bound = true),
        )
    }

    @Test
    fun `an unbound camera's line says so, and a no or an unknown shows its reason`() {
        assertEquals(
            listOf(
                "probe=4 facing=front unbound raw=unsupported(CameraX lists no RAW output, and the camera does not list the RAW capability) " +
                    "rawJpeg=unknown(CameraX lists no RAW output, but the camera lists the RAW capability) " +
                    "manualExposure=unsupported(the camera does not list MANUAL_SENSOR) " +
                    "evComp=unsupported(CameraX reports exposure compensation unsupported) " +
                    "rawSizes=unsupported(the stream configuration map lists no RAW_SENSOR sizes) " +
                    "pixelArray=unknown(the camera does not report its pixel array size) " +
                    "activeArray=unknown(camera2 could not be read: IllegalArgumentException: no) " +
                    "preCorrectionArray=unknown(the camera does not report its pre-correction active array) " +
                    "cfa=unknown(the camera reports an unrecognised colour filter arrangement 9) " +
                    "physical=unsupported(the camera reports no physical cameras)",
            ),
            CameraCapabilityFixtures.nothingSupported.copy(facing = CameraFacing.Front).toLogLines(probeId = 4, bound = false),
        )
    }

    @Test
    fun `the encoder line carries the probe id and facing it is matched by`() {
        assertEquals(
            "probe=4 facing=back encoders heif=supported(an encoder lists image/vnd.android.heic) " +
                "hevcVideo=supported(an encoder lists video/hevc) png=supported(a 1x1 bitmap compressed to 68 bytes with a PNG signature)",
            CameraCapabilityFixtures.everyEncoder.toLogLine(probeId = 4, facing = CameraFacing.Back),
        )
    }
}

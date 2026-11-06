package com.zynergylabs.forager.app.domain

/**
 * Readings and probe results the capability tests share (dispatch 2026-10-13-04). The values are
 * plausible for a phone camera, not copied from any phone: what a real phone reports is the device
 * check's job.
 */
internal object CameraCapabilityFixtures {

    /** A camera2 read with every key the probe reads reported, and both capabilities listed. */
    val fullCamera2 = Camera2Readings(
        capabilities = listOf(0, CAPABILITY_MANUAL_SENSOR, CAPABILITY_RAW),
        exposureTimeRangeNanos = 10_000L to 30_000_000_000L,
        sensitivityRange = 50 to 3_200,
        pixelArray = PixelSize(4_080, 3_072),
        activeArray = PixelRect(8, 8, 4_072, 3_064),
        preCorrectionActiveArray = PixelRect(0, 0, 4_080, 3_072),
        colorFilterArrangement = 1,
        rawSensorOutputSizes = listOf(PixelSize(2_000, 1_500), PixelSize(4_000, 3_000)),
    )

    val everythingSupported = CameraCapabilities(
        facing = CameraFacing.Back,
        rawOutput = Probed.Supported(Unit, "CameraX lists the output format"),
        rawPlusJpegOutput = Probed.Supported(Unit, "CameraX lists the output format"),
        manualExposure = Probed.Supported(
            ManualExposure(10_000L..30_000_000_000L, 50..3_200),
            "the camera lists MANUAL_SENSOR with exposure-time and sensitivity ranges",
        ),
        exposureCompensation = Probed.Supported(ExposureCompensation(-12, 12, 1, 6), "CameraX's exposure state"),
        rawSizes = Probed.Supported(listOf(PixelSize(4_000, 3_000), PixelSize(2_000, 1_500)), "the stream configuration map's RAW_SENSOR sizes"),
        pixelArray = Probed.Supported(PixelSize(4_080, 3_072), "the camera's characteristics"),
        activeArray = Probed.Supported(PixelRect(8, 8, 4_072, 3_064), "the camera's characteristics"),
        preCorrectionActiveArray = Probed.Supported(PixelRect(0, 0, 4_080, 3_072), "the camera's characteristics"),
        colorFilter = Probed.Supported(ColorFilterArrangement.GRBG, "the camera's characteristics"),
        physicalCameras = Probed.Supported(
            listOf(
                PhysicalCameraCapabilities(
                    cameraId = Probed.Supported("2", "camera2's camera id"),
                    raw = Probed.Supported(Unit, "the camera lists RAW"),
                    manualSensor = Probed.Supported(Unit, "the camera lists MANUAL_SENSOR"),
                    rawSizes = Probed.Supported(listOf(PixelSize(4_000, 3_000)), "the stream configuration map's RAW_SENSOR sizes"),
                ),
                PhysicalCameraCapabilities(
                    cameraId = Probed.Supported("3", "camera2's camera id"),
                    raw = Probed.Unsupported("the camera does not list RAW"),
                    manualSensor = Probed.Unknown("the camera reports no capability list"),
                    rawSizes = Probed.Unsupported("the stream configuration map lists no RAW_SENSOR sizes"),
                ),
            ),
            "the camera's physical cameras",
        ),
    )

    val nothingSupported = CameraCapabilities(
        facing = CameraFacing.Back,
        rawOutput = Probed.Unsupported("CameraX lists no RAW output, and the camera does not list the RAW capability"),
        rawPlusJpegOutput = Probed.Unknown("CameraX lists no RAW output, but the camera lists the RAW capability"),
        manualExposure = Probed.Unsupported("the camera does not list MANUAL_SENSOR"),
        exposureCompensation = Probed.Unsupported("CameraX reports exposure compensation unsupported"),
        rawSizes = Probed.Unsupported("the stream configuration map lists no RAW_SENSOR sizes"),
        pixelArray = Probed.Unknown("the camera does not report its pixel array size"),
        activeArray = Probed.Unknown("camera2 could not be read: IllegalArgumentException: no"),
        preCorrectionActiveArray = Probed.Unknown("the camera does not report its pre-correction active array"),
        colorFilter = Probed.Unknown("the camera reports an unrecognised colour filter arrangement 9"),
        physicalCameras = Probed.Unsupported("the camera reports no physical cameras"),
    )

    val everyEncoder = EncoderCapabilities(
        heif = Probed.Supported(Unit, "an encoder lists image/vnd.android.heic"),
        hevcVideo = Probed.Supported(Unit, "an encoder lists video/hevc"),
        png = Probed.Supported(Unit, "a 1x1 bitmap compressed to 68 bytes with a PNG signature"),
    )

    val noEncoder = EncoderCapabilities(
        heif = Probed.Unsupported("Android 26 has no HEIC image type; it arrived in API 28"),
        hevcVideo = Probed.Unknown("the encoder list could not be read: IllegalStateException: no"),
        png = Probed.Unsupported("compressing a 1x1 bitmap to PNG returned false"),
    )
}

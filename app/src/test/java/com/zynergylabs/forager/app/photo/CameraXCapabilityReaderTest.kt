package com.zynergylabs.forager.app.photo

import android.util.Range
import android.util.Rational
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ExposureState
import androidx.camera.core.ZoomState
import androidx.lifecycle.LiveData
import com.zynergylabs.forager.app.domain.CameraCapabilityReadings
import com.zynergylabs.forager.app.domain.CameraFacing
import com.zynergylabs.forager.app.domain.CameraReading
import com.zynergylabs.forager.app.domain.CameraXRawListing
import com.zynergylabs.forager.app.domain.ExposureStateReading
import com.zynergylabs.forager.app.domain.Probed
import com.zynergylabs.forager.app.domain.mapCameraCapabilities
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real reader, [readCameraCapabilities], against the real CameraX 1.6.2 classes, with a stub
 * camera-info object in place of a camera (dispatch 2026-10-13-04, planner's item 7).
 *
 * What it reaches is narrow and real: a camera-info object CameraX cannot see inside. That is the
 * case where CameraX's own capabilities call answers "no RAW" silently (it checks the object's
 * type before anything else; read from the pinned artifact) and `Camera2CameraInfo.from` throws.
 * This shows the reader keeps both as they are, and that the rules turn the pair into an unknown
 * rather than an unsupported. The stub throws from every member the probe should not touch, so a
 * reader that started reading something else would surface here as a failed read.
 *
 * What it cannot reach: any value a real camera reports. That is the device check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraXCapabilityReaderTest {

    private val unwrapFailure =
        "IllegalArgumentException: Could not unwrap StubCameraInfo as Camera2CameraInfo!"

    @Test
    fun `a camera-info object CameraX cannot see inside reads as listed-no and camera2-failed`() {
        assertEquals(
            CameraCapabilityReadings(
                facing = CameraFacing.Back,
                cameraXRaw = CameraReading.Read(CameraXRawListing(raw = false, rawJpeg = false)),
                camera2 = CameraReading.Failed(unwrapFailure),
                exposureState = CameraReading.Read(ExposureStateReading(supported = true, lower = -12, upper = 12, stepNumerator = 1, stepDenominator = 6)),
                physicalCameras = CameraReading.Read(emptyList()),
            ),
            readCameraCapabilities(StubCameraInfo(), CameraFacing.Back),
        )
    }

    @Test
    fun `CameraX's silent no becomes an unknown with camera2's reason, not an unsupported`() {
        val probed = mapCameraCapabilities(readCameraCapabilities(StubCameraInfo(), CameraFacing.Back))

        assertEquals(
            Probed.Unknown("CameraX lists no RAW output, and camera2 could not be read to confirm it: $unwrapFailure"),
            probed.rawOutput,
        )
        assertEquals(Probed.Unknown("camera2 could not be read: $unwrapFailure"), probed.manualExposure)
    }

    private class StubExposureState : ExposureState {
        override fun getExposureCompensationIndex(): Int = 0
        override fun getExposureCompensationRange(): Range<Int> = Range(-12, 12)
        override fun getExposureCompensationStep(): Rational = Rational(1, 6)
        override fun isExposureCompensationSupported(): Boolean = true
    }

    /** Implements only what the probe reads; everything else throws, so an unexpected read is a failed read, not a quiet default. */
    private class StubCameraInfo : CameraInfo {
        override fun getExposureState(): ExposureState = StubExposureState()
        override fun getSensorRotationDegrees(): Int = notRead()
        override fun getSensorRotationDegrees(relativeRotation: Int): Int = notRead()
        override fun hasFlashUnit(): Boolean = notRead()
        override fun getTorchState(): LiveData<Int> = notRead()
        override fun getZoomState(): LiveData<ZoomState> = notRead()
        override fun getCameraState(): LiveData<CameraState> = notRead()
        override fun getImplementationType(): String = notRead()
        override fun getCameraSelector(): CameraSelector = notRead()
        override fun toString() = "StubCameraInfo"

        private fun notRead(): Nothing = throw UnsupportedOperationException("the probe does not read this")
    }
}

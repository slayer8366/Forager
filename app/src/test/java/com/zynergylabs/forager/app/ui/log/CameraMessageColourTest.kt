package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CameraCapabilityFixtures
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.LOW_STORAGE_FLOOR_BYTES
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.Probed
import com.zynergylabs.forager.app.domain.raw.DngFailureStage
import com.zynergylabs.forager.app.domain.raw.DngWriteResult
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FakeDngWriter
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.photo.RAW_LOW_STORAGE_MESSAGE
import com.zynergylabs.forager.app.photo.RAW_NOT_CLEAN_MESSAGE
import com.zynergylabs.forager.app.photo.RAW_NOT_SAVED_MESSAGE
import com.zynergylabs.forager.app.photo.RawShotSaver
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The RAW fallback messages are notices, not errors: the photo was saved (RECORD -1019, the owner: "2 yes"). So they are
 * drawn in the camera's normal message colour, and a real failure (no photo at all) keeps the error colour. Checked on the
 * drawn pixels of each message, not on which parameter was passed: a message whose glyphs contain the error colour fails.
 * The failed-capture message is the positive control, so a pixel check that cannot see the error colour cannot pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraMessageColourTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(FileProviderCacheReset()).around(composeRule)

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private val s22Raw = CameraCapabilityFixtures.everythingSupported.copy(rawSizes = Probed.Supported(listOf(PixelSize(4000, 3000)), "test"))

    /** The error colour the message slot draws with here: Material's default scheme, as the dialog has no theme of its own in this test. */
    private val error = lightColorScheme().error

    private fun shootRaw(writer: FakeDngWriter, freeBytes: Long = Long.MAX_VALUE, failCapture: Boolean = false, format: CameraFormat = CameraFormat.Raw) {
        val session = FakeCameraCaptureSession(capabilitiesOnOpen = s22Raw).apply { if (failCapture) failEveryCapture() }
        val files = CameraCaptureFiles(context)
        composeRule.setContent {
            InAppCameraDialog(
                session = session,
                cameraCaptureFiles = files,
                onPhotoCaptured = {},
                onDismiss = {},
                gridMode = GridMode.Off,
                onGridModeChanged = {},
                autoSaveLocationToPhotos = true,
                onAutoSaveLocationToPhotosChanged = {},
                levelProvider = FakeLevelProvider(),
                cameraFormat = format,
                rawShotSaver = RawShotSaver(files::newRawFile, { freeBytes }, writer, Dispatchers.Unconfined),
                viewfinder = { modifier -> Box(modifier.fillMaxSize()) },
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()
    }

    private fun errorPixels(message: String): Pair<Int, Int> {
        val map = composeRule.onNodeWithText(message).captureToImage().toPixelMap()
        var red = 0
        var white = 0
        for (x in 0 until map.width) for (y in 0 until map.height) {
            val c = map[x, y]
            if (near(c, error)) red++
            if (near(c, Color.White)) white++
        }
        println("COLOUR '$message': error-coloured=$red white=$white")
        return red to white
    }

    private fun near(a: Color, b: Color): Boolean =
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) < 0.25f && a.alpha > 0.9f

    private fun assertNormalColour(message: String) {
        val (red, white) = errorPixels(message)
        assertTrue("'$message' is drawn (white pixels: $white)", white >= 10)
        assertTrue("'$message' has no error-coloured pixels (found $red)", red == 0)
    }

    private val written = FakeDngWriter { _, d -> d.writeBytes(byteArrayOf(1)); DngWriteResult.Written(1) }

    @Test
    fun `positive control - a failed capture's message is drawn in the error colour`() {
        shootRaw(written, failCapture = true)
        val (red, _) = errorPixels(CAPTURE_FAILED_MESSAGE)
        assertTrue("the error message shows the error colour (found $red)", red >= 10)
    }

    @Test
    fun `the low-storage notice is drawn in the normal message colour`() {
        shootRaw(written, freeBytes = LOW_STORAGE_FLOOR_BYTES)
        assertNormalColour(RAW_LOW_STORAGE_MESSAGE)
    }

    @Test
    fun `the couldn't-be-saved notice is drawn in the normal message colour`() {
        shootRaw(FakeDngWriter { _, d -> d.delete(); DngWriteResult.Failed("io", DngFailureStage.Writing) })
        assertNormalColour(RAW_NOT_SAVED_MESSAGE)
    }

    @Test
    fun `the couldn't-be-cleaned notice is drawn in the normal message colour`() {
        shootRaw(FakeDngWriter { _, d -> d.delete(); DngWriteResult.Failed("Make", DngFailureStage.Check) })
        assertNormalColour(RAW_NOT_CLEAN_MESSAGE)
    }
}

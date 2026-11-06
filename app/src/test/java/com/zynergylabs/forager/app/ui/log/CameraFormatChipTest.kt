package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.repository.FakeCameraFormatRepository
import com.zynergylabs.forager.app.domain.CameraCapabilities
import com.zynergylabs.forager.app.domain.CameraCapabilityFixtures
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.LOW_STORAGE_FLOOR_BYTES
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.Probed
import com.zynergylabs.forager.app.domain.approximateShotBytes
import com.zynergylabs.forager.app.domain.approximateSizeLabel
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.domain.raw.DngFailureStage
import com.zynergylabs.forager.app.domain.raw.DngWriteResult
import com.zynergylabs.forager.app.domain.worstCaseRawShotBytes
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FakeDngWriter
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.photo.RAW_LOW_STORAGE_MESSAGE
import com.zynergylabs.forager.app.photo.RAW_NOT_CLEAN_MESSAGE
import com.zynergylabs.forager.app.photo.RAW_NOT_SAVED_MESSAGE
import com.zynergylabs.forager.app.photo.RawShot
import com.zynergylabs.forager.app.photo.RawShotSaver
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The format chip and a RAW shot through the real camera screen (dispatch 2026-10-15-01, the step path), on a fake
 * session and a fake DNG writer: the chip appears only where the probe offers RAW, the menu offers JPG and RAW with their
 * sizes, the choice is stored and remembered (RECORD -1005), a RAW shot hands up its DNG with its JPG, and each way a RAW
 * shot can fall back to its JPG says so in the camera's message slot (RECORD -1003, Amendment 1 item 5). The chip and
 * the menu rows are reached by real touches at screen coordinates (CLAUDE.md: a semantic click asserts wiring, not
 * routing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraFormatChipTest {

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

    private val captured = mutableListOf<PhotoSource>()

    /** RAW at the S22 Ultra's reading (RECORD -996). */
    private val s22Raw = CameraCapabilityFixtures.everythingSupported.copy(rawSizes = Probed.Supported(listOf(PixelSize(4000, 3000)), "test"))

    private val plentyOfRoom = LOW_STORAGE_FLOOR_BYTES + worstCaseRawShotBytes(4, 4) + 1

    private fun writtenDng() = FakeDngWriter { _, destination -> destination.writeBytes(DNG); DngWriteResult.Written(DNG.size.toLong()) }

    private fun open(
        repository: FakeCameraFormatRepository = FakeCameraFormatRepository(),
        session: FakeCameraCaptureSession = FakeCameraCaptureSession(capabilitiesOnOpen = s22Raw),
        writer: FakeDngWriter = writtenDng(),
        freeBytes: Long = plentyOfRoom,
    ): FakeCameraCaptureSession {
        val vm = CameraFormatViewModel(repository::getCameraFormat, repository::setCameraFormat, ErrorLog { _, _, _ -> })
        // The stored choice is read before the screen composes, as it is long before a user opens the camera.
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val files = CameraCaptureFiles(context)
        val saver = RawShotSaver(newRawFile = files::newRawFile, freeBytes = { freeBytes }, writer = writer, ioDispatcher = Dispatchers.Unconfined)
        composeRule.setContent {
            val format by vm.format.collectAsState()
            InAppCameraDialog(
                session = session,
                cameraCaptureFiles = files,
                onPhotoCaptured = { captured += it },
                onDismiss = {},
                gridMode = GridMode.Off,
                onGridModeChanged = {},
                autoSaveLocationToPhotos = true,
                onAutoSaveLocationToPhotosChanged = {},
                levelProvider = FakeLevelProvider(),
                cameraFormat = format,
                onCameraFormatChanged = vm::onCameraFormatChanged,
                rawShotSaver = saver,
                viewfinder = { modifier -> Box(modifier.fillMaxSize()) },
            )
        }
        composeRule.waitForIdle()
        return session
    }

    private fun touch(tag: String) {
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput { click(bounds.center) }
        composeRule.waitForIdle()
    }

    private fun chooseFromMenu(format: CameraFormat) {
        touch(CAMERA_FORMAT_CHIP_TAG)
        touch(formatRowTag(format))
    }

    private fun shoot() {
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()
    }

    private fun scratchDngs(): List<File> = File(context.filesDir, "captures").listFiles().orEmpty().filter { it.extension == "dng" }

    // ── Where the chip shows ────────────────────────────────────────────────────────────────────

    @Test
    fun `no chip where the probe offers no RAW, or has not reported`() {
        open(session = FakeCameraCaptureSession(capabilitiesOnOpen = null))
        composeRule.onNodeWithTag(CAMERA_FORMAT_CHIP_TAG).assertDoesNotExist()
    }

    @Test
    fun `no chip where RAW is only offered above the 12 MP operating limit`() {
        val fiftyMp: CameraCapabilities = s22Raw.copy(rawSizes = Probed.Supported(listOf(PixelSize(8160, 6120)), "test"))
        open(session = FakeCameraCaptureSession(capabilitiesOnOpen = fiftyMp))
        composeRule.onNodeWithTag(CAMERA_FORMAT_CHIP_TAG).assertDoesNotExist()
    }

    @Test
    fun `where the probe offers RAW the chip reads JPG on a first run`() {
        open()
        composeRule.onNodeWithTag(CAMERA_FORMAT_CHIP_TAG).assertContentDescriptionEquals("$CAMERA_FORMAT_CHIP_LABEL: JPG")
    }

    // ── The menu ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a touch opens the menu with JPG ticked and RAW beside it, each with its approximate size`() {
        open()
        touch(CAMERA_FORMAT_CHIP_TAG)

        composeRule.onNodeWithTag(CAMERA_FORMAT_MENU_TAG).assertExists()
        composeRule.onNodeWithTag(formatRowTag(CameraFormat.Jpg)).assertIsSelected()
        composeRule.onNodeWithTag(formatRowTag(CameraFormat.Raw)).assertIsNotSelected()
        val size = PixelSize(4000, 3000)
        composeRule.onNodeWithText(approximateSizeLabel(approximateShotBytes(CameraFormat.Jpg, size))).assertExists()
        composeRule.onNodeWithText(approximateSizeLabel(approximateShotBytes(CameraFormat.Raw, size))).assertExists()
        assertEquals("the figures the menu is built from", listOf("~4 MB", "~18 MB"), listOf(CameraFormat.Jpg, CameraFormat.Raw).map { approximateSizeLabel(approximateShotBytes(it, size)) })
    }

    @Test
    fun `choosing RAW stores it, asks the camera for it, closes the menu, and the chip then reads RAW`() {
        val repository = FakeCameraFormatRepository()
        val session = open(repository)

        chooseFromMenu(CameraFormat.Raw)

        assertEquals(CameraFormat.Raw, repository.stored)
        assertEquals(CameraFormat.Raw, session.requestedFormats.last())
        composeRule.onNodeWithTag(CAMERA_FORMAT_MENU_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CAMERA_FORMAT_CHIP_TAG).assertContentDescriptionEquals("$CAMERA_FORMAT_CHIP_LABEL: RAW")
    }

    @Test
    fun `a remembered RAW is asked for before the camera opens, so it binds once`() {
        val session = FakeCameraCaptureSession(capabilitiesOnOpen = s22Raw)
        // The view model reads the store before the screen composes, as a restart does.
        open(FakeCameraFormatRepository(CameraFormat.Raw), session)

        assertEquals("RAW was asked for, then the camera opened", listOf("request Raw", "open"), session.events.take(2))
        assertEquals(CameraFormat.Raw, session.outputFormat)
        composeRule.onNodeWithTag(CAMERA_FORMAT_CHIP_TAG).assertContentDescriptionEquals("$CAMERA_FORMAT_CHIP_LABEL: RAW")
    }

    @Test
    fun `a RAW the camera cannot bind leaves the chip honest, reading JPG`() {
        val session = FakeCameraCaptureSession(capabilitiesOnOpen = s22Raw).apply { rawBinds = false }
        open(session = session)

        chooseFromMenu(CameraFormat.Raw)

        assertEquals("asked", CameraFormat.Raw, session.requestedFormats.last())
        composeRule.onNodeWithTag(CAMERA_FORMAT_CHIP_TAG).assertContentDescriptionEquals("$CAMERA_FORMAT_CHIP_LABEL: JPG")
    }

    @Test
    fun `a write that fails leaves the chip on what is stored`() {
        val repository = FakeCameraFormatRepository().apply { failWrites = true }
        open(repository)

        chooseFromMenu(CameraFormat.Raw)

        assertEquals(1, repository.writes)
        composeRule.onNodeWithTag(CAMERA_FORMAT_CHIP_TAG).assertContentDescriptionEquals("$CAMERA_FORMAT_CHIP_LABEL: JPG")
    }

    @Test
    fun `Back closes the menu and leaves the camera open`() {
        var dismissed = 0
        val repository = FakeCameraFormatRepository()
        val vm = CameraFormatViewModel(repository::getCameraFormat, repository::setCameraFormat, ErrorLog { _, _, _ -> })
        composeRule.setContent {
            val format by vm.format.collectAsState()
            InAppCameraDialog(
                session = FakeCameraCaptureSession(capabilitiesOnOpen = s22Raw),
                cameraCaptureFiles = CameraCaptureFiles(context),
                onPhotoCaptured = {},
                onDismiss = { dismissed += 1 },
                gridMode = GridMode.Off,
                onGridModeChanged = {},
                autoSaveLocationToPhotos = true,
                onAutoSaveLocationToPhotosChanged = {},
                levelProvider = FakeLevelProvider(),
                cameraFormat = format,
                onCameraFormatChanged = vm::onCameraFormatChanged,
                viewfinder = { modifier -> Box(modifier.fillMaxSize()) },
            )
        }
        composeRule.waitForIdle()
        touch(CAMERA_FORMAT_CHIP_TAG)

        composeRule.pressBackOnCamera()

        composeRule.onNodeWithTag(CAMERA_FORMAT_MENU_TAG).assertDoesNotExist()
        assertEquals("the camera stays open", 0, dismissed)
        assertEquals("and nothing was chosen", CameraFormat.Jpg, repository.stored)
    }

    @Test
    fun `a real touch anywhere on the chip reaches it`() {
        open()
        val b = composeRule.onNodeWithTag(CAMERA_FORMAT_CHIP_TAG).fetchSemanticsNode().boundsInRoot
        val inset = 0.2f
        val points = listOf(
            b.center,
            Offset(b.left + b.width * inset, b.top + b.height * inset),
            Offset(b.right - b.width * inset, b.top + b.height * inset),
            Offset(b.left + b.width * inset, b.bottom - b.height * inset),
            Offset(b.right - b.width * inset, b.bottom - b.height * inset),
        )
        for ((i, point) in points.withIndex()) {
            composeRule.onRoot().performTouchInput { click(point) }
            composeRule.waitForIdle()
            assertEquals("touch ${i + 1} at $point opened the menu", 1, composeRule.onAllNodesWithTag(CAMERA_FORMAT_MENU_TAG).fetchSemanticsNodes().size)
            // Close it again with a touch outside, which does nothing else.
            touch(CAMERA_FORMAT_DISMISS_TAG)
            composeRule.onNodeWithTag(CAMERA_FORMAT_MENU_TAG).assertDoesNotExist()
        }
    }

    // ── A RAW shot ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a JPG shot is taken as before and never asks for RAW`() {
        val session = open()
        shoot()
        assertEquals(1, session.captureCalls)
        assertEquals(0, session.captureRawCalls)
        val source = captured.single() as CameraCapturePhotoSource
        assertNull("a JPG shot carries no DNG", source.rawFile)
    }

    @Test
    fun `a RAW shot hands up its JPG with the written DNG beside it, and says nothing`() {
        val session = open(FakeCameraFormatRepository(CameraFormat.Raw))
        shoot()

        assertEquals(0, session.captureCalls)
        assertEquals(1, session.captureRawCalls)
        val source = captured.single() as CameraCapturePhotoSource
        assertTrue("the JPG is on disk", source.capture.file.isFile)
        val dng = source.rawFile ?: throw AssertionError("no DNG handed up")
        assertEquals("dng", dng.extension)
        assertArrayEquals(DNG, dng.readBytes())
        composeRule.onNodeWithTag(CAMERA_ERROR_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(photoCountLabel(1)).assertExists()
    }

    @Test
    fun `a DNG that fails its clean check is discarded, the shot is kept as JPG, and the -1003 message shows`() {
        val refusing = FakeDngWriter { _, destination -> destination.delete(); DngWriteResult.Failed("planted make string", DngFailureStage.Check) }
        open(FakeCameraFormatRepository(CameraFormat.Raw), writer = refusing)
        shoot()

        val source = captured.single() as CameraCapturePhotoSource
        assertNull("no DNG goes with the photo", source.rawFile)
        assertTrue("the JPG is the photo", source.capture.file.isFile)
        assertTrue("no DNG left in captures/", scratchDngs().isEmpty())
        composeRule.onNodeWithText(RAW_NOT_CLEAN_MESSAGE).assertExists()
    }

    @Test
    fun `a DNG the writer could not write is discarded, the shot is kept as JPG, and couldn't be saved shows, not -1003`() {
        val failing = FakeDngWriter { _, destination -> destination.delete(); DngWriteResult.Failed("disk full", DngFailureStage.Writing) }
        open(FakeCameraFormatRepository(CameraFormat.Raw), writer = failing)
        shoot()

        assertNull((captured.single() as CameraCapturePhotoSource).rawFile)
        composeRule.onNodeWithText(RAW_NOT_SAVED_MESSAGE).assertExists()
        composeRule.onNodeWithText(RAW_NOT_CLEAN_MESSAGE).assertDoesNotExist()
    }

    @Test
    fun `with storage low the DNG is never written, the JPG is kept, and the user is told`() {
        val writer = writtenDng()
        open(FakeCameraFormatRepository(CameraFormat.Raw), writer = writer, freeBytes = LOW_STORAGE_FLOOR_BYTES)
        shoot()

        assertEquals("the writer was never asked", 0, writer.images.size)
        val source = captured.single() as CameraCapturePhotoSource
        assertNull(source.rawFile)
        composeRule.onNodeWithText(RAW_LOW_STORAGE_MESSAGE).assertExists()
        composeRule.onNodeWithTag(CAMERA_FORMAT_CHIP_TAG).assertContentDescriptionEquals("$CAMERA_FORMAT_CHIP_LABEL: RAW")
    }

    @Test
    fun `a shot the camera could not describe keeps its JPG and says so`() {
        val session = FakeCameraCaptureSession(capabilitiesOnOpen = s22Raw).apply {
            nextRawShot = { Result.success(RawShot(dng = null, dngUnavailable = "no capture result arrived")) }
        }
        open(FakeCameraFormatRepository(CameraFormat.Raw), session)
        shoot()

        assertNull((captured.single() as CameraCapturePhotoSource).rawFile)
        composeRule.onNodeWithText(RAW_NOT_SAVED_MESSAGE).assertExists()
    }

    @Test
    fun `a RAW shot that fails takes no photo and says so, as a JPG shot's does`() {
        val session = FakeCameraCaptureSession(capabilitiesOnOpen = s22Raw).apply { failEveryCapture() }
        open(FakeCameraFormatRepository(CameraFormat.Raw), session)
        shoot()

        assertEquals(1, session.captureRawCalls)
        assertTrue(captured.isEmpty())
        composeRule.onNodeWithText(CAPTURE_FAILED_MESSAGE).assertExists()
        assertFalse("its scratch JPG is cleaned up", File(context.filesDir, "captures").listFiles().orEmpty().any { it.extension == "jpg" && it.length() > 0 })
    }

    private companion object {
        val DNG = byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00)
    }
}

package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import org.junit.Assert.assertEquals
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
 * [InAppCameraHost]'s one job beyond composing the dialog: handing each captured photo to the
 * consumer the [InAppCameraTarget] names. Driven through the real [InAppCameraDialog] over
 * [FakeCameraCaptureSession] (the slot a test hands in), so a "capture" here is the shutter
 * being tapped and the fake writing a file, and what is asserted is which callback received the
 * resulting [CameraCapturePhotoSource] and that the other two received nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class InAppCameraHostTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FileProviderCacheReset()).around(declareHostActivity).around(composeRule)

    private val logEntryPhotos = mutableListOf<PhotoSource>()
    private val albumPhotos = mutableListOf<PhotoSource>()
    private val cartographyPhotos = mutableListOf<PhotoSource>()
    private var dismissed = 0
    private var slotSawLockToPortrait: Boolean? = null

    /** The test's slot: the real dialog over the fake session, viewfinder a plain box. */
    private val fakeCamera: InAppCameraSlot = { cameraCaptureFiles, lockToPortrait, onPhotoCaptured, onDismiss ->
        slotSawLockToPortrait = lockToPortrait
        val session = FakeCameraCaptureSession()
        InAppCameraDialog(
            session = session,
            cameraCaptureFiles = cameraCaptureFiles,
            lockToPortrait = lockToPortrait,
            onPhotoCaptured = onPhotoCaptured,
            onDismiss = onDismiss,
            viewfinder = { modifier -> Box(modifier) },
        )
    }

    private fun setHost(initial: InAppCameraTarget?): (InAppCameraTarget?) -> Unit {
        var setter: (InAppCameraTarget?) -> Unit = {}
        composeRule.setContent {
            var target by androidx.compose.runtime.remember { mutableStateOf(initial) }
            setter = { target = it }
            InAppCameraHost(
                target = target,
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                lockToPortrait = false,
                onLogEntryPhoto = { logEntryPhotos += it },
                onAlbumPhoto = { albumPhotos += it },
                onCartographyEntryPhoto = { cartographyPhotos += it },
                onDismiss = { dismissed++ },
                camera = fakeCamera,
            )
        }
        return setter
    }

    private fun shoot() {
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { logEntryPhotos.size + albumPhotos.size + cartographyPhotos.size == 1 }
    }

    @Test
    fun `no target composes no dialog`() {
        setHost(null)
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(0)
    }

    @Test
    fun `a photo for a find goes to the find's callback and nowhere else`() {
        setHost(InAppCameraTarget.LOG_ENTRY)
        shoot()
        assertEquals(1, logEntryPhotos.size)
        assertTrue(logEntryPhotos.single() is CameraCapturePhotoSource)
        assertEquals("the host hands the setting to the slot unchanged", false, slotSawLockToPortrait)
        assertEquals(0, albumPhotos.size)
        assertEquals(0, cartographyPhotos.size)
    }

    @Test
    fun `a photo for the Album goes to the Album's callback and nowhere else`() {
        setHost(InAppCameraTarget.ALBUM)
        shoot()
        assertEquals(1, albumPhotos.size)
        assertEquals(0, logEntryPhotos.size)
        assertEquals(0, cartographyPhotos.size)
    }

    @Test
    fun `a photo for a Cartography entry goes to that callback and nowhere else`() {
        setHost(InAppCameraTarget.CARTOGRAPHY_ENTRY)
        shoot()
        assertEquals(1, cartographyPhotos.size)
        assertEquals(0, logEntryPhotos.size)
        assertEquals(0, albumPhotos.size)
    }

    @Test
    fun `Back asks the holder to close, and the host itself owns no open state`() {
        val setTarget = setHost(InAppCameraTarget.ALBUM)
        composeRule.pressBackOnCamera()
        composeRule.waitForIdle()

        assertEquals(1, dismissed)
        // Until the holder clears the target, the dialog is still there: closing is the
        // ViewModel's decision, not a side effect the host takes on its own.
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(1)
        setTarget(null)
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(0)
    }
}

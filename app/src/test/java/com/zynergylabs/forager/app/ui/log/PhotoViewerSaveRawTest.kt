package com.zynergylabs.forager.app.ui.log

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LogPhoto
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowToast

/**
 * A RAW shot's export (dispatch 2026-10-15-01, step 6: "Export sends the RAW file. The JPG is never a separate photo";
 * RECORD -1004), through the viewer's real save control, as `PhotoViewerSaveTest` drives a JPG's. The viewer shows the
 * JPG; what leaves the app is the DNG. Whether the phone's Gallery accepts a DNG is a device check.
 */
private val DNG_BYTES = byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)

private fun rawContext(): Application = ApplicationProvider.getApplicationContext()

/** A RAW photo as the store leaves one: the shot's JPG in `relativePath`, the DNG beside it. */
private fun storedRawPhoto(id: String, dng: ByteArray = DNG_BYTES): LogPhoto {
    val dir = File(rawContext().filesDir, "photos").apply { mkdirs() }
    File(dir, "$id.jpg").writeBytes(EXPORT_TEST_JPEG)
    File(dir, "$id.dng").writeBytes(dng)
    return LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = 1_800_000_000_000L, rawRelativePath = "photos/$id.dng")
}

private fun rawHostRule() = object : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}

private fun awaitToastText(rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>): String {
    rule.waitUntil(timeoutMillis = 5_000) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        ShadowToast.getTextOfLatestToast() != null
    }
    return ShadowToast.getTextOfLatestToast()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoViewerSaveRawToGalleryTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(rawHostRule()).around(composeRule)

    @Before
    fun setUp() {
        FakeMediaProvider.reset()
        ShadowToast.reset()
        ShadowLog.clear()
        Robolectric.buildContentProvider(FakeMediaProvider::class.java).create("media")
    }

    @After
    fun tearDown() = FakeMediaProvider.reset()

    @Test
    fun `saving a RAW photo sends its DNG, typed and named as one, not its JPG`() {
        val photo = storedRawPhoto("raw-gallery")
        composeRule.setContent { PhotoViewerDialog(photos = listOf(photo), initialIndex = 0, onDismiss = {}) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Save to Gallery").performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertEquals("Saved to Gallery", awaitToastText(composeRule))
        val row = FakeMediaProvider.inserted.single()
        assertEquals("image/x-adobe-dng", row.values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        val name = row.values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
        assertTrue("the name '$name' ends .dng", name.endsWith(".dng"))
        assertArrayEquals("the DNG's bytes, not the JPG's", DNG_BYTES, row.file.readBytes())
    }

    @Test
    fun `a RAW photo whose DNG is not a TIFF container is not exported as one`() {
        val photo = storedRawPhoto("raw-not-tiff", dng = EXPORT_TEST_JPEG)
        composeRule.setContent { PhotoViewerDialog(photos = listOf(photo), initialIndex = 0, onDismiss = {}) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Save to Gallery").performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertEquals("Couldn't save that photo.", awaitToastText(composeRule))
        assertTrue("nothing was published", FakeMediaProvider.inserted.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoViewerSaveRawToFolderTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(rawHostRule()).around(composeRule)

    @Before
    fun setUp() {
        ShadowToast.reset()
        ShadowLog.clear()
    }

    @Test
    fun `the folder picker is offered a DNG, and the chosen document receives the DNG's bytes`() {
        val photo = storedRawPhoto("raw-folder")
        composeRule.setContent { PhotoViewerDialog(photos = listOf(photo), initialIndex = 0, onDismiss = {}) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Save to folder").performTouchInput { click(center) }
        val activity = Shadows.shadowOf(composeRule.activity)
        composeRule.waitUntil(timeoutMillis = 5_000) { activity.peekNextStartedActivityForResult() != null }
        val intent = activity.peekNextStartedActivityForResult().intent
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("image/x-adobe-dng", intent.type)
        assertTrue(intent.getStringExtra(Intent.EXTRA_TITLE)!!.endsWith(".dng"))

        val target = File(rawContext().cacheDir, "picked/chosen.dng").apply { parentFile?.mkdirs(); delete() }
        composeRule.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(target))) }
        composeRule.waitForIdle()

        assertEquals("Saved", awaitToastText(composeRule))
        assertArrayEquals("the DNG's bytes, not the JPG's", DNG_BYTES, target.readBytes())
    }
}

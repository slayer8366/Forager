package com.zynergylabs.forager.app.ui.log

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.exifinterface.media.ExifInterface
import com.zynergylabs.forager.app.domain.model.LogPhoto
import java.io.File
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

private typealias SaveRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

private const val GALLERY = "Save to Gallery"
private const val FOLDER = "Save to folder"
private const val EXPORTER_TAG = "PhotoExporter"

private fun hostActivityRule() = object : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}

private fun context(): Application = ApplicationProvider.getApplicationContext()

/** A photo file in the store's own place, whatever its bytes are, named `.jpg` as `FilePhotoStore` names every one (`FilePhotoStore.kt:120`). */
private fun storedPhoto(id: String, bytes: ByteArray): LogPhoto {
    val file = File(context().filesDir, "photos/$id.jpg")
    file.parentFile?.mkdirs()
    file.writeBytes(bytes)
    return LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = 1_800_000_000_000L)
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun SaveRule.show(photo: LogPhoto) {
    setContent { PhotoViewerDialog(photos = listOf(photo), initialIndex = 0, onDismiss = {}) }
    waitForIdle()
}

private fun SaveRule.tap(description: String) {
    onNodeWithContentDescription(description).performTouchInput { click(center) }
    waitForIdle()
}

/** Waits for the toast the save ends with (the copy runs off the main thread) and returns its text. */
private fun SaveRule.awaitToast(): String {
    try {
        // The message is posted to the main looper from the IO thread; Robolectric's paused looper runs it only when told to
        // (a real device's runs on its own), and waitUntil pumps the compose clock, not that looper.
        waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast() != null
        }
    } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
        // Say what was going on instead of only that nothing happened: the logs and what the provider saw.
        val logs = ShadowLog.getLogs().joinToString("\n") { "${it.type} ${it.tag}: ${it.msg} ${it.throwable ?: ""}" }
        throw AssertionError(
            "no toast within 5 s. toasts=${ShadowToast.shownToastCount()} latest=${ShadowToast.getLatestToast()} mainLooperIdle=${Shadows.shadowOf(android.os.Looper.getMainLooper()).isIdle} inserted=${FakeMediaProvider.inserted.size} updated=${FakeMediaProvider.updated.size} deleted=${FakeMediaProvider.deleted.size}; logs:\n$logs",
            timeout,
        )
    }
    return ShadowToast.getTextOfLatestToast()
}

private fun exporterWarnings() = ShadowLog.getLogs().filter { it.tag == EXPORTER_TAG && it.type == Log.WARN }

/**
 * "Save to Gallery" on the photo viewer, API 29+ (owner, 2026-09-29: "1 A", "4 A"; intent `2026-09-28-126`), through the
 * real [PhotoViewerDialog] and a real touch on its control. The platform's media provider is replaced by
 * [FakeMediaProvider], which records what the exporter inserts; the stored file is the store's own, under `filesDir/photos/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoViewerSaveToGalleryTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    @Before
    fun setUp() {
        FakeMediaProvider.reset()
        ShadowToast.reset()
        ShadowLog.clear()
        Robolectric.buildContentProvider(FakeMediaProvider::class.java).create("media")
    }

    @After
    fun tearDown() = FakeMediaProvider.reset()

    private fun assertSavedAs(mime: String, extension: String) {
        assertEquals("one row was inserted", 1, FakeMediaProvider.inserted.size)
        val row = FakeMediaProvider.inserted.single()
        assertEquals("the type is the content's", mime, row.values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        val name = row.values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
        assertTrue("the name '$name' ends .$extension, not .jpg", name.endsWith(".$extension"))
        assertEquals("Pictures/Forager", row.values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
    }

    @Test
    fun `saving a JPEG inserts it under Pictures-Forager with its own type and the stored bytes, and says Saved to Gallery`() {
        val photo = storedPhoto("save-jpeg", EXPORT_TEST_JPEG)
        composeRule.show(photo)

        composeRule.tap(GALLERY)

        assertEquals("Saved to Gallery", composeRule.awaitToast())
        assertSavedAs("image/jpeg", "jpg")
        val row = FakeMediaProvider.inserted.single()
        assertTrue(row.values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME).startsWith("forager-photo-"))
        assertEquals("the row is pending while it is written", 1, row.values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertEquals(
            "and published afterwards",
            0,
            // F1 item 7: the record's time is written after the publish too, so the publish is the one update that carries IS_PENDING.
            FakeMediaProvider.updated.single { it.first == row.uri && it.second.containsKey(MediaStore.MediaColumns.IS_PENDING) }.second.getAsInteger(MediaStore.MediaColumns.IS_PENDING),
        )
        assertArrayEquals("the stored bytes were written", EXPORT_TEST_JPEG, row.file.readBytes())
        assertTrue("no location column is written", listOf("latitude", "longitude").none { row.values.containsKey(it) })
        assertTrue("nothing was deleted", FakeMediaProvider.deleted.isEmpty())
    }

    @Test
    fun `PNG content in a jpg-named file is saved as image-png with a png name`() {
        composeRule.show(storedPhoto("save-png", exportTestPng()))

        composeRule.tap(GALLERY)

        assertEquals("Saved to Gallery", composeRule.awaitToast())
        assertSavedAs("image/png", "png")
    }

    @Test
    fun `HEIC content in a jpg-named file is saved as image-heic with a heic name`() {
        composeRule.show(storedPhoto("save-heic", exportTestHeic()))

        composeRule.tap(GALLERY)

        assertEquals("Saved to Gallery", composeRule.awaitToast())
        assertSavedAs("image/heic", "heic")
    }

    @Test
    fun `WebP content in a jpg-named file is saved as image-webp with a webp name`() {
        composeRule.show(storedPhoto("save-webp", exportTestWebp()))

        composeRule.tap(GALLERY)

        assertEquals("Saved to Gallery", composeRule.awaitToast())
        assertSavedAs("image/webp", "webp")
    }

    @Test
    fun `an imported photo is exported as its stored bytes and the stored file is not touched`() {
        // The owner's ruling (2026-09-29): imports are outside our scope and are exported unchanged, so a location the
        // import carries stays in the copy; the stored file must not change either way.
        val file = File(context().filesDir, "photos/save-import.jpg").apply { parentFile?.mkdirs(); writeBytes(EXPORT_TEST_JPEG) }
        ExifInterface(file.absolutePath).apply {
            setLatLong(45.5, -122.6)
            saveAttributes()
        }
        val storedBefore = file.readBytes()
        assertNotNull("the fixture really carries a GPS position", ExifInterface(file.absolutePath).latLong)
        val photo = LogPhoto("save-import", "photos/save-import.jpg", createdAtEpochMillis = 1_800_000_000_000L)
        composeRule.show(photo)

        composeRule.tap(GALLERY)

        assertEquals("Saved to Gallery", composeRule.awaitToast())
        assertEquals("the exported copy is the stored bytes", sha256(storedBefore), sha256(FakeMediaProvider.inserted.single().file.readBytes()))
        assertEquals("the stored file is byte-unchanged", sha256(storedBefore), sha256(file.readBytes()))
    }

    @Test
    fun `when the store refuses the row the failure message shows and the failure is logged`() {
        FakeMediaProvider.refuseInsert = true
        composeRule.show(storedPhoto("save-refused", EXPORT_TEST_JPEG))

        composeRule.tap(GALLERY)

        assertEquals("Couldn't save that photo.", composeRule.awaitToast())
        assertEquals("exactly one toast", 1, ShadowToast.shownToastCount())
        assertEquals("one warning from the exporter", 1, exporterWarnings().size)
        assertTrue("nothing was inserted", FakeMediaProvider.inserted.isEmpty())
    }

    @Test
    fun `bytes that are no image are not saved, the failure message shows, and it is logged`() {
        composeRule.show(storedPhoto("save-text", exportTestNotAnImage()))

        composeRule.tap(GALLERY)

        assertEquals("Couldn't save that photo.", composeRule.awaitToast())
        assertTrue("no row was created for a type that could not be told", FakeMediaProvider.inserted.isEmpty())
        assertEquals("one warning from the exporter", 1, exporterWarnings().size)
    }
}

/**
 * The same action on Android 9 and below (owner, "3 B"): "Save to folder", which opens the Storage Access Framework's
 * create-document picker and writes where the user chose. Nothing goes to MediaStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoViewerSaveToFolderTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    @Before
    fun setUp() {
        FakeMediaProvider.reset()
        ShadowToast.reset()
        ShadowLog.clear()
        Robolectric.buildContentProvider(FakeMediaProvider::class.java).create("media")
    }

    @After
    fun tearDown() = FakeMediaProvider.reset()

    /** The intent the picker was started with, once the exporter has sniffed the file. */
    private fun awaitPickerIntent(): Intent {
        val activity = Shadows.shadowOf(composeRule.activity)
        composeRule.waitUntil(timeoutMillis = 5_000) { activity.peekNextStartedActivityForResult() != null }
        return activity.peekNextStartedActivityForResult().intent
    }

    private fun deliverPick(pickerIntent: Intent, resultCode: Int, uri: Uri?) {
        composeRule.runOnUiThread {
            Shadows.shadowOf(composeRule.activity).receiveResult(pickerIntent, resultCode, uri?.let { Intent().setData(it) })
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a tap opens the create-document picker with the sniffed type and name, and writes nothing to MediaStore`() {
        composeRule.show(storedPhoto("folder-launch", exportTestPng()))

        composeRule.tap(FOLDER)

        val intent = awaitPickerIntent()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("image/png", intent.type)
        assertTrue(intent.categories.contains(Intent.CATEGORY_OPENABLE))
        val title = intent.getStringExtra(Intent.EXTRA_TITLE)
        assertNotNull(title)
        assertTrue("the suggested name '$title' ends .png", title!!.endsWith(".png"))
        assertTrue("MediaStore was not touched", FakeMediaProvider.inserted.isEmpty())
        assertNull("no message before the user has chosen", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `the chosen document receives the stored bytes and Saved shows`() {
        val photo = storedPhoto("folder-write", EXPORT_TEST_JPEG)
        composeRule.show(photo)
        composeRule.tap(FOLDER)
        val intent = awaitPickerIntent()
        val target = File(context().cacheDir, "picked/chosen.jpg").apply { parentFile?.mkdirs(); delete() }

        deliverPick(intent, Activity.RESULT_OK, Uri.fromFile(target))

        assertEquals("Saved", composeRule.awaitToast())
        assertArrayEquals(EXPORT_TEST_JPEG, target.readBytes())
        assertArrayEquals("the stored file is unchanged", EXPORT_TEST_JPEG, File(context().filesDir, photo.relativePath).readBytes())
    }

    @Test
    fun `a document that cannot be written shows the failure message and is logged`() {
        composeRule.show(storedPhoto("folder-fail", EXPORT_TEST_JPEG))
        composeRule.tap(FOLDER)
        val intent = awaitPickerIntent()

        deliverPick(intent, Activity.RESULT_OK, Uri.fromFile(File(context().cacheDir, "no-such-dir/x/chosen.jpg")))

        assertEquals("Couldn't save that photo.", composeRule.awaitToast())
        assertEquals("one warning from the exporter", 1, exporterWarnings().size)
    }

    @Test
    fun `cancelling the picker shows no message`() {
        composeRule.show(storedPhoto("folder-cancel", EXPORT_TEST_JPEG))
        composeRule.tap(FOLDER)
        val intent = awaitPickerIntent()

        deliverPick(intent, Activity.RESULT_CANCELED, null)

        assertNull(ShadowToast.getTextOfLatestToast())
        assertTrue(exporterWarnings().isEmpty())
    }
}

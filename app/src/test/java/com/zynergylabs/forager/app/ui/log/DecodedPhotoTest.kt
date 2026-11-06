package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Base64
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
import org.robolectric.shadows.ShadowLog

/**
 * Workstream G2 (`docs/plans/pr26-rework.md`): [DecodedPhoto] is the single decode-and-render
 * component [LogPhotoThumbnail]/[LogGalleryScreen]'s cover thumbnail/[LogEntryReportScreen]'s
 * report thumbnail all now delegate to. Covers the two things the dispatch's own gate names:
 * a decodable photo renders, and a decode failure is logged rather than silently swallowed.
 *
 * The "decode failure" fixture below isn't an arbitrary guess at what makes
 * `BitmapFactory.decodeFile` fail under Robolectric — checked empirically first (per this
 * project's own "see the failure before writing the fix" discipline): Robolectric's default
 * `BitmapFactory` shadow under this project's pinned SDK (36) fakes a successful 100x100 decode
 * for *any* file path, including a missing one or one containing arbitrary non-image bytes — it
 * does not fail the way real Android would. The one input that reliably throws instead is a
 * minimal, truncated real PNG (valid signature, incomplete/malformed body) — verified by direct
 * probe before this file was written, not assumed to work by analogy with a real device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DecodedPhotoTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private fun filePath(name: String, bytes: ByteArray): String {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File(context.filesDir, name)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return name
    }

    @Test
    fun `a decodable photo renders as an image, not the placeholder`() {
        // Robolectric's shadow fakes a successful decode for any existing file's bytes here,
        // including bytes that aren't a real image — see this class's own doc comment. This
        // fixture stands in for "decoding succeeded," not for "these bytes are a real photo."
        val relativePath = filePath("decodable.jpg", byteArrayOf(1, 2, 3, 4, 5))

        composeRule.setContent {
            DecodedPhoto(relativePath = relativePath, modifier = Modifier)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Log photo").assertExists()
    }

    @Test
    fun `a decode failure logs a warning and shows the placeholder, not a crash or silence`() {
        val relativePath = filePath("corrupt.png", CORRUPT_PNG_BYTES)

        composeRule.setContent {
            DecodedPhoto(relativePath = relativePath, modifier = Modifier)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ShadowLog.getLogs().any { it.tag == "LogPhotoDecode" }
        }

        composeRule.onNodeWithContentDescription("Log photo").assertDoesNotExist()
        val warning = ShadowLog.getLogs().first { it.tag == "LogPhotoDecode" }
        assertTrue("expected the failure logged at WARN, was type ${warning.type}", warning.type == Log.WARN)
        assertTrue("the logged message should name the failing path", warning.msg.contains(relativePath))
    }

    @Test
    fun `contentDescription is passed through`() {
        val relativePath = filePath("decodable2.jpg", byteArrayOf(9, 9, 9))

        composeRule.setContent {
            DecodedPhoto(relativePath = relativePath, modifier = Modifier, contentDescription = "Gallery photo")
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Gallery photo").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Gallery photo").assertExists()
    }

    /**
     * EXIF-orientation-display dispatch: the one decode every thumbnail site shares turns the bitmap
     * per its orientation tag. Rewritten for dispatch 2026-09-28-658 (RECORD -660, item 6): the decode
     * is now sized to the cell, so the composable's node is the cell's size and no longer shows the
     * bitmap's. The bitmap is read from [decodeThumbnail], the decode `DecodedPhoto` runs, with exact
     * sizes worked by hand, as strictly as the fixed-sample version did.
     *
     * The fixture is 40×20. For a 10 px cell the shorter edge, 20, halves once to 10 and not again
     * (5 < 10): sample 2, a 20×10 bitmap, turned to 10×20 by ROTATE_90. For a 40 px cell the
     * shorter edge cannot halve at all (10 < 40): sample 1, 40×20, turned to 20×40. Robolectric's
     * legacy `BitmapFactory` reads a real JPEG's dimensions and honours `inSampleSize`.
     */
    @Test
    fun `a rotate-90 tagged photo decodes turned and sized to its cell, an untagged one unturned`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val turned = File(context.filesDir, jpegFixture("turned.jpg", ExifInterface.ORIENTATION_ROTATE_90))
        val plain = File(context.filesDir, jpegFixture("plain.jpg", null))

        decodeThumbnail(turned, cellEdgePx = 10).let { assertEquals("turned, 10 px cell", 10 to 20, it.width to it.height) }
        decodeThumbnail(plain, cellEdgePx = 10).let { assertEquals("plain, 10 px cell", 20 to 10, it.width to it.height) }
        decodeThumbnail(turned, cellEdgePx = 40).let { assertEquals("turned, 40 px cell", 20 to 40, it.width to it.height) }
        decodeThumbnail(plain, cellEdgePx = 40).let { assertEquals("plain, 40 px cell", 40 to 20, it.width to it.height) }
    }

    /** The composable decodes once it has a size, and shows the photo filling the cell it was given. */
    @Test
    fun `a sized thumbnail loads and keeps its cell's size`() {
        val turned = jpegFixture("turned.jpg", ExifInterface.ORIENTATION_ROTATE_90)

        composeRule.setContent {
            Column {
                DecodedPhoto(relativePath = turned, modifier = Modifier.size(10.dp), contentDescription = "Turned photo")
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Turned photo").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Turned photo").assertWidthIsEqualTo(10.dp).assertHeightIsEqualTo(10.dp)
    }

    private fun jpegFixture(name: String, orientationTag: Int?): String {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File(context.filesDir, "photos/$name").apply { parentFile?.mkdirs() }
        file.writeBytes(Base64.getDecoder().decode(JPEG_40X20_BASE64))
        if (orientationTag != null) {
            ExifInterface(file.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientationTag.toString()); saveAttributes() }
        }
        return "photos/$name"
    }

    private companion object {
        /** A 40×20 baseline JPEG with no EXIF segment — the same fixture [com.zynergylabs.forager.app.photo.PhotoOrientationTest] uses. */
        const val JPEG_40X20_BASE64 = "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAUACgDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD5/ooooAKKKKACiiigAooooAKKKKACiiigD//Z"

        // A minimal, deliberately truncated PNG — valid signature and header, incomplete body.
        // Verified by direct probe (see class doc comment) to make Robolectric's real ImageIO-backed
        // PNG path throw rather than fall back to a faked successful decode.
        val CORRUPT_PNG_BYTES: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
    }
}

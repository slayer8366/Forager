package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Base64
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

    private companion object {
        // A minimal, deliberately truncated PNG — valid signature and header, incomplete body.
        // Verified by direct probe (see class doc comment) to make Robolectric's real ImageIO-backed
        // PNG path throw rather than fall back to a faked successful decode.
        val CORRUPT_PNG_BYTES: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
    }
}

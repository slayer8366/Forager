package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.pinch
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LogPhoto
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLog

/**
 * [PhotoViewerDialog]'s gestures and both of its exits. Every gesture here is a real
 * `performTouchInput` at coordinates inside the dialog's own window — the pinch and the drag go
 * through [androidx.compose.foundation.gestures.detectTransformGestures] exactly as a finger's
 * would, and there is no View interop anywhere in this dialog, so this is not the
 * Compose-interop gesture-routing blind spot the dispatch names. What Robolectric cannot do here
 * is decode a real photo: its legacy `BitmapFactory` shadow fakes a 100×100 bitmap for any bytes
 * (see [DecodedPhotoTest]), so the fit geometry below is that of a 100×100 image in the test
 * window, not of a real capture.
 *
 * Zoom is read back through the photo's `stateDescription` (what TalkBack announces) and pan
 * through [PhotoViewerPanKey] (see its doc comment for why a semantics seam is the only one).
 *
 * Back is sent as a key event to the dialog window itself, not through the Activity's
 * `OnBackPressedDispatcher`: a dialog is its own window, and the Activity route the existing
 * back tests use never reaches it (a caveat
 * [com.zynergylabs.forager.app.ui.availability.AvailabilityScreenMapIconStackTest] already
 * records for its exit prompt).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoViewerDialogTest {

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

    private fun photoFile(name: String, bytes: ByteArray = byteArrayOf(1, 2, 3, 4, 5)): LogPhoto {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File(context.filesDir, "photos/$name")
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return LogPhoto(id = name, relativePath = "photos/$name", createdAtEpochMillis = 1_000L)
    }

    private var dismissals = 0

    private fun open(photos: List<LogPhoto>, initialIndex: Int = 0) {
        dismissals = 0
        composeRule.setContent {
            PhotoViewerDialog(photos = photos, initialIndex = initialIndex, onDismiss = { dismissals++ })
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(VIEWER_PHOTO_DESCRIPTION).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun photo(): SemanticsNodeInteraction = composeRule.onNodeWithContentDescription(VIEWER_PHOTO_DESCRIPTION)

    private fun zoomLabel(): String = photo().fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    private fun pan(): Offset = photo().fetchSemanticsNode().config[PhotoViewerPanKey]

    /** "Zoom 2.5×" → 2.5f. Parsed rather than string-matched so a range assertion can be made where the gesture's exact factor depends on touch slop. */
    private fun zoom(): Float = zoomLabel().removePrefix("Zoom ").removeSuffix("×").toFloat()

    @Test
    fun `opens fit to screen, with no pan`() {
        open(listOf(photoFile("a.jpg")))

        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertIsDisplayed()
        assertEquals("Zoom 1.0×", zoomLabel())
        assertEquals(Offset.Zero, pan())
    }

    @Test
    fun `pinching out zooms in`() {
        open(listOf(photoFile("a.jpg")))

        photo().performTouchInput {
            pinch(
                start0 = center - Offset(20f, 0f),
                end0 = center - Offset(60f, 0f),
                start1 = center + Offset(20f, 0f),
                end1 = center + Offset(60f, 0f),
            )
        }
        composeRule.waitForIdle()

        val zoom = zoom()
        assertTrue("a pinch from 40px apart to 120px apart must zoom in past fit, was $zoom", zoom > 1f)
        assertTrue("and must stay under the ceiling of $MAX_SCALE, was $zoom", zoom < MAX_SCALE)
    }

    @Test
    fun `zoom is capped at the ceiling, however far the fingers spread`() {
        open(listOf(photoFile("a.jpg")))

        photo().performTouchInput {
            pinch(
                start0 = center - Offset(5f, 0f),
                end0 = center - Offset(150f, 0f),
                start1 = center + Offset(5f, 0f),
                end1 = center + Offset(150f, 0f),
            )
        }
        composeRule.waitForIdle()

        assertEquals("Zoom 5.0×", zoomLabel())
    }

    @Test
    fun `pinching in at fit does not shrink below fit`() {
        open(listOf(photoFile("a.jpg")))

        photo().performTouchInput {
            pinch(
                start0 = center - Offset(100f, 0f),
                end0 = center - Offset(10f, 0f),
                start1 = center + Offset(100f, 0f),
                end1 = center + Offset(10f, 0f),
            )
        }
        composeRule.waitForIdle()

        assertEquals("Zoom 1.0×", zoomLabel())
        assertEquals(Offset.Zero, pan())
    }

    @Test
    fun `double-tap toggles between fit and the double-tap zoom`() {
        open(listOf(photoFile("a.jpg")))

        photo().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        assertEquals("Zoom 2.5×", zoomLabel())

        photo().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        assertEquals("Zoom 1.0×", zoomLabel())
        assertEquals(Offset.Zero, pan())
    }

    @Test
    fun `dragging pans only once zoomed in`() {
        open(listOf(photoFile("a.jpg")))

        // At fit the image already fits the viewport, so a drag has nowhere to move it.
        photo().performTouchInput {
            down(center)
            moveBy(Offset(-80f, 0f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals("a drag at fit must not pan", Offset.Zero, pan())

        photo().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        photo().performTouchInput {
            down(center)
            moveBy(Offset(-80f, 0f))
            up()
        }
        composeRule.waitForIdle()
        val panned = pan()
        assertTrue("a leftward drag while zoomed must pan the photo left, pan was $panned", panned.x < 0f)
        assertTrue("and by no more than the finger moved (80px), pan was $panned", panned.x >= -80f)
        assertEquals("a horizontal drag must not move the photo vertically", 0f, panned.y)
    }

    @Test
    fun `the close control dismisses`() {
        open(listOf(photoFile("a.jpg")))

        composeRule.onNodeWithContentDescription("Close photo").performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(1, dismissals)
    }

    @Test
    fun `the back key on the dialog window dismisses`() {
        open(listOf(photoFile("a.jpg")))
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("the viewer must be a real Dialog window for the system back gesture to reach it", dialog)

        composeRule.runOnUiThread {
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        composeRule.waitForIdle()

        assertEquals(1, dismissals)
    }

    @Test
    fun `several photos step with the controls and reset zoom on each step`() {
        open(listOf(photoFile("a.jpg"), photoFile("b.jpg"), photoFile("c.jpg")), initialIndex = 1)
        composeRule.onNodeWithTag(PHOTO_VIEWER_COUNTER_TAG).assertTextEquals("2 / 3")

        photo().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        assertEquals("Zoom 2.5×", zoomLabel())

        composeRule.onNodeWithContentDescription("Next photo").performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PHOTO_VIEWER_COUNTER_TAG).assertTextEquals("3 / 3")
        assertEquals("stepping to another photo starts it at fit", "Zoom 1.0×", zoomLabel())

        composeRule.onNodeWithContentDescription("Next photo").performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PHOTO_VIEWER_COUNTER_TAG).assertTextEquals("1 / 3")

        composeRule.onNodeWithContentDescription("Previous photo").performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PHOTO_VIEWER_COUNTER_TAG).assertTextEquals("3 / 3")
    }

    @Test
    fun `a single photo shows no stepping controls`() {
        open(listOf(photoFile("a.jpg")))

        composeRule.onNodeWithTag(PHOTO_VIEWER_COUNTER_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Next photo").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Previous photo").assertDoesNotExist()
    }

    @Test
    fun `a decode failure is said and logged, not shown as a blank or a crash`() {
        val corrupt = photoFile("corrupt.png", CORRUPT_PNG_BYTES)
        dismissals = 0
        composeRule.setContent {
            PhotoViewerDialog(photos = listOf(corrupt), initialIndex = 0, onDismiss = { dismissals++ })
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(VIEWER_PHOTO_DESCRIPTION).fetchSemanticsNodes().isEmpty() &&
                ShadowLog.getLogs().any { it.tag == "PhotoViewer" }
        }

        composeRule.onNodeWithTag(PHOTO_VIEWER_FAILED_TAG).assertIsDisplayed()
        val warning = ShadowLog.getLogs().first { it.tag == "PhotoViewer" }
        assertEquals(Log.WARN, warning.type)
        assertTrue("the logged message should name the failing path", warning.msg.contains(corrupt.relativePath))
        composeRule.onNodeWithContentDescription("Close photo").assertIsDisplayed()
    }

    private companion object {
        /** The same truncated PNG [DecodedPhotoTest] verified makes Robolectric's real ImageIO-backed PNG path throw. */
        val CORRUPT_PNG_BYTES: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
    }
}

package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import org.junit.After
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
 * The find page's photos (RECORD -868: "have the picture be centered inside the find entry itself"; RECORD -871:
 * "Under the title, whole photo, next one peeking (Recommended)"), through the real [LogEntryReportScreen].
 *
 * Native graphics, with real PNG files of a known shape written to app storage: under Robolectric's default (legacy)
 * graphics every decode is a 100 x 100 square whatever the file (see [DecodedPhotoTest]'s doc comment), so a crop and
 * the whole photo would look the same. Here a 400 x 200 photo is twice as wide as tall, and the drawn photo must be
 * too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FindPagePhotosTest {

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

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val written = mutableListOf<File>()

    @After
    fun tearDown() {
        written.forEach { it.delete() }
    }

    /** A real PNG of [width] x [height] pixels at [relativePath] under app storage, where [DecodedPhoto] reads. */
    private fun photo(id: String, width: Int, height: Int): LogPhoto {
        val relativePath = "photos/$id.png"
        val file = File(context.filesDir, relativePath)
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF6B8E23.toInt()) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        written += file
        return LogPhoto(id = id, relativePath = relativePath, createdAtEpochMillis = 1_000L)
    }

    private val find = MushroomLogEntry.draft(id = "find-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 10, 10))
        .copy(isDraft = false, ownIdentification = "Chanterelle")

    private fun setScreen(photos: List<LogPhoto>) {
        composeRule.setContent {
            LogEntryReportScreen(entry = find.copy(photos = photos), onEdit = {}, onDeleteEntry = {}, onBack = {})
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().size >= minOf(photos.size, 2)
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun root(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()
    private val DpRect.centreX: Dp get() = (left + right) / 2
    private fun near(a: Dp, b: Dp, tolerance: Dp = 1.dp) = abs((a - b).value) <= tolerance.value

    @Test
    fun `one photo comes first under the title, the page's width less its margins, centred, whole in its own shape`() {
        val wide = photo("wide", 400, 200)
        setScreen(listOf(wide))

        val photo = bounds(findPagePhotoTag("wide"))
        val title = bounds(FIND_PAGE_TITLE_TAG)
        val foundAt = composeRule.onNodeWithText("Found at", substring = true).getUnclippedBoundsInRoot()
        val identification = composeRule.onNodeWithText("Your own identification", substring = true).getUnclippedBoundsInRoot()
        val root = root()

        assertTrue("the photo $photo is under the title $title", photo.top >= title.bottom)
        assertTrue("the photo $photo is above Found at $foundAt", photo.bottom <= foundAt.top)
        assertTrue("and above the identification $identification", photo.bottom <= identification.top)
        val margins = 16.dp * 2
        assertTrue("the photo is the page's width less its margins: ${photo.width} against ${root.width - margins}", near(photo.width, root.width - margins))
        assertTrue("centred: ${photo.centreX} against ${root.centreX}", near(photo.centreX, root.centreX))
        val aspect = photo.width / photo.height
        assertTrue("whole, in its own 2:1 shape, not cropped to another: ${photo.width} x ${photo.height} is $aspect", abs(aspect - 2f) < 0.03f)
    }

    /**
     * A photo taller than the page can show at full width (here 1:2, which at 328 dp wide would be 656 dp tall) is drawn
     * at the 480 dp cap and narrowed to keep its shape, centred: whole, not cropped to fill the width.
     */
    @Test
    fun `a tall photo is drawn whole at the height cap, narrowed to its own shape and centred, not cropped to the width`() {
        setScreen(listOf(photo("tall", 200, 400)))

        val photo = bounds(findPagePhotoTag("tall"))
        val root = root()
        assertTrue("at the cap: ${photo.height}", near(photo.height, 480.dp))
        assertTrue("narrowed to its 1:2 shape: ${photo.width}", near(photo.width, 240.dp))
        assertTrue("centred: ${photo.centreX} against ${root.centreX}", near(photo.centreX, root.centreX))
    }

    /**
     * RECORD -874 (the planner's call): in a short landscape window the cap is 60% of the window's height, not 480 dp,
     * so the title and the find's details stay in view. Here 384 dp tall: the 2:1 photo, 304 dp tall at the 608 dp
     * width, is drawn 230.4 dp tall and 460.8 dp wide, centred, and "Found at" and the identification end on screen.
     */
    @Test
    @Config(qualifiers = "w640dp-h384dp-land-xhdpi")
    fun `in a short landscape window the photo is capped at 60 percent of the window height, with the title and details in view`() {
        setScreen(listOf(photo("wide", 400, 200)))

        val photo = bounds(findPagePhotoTag("wide"))
        val root = root()
        // 60% of the configured 384 dp window height.
        val cap = 384.dp * 0.6f
        assertTrue("capped at 60% of the window height, $cap: ${photo.height}", near(photo.height, cap))
        assertTrue("narrowed to its 2:1 shape: ${photo.width}", near(photo.width, cap * 2))
        assertTrue("centred: ${photo.centreX} against ${root.centreX}", near(photo.centreX, root.centreX))
        val title = bounds(FIND_PAGE_TITLE_TAG)
        val foundAt = composeRule.onNodeWithText("Found at", substring = true).getUnclippedBoundsInRoot()
        val identification = composeRule.onNodeWithText("Your own identification", substring = true).getUnclippedBoundsInRoot()
        assertTrue("the title is on screen: $title", title.top >= root.top && title.bottom <= root.bottom)
        assertTrue("Found at is on screen: $foundAt, window ends at ${root.bottom}", foundAt.bottom <= root.bottom)
        assertTrue("the identification is on screen: $identification, window ends at ${root.bottom}", identification.bottom <= root.bottom)
    }

    @Test
    fun `several photos sit in a row with the first centred and the next peeking in, and a swipe brings the next to the centre`() {
        setScreen(listOf(photo("a", 400, 200), photo("b", 400, 200), photo("c", 400, 200)))
        val root = root()

        val first = bounds(findPagePhotoTag("a"))
        val second = bounds(findPagePhotoTag("b"))
        assertTrue("the first photo is centred: ${first.centreX} against ${root.centreX}", near(first.centreX, root.centreX))
        assertTrue("the next photo starts after the first: $second, $first", second.left > first.right)
        assertTrue("and peeks in from the side, its start on screen: ${second.left} against ${root.right}", second.left < root.right)
        val aspect = first.width / first.height
        assertTrue("each photo is whole, in its own 2:1 shape: $aspect", abs(aspect - 2f) < 0.03f)

        // A deliberate, unhurried swipe of a bit more than half a page, so the pager settles on the next page by distance
        // rather than flinging. The default swipeLeft runs the row's whole width in 200 ms, more than a page plus a fling,
        // and on CI landed on the third photo (run 38167052604, "-92.0.dp against 180.0.dp").
        composeRule.onNodeWithTag(FIND_PAGE_PHOTOS_TAG).performTouchInput {
            swipeLeft(startX = width * 0.8f, endX = width * 0.2f, durationMillis = 600)
        }
        composeRule.waitForIdle()

        val secondAfter = bounds(findPagePhotoTag("b"))
        assertTrue("after a swipe the second photo is centred: ${secondAfter.centreX} against ${root.centreX}", near(secondAfter.centreX, root.centreX))
    }

    @Test
    fun `a real touch on a photo in the row opens the full-screen viewer`() {
        setScreen(listOf(photo("a", 400, 200), photo("b", 400, 200)))

        val photo = bounds(findPagePhotoTag("a"))
        for ((fx, fy) in listOf(0.15f to 0.3f, 0.5f to 0.5f, 0.85f to 0.7f)) {
            val x = photo.left + photo.width * fx
            val y = photo.top + photo.height * fy
            composeRule.onRoot().performTouchInput { click(Offset(x.toPx(), y.toPx())) }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertExists()
            composeRule.onNode(hasContentDescription("Close photo")).performTouchInput { click() }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()
        }
    }
}

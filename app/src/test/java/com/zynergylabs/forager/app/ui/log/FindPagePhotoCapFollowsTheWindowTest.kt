package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.ui.adaptive.FixedWindowInfo
import com.zynergylabs.forager.app.ui.adaptive.statedSize
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import org.junit.After
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
import org.robolectric.annotation.GraphicsMode

/**
 * Lint dispatch 2026-10-17-03, Phase 2 (RECORD -1195), ConfigurationScreenWidthHeight at `LogEntryReportScreen`'s
 * photo cap: the find page's photo is capped at 60% of the **window's** height (RECORD -874), read from
 * `LocalWindowInfo`, not from `Configuration.screenHeightDp`.
 *
 * The configuration says an 800 dp tall phone (this class's qualifiers); the window provided says 300 dp. A 1:2 photo
 * is then drawn at 60% of 300 dp, 180 dp tall. Before the fix it was drawn at the 480 dp cap that 60% of 800 dp
 * allows. Native graphics and real PNGs, as in [FindPagePhotosTest], so the photo has its real shape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FindPagePhotoCapFollowsTheWindowTest {

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

    @Test
    fun `the photo cap is 60 percent of the window's height, not the configuration's`() {
        val tall = photo("tall", 200, 400)
        var configurationHeight = -1
        composeRule.setContent {
            configurationHeight = LocalConfiguration.current.statedSize().height.value.toInt()
            val window = FixedWindowInfo(LocalWindowInfo.current, DpSize(360.dp, 300.dp), LocalDensity.current.density)
            CompositionLocalProvider(LocalWindowInfo provides window) {
                LogEntryReportScreen(entry = find.copy(photos = listOf(tall)), onEdit = {}, onDeleteEntry = {}, onBack = {})
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        assertEquals("the configuration still says 800 dp", 800, configurationHeight)
        val height = composeRule.onNodeWithTag(findPagePhotoTag("tall")).getUnclippedBoundsInRoot().height
        val cap = 300.dp * 0.6f
        assertTrue("the photo is drawn at 60% of the 300 dp window, $cap, not at 480 dp: $height", abs((height - cap).value) <= 1f)
    }
}

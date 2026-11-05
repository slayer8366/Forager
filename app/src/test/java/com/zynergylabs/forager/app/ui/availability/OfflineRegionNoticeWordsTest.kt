package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.map.TRAIL_STYLE_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_V3_URL
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * The older-style notice's words (dispatch 2026-11-04-09 A8; RECORD -2112, the owner: "Look and hill shading
 * (Recommended)"), through [OfflineRegionsSection], the list Offline maps draws, at the S22's portrait window
 * (`w384dp-h823dp`). An area downloaded against street-v3, as both the S22's are, shows them; a trail-v4 area shows none.
 * Only the words changed: when the notice shows (`regionHasOlderMapStyle`) did not.
 *
 * The two layout cases are a record for the report, not a gate on the wrap: the owner's option said "It's longer, so it
 * may wrap to two lines in the row". Each asserts that its case ran at the scale it names and that the words are laid
 * out whole, none cut off, and prints how many lines they take. The section is composed at the window's full width, as
 * the Records tab gives it (`ui/log/RecordsTab.kt`); the whole screen is not composed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class OfflineRegionNoticeWordsTest {
    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val words = "Older map style. Download again for the new look and hill shading."
    private var fontScaleSeen: Float? = null

    private fun region(id: Long, styleUrl: String) = OfflineRegionSummary(
        id = id, name = "Region $id", region = Region(40.1, -71.5, 5), minZoom = 10.0, maxZoom = 15.0,
        tileCount = 100, sizeBytes = 2_000_000L, createdAtEpochMillis = 1_000L, styleUrl = styleUrl,
    )

    private fun show() = composeRule.setContent {
        ForagerTheme {
            fontScaleSeen = LocalDensity.current.fontScale
            OfflineRegionsSection(
                regions = listOf(region(1, TRAIL_STYLE_V3_URL), region(2, TRAIL_STYLE_URL)),
                tilesUsed = 0, errorMessage = null, staleThresholdDays = 30, distanceUnit = DistanceUnit.MILES,
                nowEpochMillis = 2_000L, onDeleteOfflineRegion = {}, onOpenRegionDetails = null,
            )
        }
    }

    @Test
    fun `an area downloaded in an older style shows the owner's words, and a trail-v4 area shows none`() {
        show()

        composeRule.onNodeWithTag(offlineRegionOlderStyleTag(1)).assertIsDisplayed().assertTextEquals(words)
        composeRule.onNodeWithTag(offlineRegionOlderStyleTag(2)).assertDoesNotExist()
        assertEquals("one row shows them", 1, composeRule.onAllNodesWithText(words).fetchSemanticsNodes().size)
    }

    @Test
    fun `at the default text size the words are laid out whole, and the lines they take are stated`() {
        show()
        assertEquals("this case runs at the default text size", 1f, fontScaleSeen ?: 0f, 0.001f)
        measure("fontScale=1.0")
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `at the largest text size, 2_0, the words are laid out whole, and the lines they take are stated`() {
        show()
        assertEquals("this case runs at font scale 2.0", 2f, fontScaleSeen ?: 0f, 0.001f)
        measure("fontScale=2.0")
    }

    private fun measure(case: String) {
        val node = composeRule.onNodeWithTag(offlineRegionOlderStyleTag(1)).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        val text = layout.layoutInput.text.text
        val width = with(composeRule.density) { node.size.width.toDp() }
        val lines = (0 until layout.lineCount).map { text.substring(layout.getLineStart(it), layout.getLineEnd(it)) }
        println("MEASURED $case: the notice takes ${layout.lineCount} line(s) in a $width box: $lines")
        assertEquals(words, text)
        assertFalse("$case: nothing cut off", layout.hasVisualOverflow)
        assertEquals("$case: every character laid out", words.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
    }
}

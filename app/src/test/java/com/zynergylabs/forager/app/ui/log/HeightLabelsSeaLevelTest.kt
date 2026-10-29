package com.zynergylabs.forager.app.ui.log

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.zynergylabs.forager.app.domain.NoSeaLevelConverter
import com.zynergylabs.forager.app.domain.SeaLevelConverter
import com.zynergylabs.forager.app.domain.heightProfileOf
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.ui.availability.FixedOffsetSeaLevelConverter
import com.zynergylabs.forager.app.ui.format.LocalSeaLevelConverter
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-28-13, item 5 and Amendments 1 and 2 (RECORD -1793, -1794): the height chart's highest and lowest
 * labels read above sea level; an imported track's as the file gave them; and on a phone that cannot convert, the
 * chart has no numbers (the owner: "Chart with no numbers (Recommended)"), never the raw ones. Through the composable
 * the journal entry and the Records track sheet both draw ([EntryHeightProfile]), with the profile the app builds
 * ([heightProfileOf]) from a made-up walk: twelve points from 100 m to 155 m, metric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HeightLabelsSeaLevelTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun walk(importedAt: Long? = null) = Track(
        id = "T1",
        name = null,
        startedAtEpochMillis = T0,
        endedAtEpochMillis = T0 + 11_000L,
        points = (0 until 12).map { i -> TrackPoint(45.52 + i * 0.0005, -122.68, 100.0 + i * 5.0, 5f, T0 + i * 1_000L) },
        importedAtEpochMillis = importedAt,
    )

    private fun show(track: Track, converter: SeaLevelConverter) {
        composeRule.setContent {
            CompositionLocalProvider(LocalSeaLevelConverter provides converter) {
                ForagerTheme { EntryHeightProfile(profile = heightProfileOf(listOf(track)), distanceUnit = DistanceUnit.KILOMETERS) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun labelShown(text: String) =
        composeRule.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(ENTRY_HEIGHT_PROFILE_TAG))).fetchSemanticsNodes().size

    @Test
    fun `a recorded walk's highest and lowest read above sea level`() {
        show(walk(), FixedOffsetSeaLevelConverter(21.6))

        assertEquals("highest, 155 + 21.6", 1, labelShown("177 m"))
        assertEquals("lowest, 100 + 21.6", 1, labelShown("122 m"))
        assertEquals("not the stored heights", 0, labelShown("155 m") + labelShown("100 m"))
    }

    @Test
    fun `an imported walk's labels read as the file gave them`() {
        show(walk(importedAt = T0 + 60_000L), FixedOffsetSeaLevelConverter(21.6))

        assertEquals(1, labelShown("155 m"))
        assertEquals(1, labelShown("100 m"))
        assertEquals("not converted a second time", 0, labelShown("177 m") + labelShown("122 m"))
    }

    @Test
    fun `on a phone that cannot convert the chart is drawn with no height numbers`() {
        show(walk(), NoSeaLevelConverter)

        assertEquals("the chart is still there", 1, composeRule.onAllNodes(hasTestTag(ENTRY_HEIGHT_PROFILE_TAG)).fetchSemanticsNodes().size)
        assertEquals("no raw height as a label", 0, labelShown("155 m") + labelShown("100 m"))
        val described = composeRule.onAllNodesWithContentDescription("Height profile: over", substring = true).fetchSemanticsNodes()
        assertEquals(1, described.size)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("highest", substring = true).fetchSemanticsNodes().size)
    }

    private companion object {
        const val T0 = 1_790_000_000_000L
    }
}

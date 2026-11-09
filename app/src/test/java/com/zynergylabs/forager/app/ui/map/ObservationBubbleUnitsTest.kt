package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Dispatch 2026-09-28-549, Amendment 1 (RECORD -557): the sighting bubble's accuracy note follows
 * Units. Driven through [MapBubbleLayer], the layer every map host composes, with the unit carried in
 * [MapRecordSources] as `AvailabilityScreen` and `JournalTab` build it, so the test covers the path
 * from the host's setting to the bubble rather than [ObservationBubble] handed a unit by hand. (The
 * screen-level bubble tests in `AvailabilityScreenMapIconStackTest` are `@Ignore`d for a harness
 * problem recorded in docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ObservationBubbleUnitsTest {

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

    private val sighting = Sighting(
        observationId = 42L,
        taxonId = 47348L,
        scientificName = "Cantharellus formosus",
        commonName = "Chanterelle",
        lat = 45.33,
        lng = -122.64,
        observedOn = LocalDate.of(2026, 8, 1),
        photoUrl = null,
        positionalAccuracyMeters = 12,
    )

    private fun accuracyShownUnder(unit: DistanceUnit): String {
        composeRule.setContent {
            ForagerTheme {
                Box(Modifier.fillMaxSize()) {
                    MapBubbleLayer(
                        tapped = TappedMapThing(MapBubbleTarget.SightingTarget(sighting), Offset(200f, 400f), 0f),
                        onDismiss = {},
                        sources = MapRecordSources(distanceUnit = unit),
                        forecast = null,
                        onViewSightingOnINaturalist = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag("observation-bubble-accuracy", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
    }

    @Test
    fun `under the imperial setting the bubble gives the accuracy in feet`() {
        // 12 / 0.3048 = 39.37
        assertEquals("±39 ft accuracy", accuracyShownUnder(DistanceUnit.MILES))
    }

    @Test
    fun `under the metric setting the bubble gives the accuracy in metres, as before`() {
        assertEquals("±12 m accuracy", accuracyShownUnder(DistanceUnit.KILOMETERS))
    }
}

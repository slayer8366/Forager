package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.map.MapSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * RECORD -736. The owner, verbatim: "Yes for landscape I'm willing to accept a shorter search bar. The compass strip must
 * remain the same height as portrait though and that's important".
 *
 * Through the real [AvailabilityScreen], native graphics, with a fix, a fixed compass and a search showing (so Clear shows):
 * the compass strip's height in portrait (384 x 823) and in short landscape (780 x 360 and 823 x 384, both rotations), at fonts
 * 1.0 and 2.0, each against the strip's own content height at that font, measured on the same screen: the larger of its
 * three-dot button and its laid-out readout line (the coordinates text). Portrait and landscape equal the same figure, so they
 * equal each other; the portrait classes are the positive control that the figure is the portrait strip's. (A first draft
 * computed the line from the 20 sp line height; portrait at font 2.0 measured 37.33 dp against its 36, and failed, which is
 * how that was caught.) In landscape
 * the search bar equals the strip. At font 2.0 the bar's field, resting text and Clear are measured and printed (what is cut).
 */
abstract class StripPortraitHeightTests(private val fontScale: Float) {
    val composeRule = createComposeRule()
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 3_000.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }
    private var rotationSeen: Int? = null

    protected fun setScreen(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix, region = Region(lat = 45.52, lng = -122.68, radiusKm = 15)),
                compassProvider = Compass315,
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { t + 1_000L },
                mapSlot = map,
                searchInputs = AvailabilityScreenSearchInputs(
                    onUseCurrentLocation = {},
                    onManualLatChanged = {},
                    onManualLngChanged = {},
                    onSearchManualCoordinates = {},
                    onRadiusChanged = {},
                    onMonthSelected = {},
                    onMapTabSelected = {},
                    onSeasonalTabSelected = {},
                    onTaxonSearchQueryChanged = {},
                    onTaxonSearchResultSelected = {},
                    onDismissTaxonSuggestions = {},
                    onReopenTaxonSuggestions = {},
                    onPlaceTripPin = { _, _, _ -> },
                    onDeletePlannedTrip = {},
                    onRecentSearchSelected = {},
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the screen must actually see the rotation this test is about", rotation, rotationSeen)
        assertEquals("the font scale this test is about", fontScale, composeRule.density.fontScale, 0.01f)
    }

    protected fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    protected fun DpRect.d() = "[%.2f, %.2f][%.2f, %.2f] (%.2f x %.2f)".format(left.value, top.value, right.value, bottom.value, (right - left).value, (bottom - top).value)

    /** The strip's own content height at this font, as laid out: its three-dot button, or its readout line if taller. */
    protected fun expectedStripHeight(): Float {
        val button = bounds(MAP_QUICK_SETTINGS_BUTTON_TAG)
        val line = bounds(COMPASS_STRIP_COORDINATES_TAG)
        return maxOf((button.bottom - button.top).value, (line.bottom - line.top).value)
    }

    protected fun assertStripHeight(where: String) {
        val strip = bounds(STRIP_TAG)
        println("MEASURED font $fontScale $where: strip ${strip.d()}, its content height ${expectedStripHeight()}")
        assertEquals("$where, font $fontScale: the strip is its portrait height", expectedStripHeight(), (strip.bottom - strip.top).value, 0.5f)
    }

    protected fun assertLandscape(rotation: Int) {
        setScreen(rotation)
        val strip = bounds(STRIP_TAG)
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val field = bounds(ACTIVE_SEARCH_SUMMARY_TAG)
        val clear = bounds(SEARCH_BAR_CLEAR_TAG)
        val texts = composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(SEARCH_ENTRY_BAR_TAG)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().map { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: ""
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layout = results.single()
            val box = with(composeRule.density) { DpRect(node.boundsInRoot.left.toDp(), node.boundsInRoot.top.toDp(), node.boundsInRoot.right.toDp(), node.boundsInRoot.bottom.toDp()) }
            "<$text> node ${box.d()}, text ${with(composeRule.density) { layout.size.height.toDp().value }} dp tall, lines ${layout.lineCount}, visible ${layout.getLineEnd(0, visibleEnd = true)} of ${text.length}"
        }
        println("MEASURED font $fontScale landscape rotation $rotation: bar ${bar.d()}, field ${field.d()}, Clear ${clear.d()}; texts $texts")
        assertStripHeight("landscape rotation $rotation")
        assertEquals("landscape rotation $rotation, font $fontScale: the bar is the strip's height", (strip.bottom - strip.top).value, (bar.bottom - bar.top).value, 0.5f)
    }

    private object Compass315 : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(315f, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    protected companion object {
        const val STRIP_TAG = "compass-elevation-strip"
    }
}

abstract class StripPortraitHeightPortraitTests(fontScale: Float) : StripPortraitHeightTests(fontScale) {
    @Test fun `in portrait the strip is its content height`() {
        setScreen(Surface.ROTATION_0)
        assertStripHeight("portrait")
    }
}

abstract class StripPortraitHeightLandscapeTests(fontScale: Float) : StripPortraitHeightTests(fontScale) {
    @Test fun `at ROTATION_90 the landscape strip is its portrait height and the bar equals it`() = assertLandscape(Surface.ROTATION_90)

    @Test fun `at ROTATION_270 the landscape strip is its portrait height and the bar equals it`() = assertLandscape(Surface.ROTATION_270)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StripHeightPortraitFont1Test : StripPortraitHeightPortraitTests(1.0f)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StripHeightPortraitFont2Test : StripPortraitHeightPortraitTests(2.0f)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 1.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StripHeight780Font1Test : StripPortraitHeightLandscapeTests(1.0f)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StripHeight780Font2Test : StripPortraitHeightLandscapeTests(2.0f)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StripHeight823Font1Test : StripPortraitHeightLandscapeTests(1.0f)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StripHeight823Font2Test : StripPortraitHeightLandscapeTests(2.0f)

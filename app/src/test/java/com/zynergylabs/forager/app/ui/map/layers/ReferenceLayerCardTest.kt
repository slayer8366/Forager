package com.zynergylabs.forager.app.ui.map.layers

import android.app.Application
import android.content.ComponentName
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailsDatesStore
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailsManifestTest
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailsSourceDates
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailsSources
import com.zynergylabs.forager.app.ui.map.MAP_LAYERS_SHEET_TAG
import com.zynergylabs.forager.app.ui.map.MAPS_TAB_OVERLAYS
import com.zynergylabs.forager.app.ui.map.MapLayersSheet
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.mapLayerSwitchTag
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.robolectric.shadows.ShadowDialog

/**
 * Step 8d (dispatch 2026-10-26-24; the owner's card, RECORD -1717): Layers > the (i) beside "Official trails" > the
 * card > Back > the Layers sheet again. Every touch is a real one at coordinates inside the target's own bounds (CLAUDE.md,
 * "A semantic `performClick` asserts wiring, not routing"), and Back is a real Back key on the sheet's own window.
 * The dates come through the real [ReferenceTrailsSources] over a fixture manifest; nothing touches the network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class ReferenceLayerCardTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val visibilityWrites = mutableListOf<String>()
    private var dismissals = 0
    private var fetches = 0

    @After
    fun tearDown() = scope.cancel()

    private class Store(var kept: ReferenceTrailsSourceDates?) : ReferenceTrailsDatesStore {
        override suspend fun read(): Result<ReferenceTrailsSourceDates?> = Result.success(kept)
        override suspend fun write(dates: ReferenceTrailsSourceDates): Result<Unit> = Result.success(Unit).also { kept = dates }
    }

    private fun setSheet(kept: ReferenceTrailsSourceDates?, answer: () -> Result<String>) {
        val sources = ReferenceTrailsSources(source = { fetches++; answer() }, store = Store(kept), scope = scope, log = { _, _ -> })
        composeRule.setContent {
            ForagerTheme(darkTheme = false) {
                ProvideReferenceTrailsSources(sources) {
                    MapLayersSheet(
                        mapMode = MapMode.TRAIL,
                        onMapModeSelected = {},
                        overlays = MAPS_TAB_OVERLAYS,
                        colourFields = emptyList(),
                        state = com.zynergylabs.forager.app.ui.map.layers.MapLayersState.DEFAULT,
                        onVisibilityChanged = { id, visible -> visibilityWrites += "$id $visible" },
                        onOpacityChanged = { _, _ -> },
                        onColourFieldMoved = { _, _ -> },
                        onDismiss = { dismissals++ },
                        referenceTrails = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun exists(tag: String) = composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun layersTitleShown() =
        composeRule.onAllNodes(hasText("Layers") and hasAnyAncestor(hasTestTag(MAP_LAYERS_SHEET_TAG))).fetchSemanticsNodes().isNotEmpty()

    private fun touchInfo(fraction: Offset) {
        composeRule.onNodeWithTag(REFERENCE_TRAILS_INFO_TAG).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(REFERENCE_TRAILS_INFO_TAG).performTouchInput { click(Offset(width * fraction.x, height * fraction.y)) }
        composeRule.waitForIdle()
    }

    private fun openCard() = touchInfo(Offset(0.5f, 0.5f))

    /** A real Back key, down and up, on the sheet's own window (the latest dialog shown), as `RecordDetailsSheetTest` presses it. */
    private fun pressBackOnSheet() {
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue("the sheet's window must be showing to press Back on", dialog != null && dialog.isShowing)
        composeRule.runOnUiThread {
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        composeRule.waitForIdle()
    }

    /** Every text on the card, top to bottom. */
    private fun cardTexts(): List<String> =
        composeRule.onAllNodes(hasAnyAncestor(hasTestTag(REFERENCE_LAYER_CARD_TAG)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }

    @Test
    fun `a real touch anywhere across the (i) opens the card, and Back closes only the card`() {
        setSheet(kept = null) { Result.failure(IOException("offline")) }
        // A finger is not a point: the (i)'s four corners' insides and its centre, each a real touch.
        val samples = listOf(Offset(0.15f, 0.15f), Offset(0.85f, 0.15f), Offset(0.5f, 0.5f), Offset(0.15f, 0.85f), Offset(0.85f, 0.85f))
        samples.forEachIndexed { i, point ->
            assertTrue("the list shows before the touch at $point", layersTitleShown() && !exists(REFERENCE_LAYER_CARD_TAG))
            touchInfo(point)
            assertTrue("a touch at $point of the (i) opens the card", exists(REFERENCE_LAYER_CARD_TAG))
            composeRule.onNodeWithTag(REFERENCE_LAYER_CARD_TAG).assertIsDisplayed()
            assertTrue("the card is shown in place of the list", !layersTitleShown() && !exists(mapLayerSwitchTag(MapLayerIds.REFERENCE_FS_BAND)))
            assertEquals("each opening asks for the manifest", i + 1, fetches)

            pressBackOnSheet()
            assertTrue("Back closes the card (after the touch at $point)", !exists(REFERENCE_LAYER_CARD_TAG))
            assertTrue("and the Layers sheet stays, with its list", exists(MAP_LAYERS_SHEET_TAG) && layersTitleShown())
            composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.REFERENCE_FS_BAND)).performScrollTo().assertIsDisplayed()
        }
        assertEquals("Back never dismissed the sheet", 0, dismissals)
        assertEquals("no touch on the (i) reached the row's switch", emptyList<String>(), visibilityWrites)
    }

    @Test
    fun `a second Back, with the card closed, closes the sheet as before`() {
        setSheet(kept = null) { Result.failure(IOException("offline")) }
        openCard()
        pressBackOnSheet()
        assertEquals(0, dismissals)
        pressBackOnSheet()
        assertEquals(1, dismissals)
    }

    @Test
    fun `the card's text with both dates known, in the app's date style`() {
        setSheet(kept = null) { Result.success(ReferenceTrailsManifestTest.manifest()) }
        openCard()
        assertEquals(
            listOf(
                "Official trails",
                "Forest Service and BLM trail records, drawn as a faint violet band under the trail map's lines.",
                "Where the agency and the trail map agree, the trail sits inside the band. A band with no trail is an official trail the trail map lacks.",
                "Routes follow the trail map, not this band.",
                "Sources: Forest Service data from Oct 24, 2026 · CC BY 4.0. BLM data from Oct 25, 2026 · public domain.",
            ),
            cardTexts(),
        )
        val sources = composeRule.onNodeWithTag(REFERENCE_LAYER_CARD_SOURCES_TAG).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val bold = sources.spanStyles.single()
        assertEquals("only the lead is bold", "Sources:", sources.text.substring(bold.start, bold.end))
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
    }

    @Test
    fun `the card's text with no copy at all`() {
        setSheet(kept = null) { Result.failure(IOException("offline")) }
        openCard()
        assertEquals(
            listOf(
                "Official trails",
                "Forest Service and BLM trail records, drawn as a faint violet band under the trail map's lines.",
                "Where the agency and the trail map agree, the trail sits inside the band. A band with no trail is an official trail the trail map lacks.",
                "Routes follow the trail map, not this band.",
                "Sources: Forest Service · CC BY 4.0. BLM · public domain.",
                "Dates show once you've been online.",
            ),
            cardTexts(),
        )
    }

    @Test
    fun `the card's text with one source's date missing`() {
        setSheet(kept = null) { Result.success(ReferenceTrailsManifestTest.manifest(sources = listOf(ReferenceTrailsManifestTest.BLM_SOURCE))) }
        openCard()
        assertEquals(
            "Sources: Forest Service · CC BY 4.0. BLM data from Oct 25, 2026 · public domain.",
            cardTexts()[4],
        )
        assertEquals("a copy exists, so no 'once you've been online' line", 5, cardTexts().size)
    }

    @Test
    fun `offline, the card shows the kept copy's dates`() {
        setSheet(kept = ReferenceTrailsSourceDates(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2))) { Result.failure(IOException("offline")) }
        openCard()
        assertEquals(1, fetches)
        assertEquals("Sources: Forest Service data from Sep 1, 2026 · CC BY 4.0. BLM data from Sep 2, 2026 · public domain.", cardTexts()[4])
        assertEquals(5, cardTexts().size)
    }
}

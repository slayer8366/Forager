package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-04-07, the map type chips (RECORD -2089; Amendment 1, -2093; Amendment 2, -2094). The owner: "Let's
 * design a dark outline for the map style chips in day mode. And have the night mode use a white outline as shown", and
 * "Rust is a red color. That's meant for night vision".
 *
 * - **Day** (A2): the selected chip keeps Material's pale #F6DFC8 fill and #3A2008 label, and gains a 1 dp Bark #3B2E24
 *   outline, the day `onSurface`.
 * - **Night** (A3, A6, A7): the selected chip is rust red #A2612C, with Material's own pale #F6DFC8 label (the owner chose
 *   "Pale #F6DFC8") and a 1 dp #EDE3D0 outline, the night `onSurface`.
 * - **Unchanged** (A4, A10): the unselected chips keep Material's 1 dp `outlineVariant` outline and no fill; the grey
 *   "chosen, but offline" chip keeps its colours and has no outline; every chip's fill stays solid, the owner's exception
 *   to the 80% map chrome rule for the map type chips only ("Keep them solid as they are today").
 *
 * Every colour is read from the drawn pixels, through both of the panel's real entry points ([MapTypePanelChipHarness]),
 * and written out as the ruled hex, not read from the code under test, so a chip drawn any other colour fails. Each case
 * is its own test, so the JUnit XML names each failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapTypePanelChipOutlineTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val harness = MapTypePanelChipHarness(composeRule)

    private enum class EntryPoint(val label: String) { MAPS_TAB("Maps tab"), ENTRY_MAP("entry map") }

    /** One theme's ruled colours. [onSurface] is both the selected chip's outline and the grey chip's label. */
    private class Look(
        val name: String,
        val dark: Boolean,
        val fill: Color,
        val label: Color,
        val onSurface: Color,
        val outlineVariant: Color,
    )

    private val day = Look(
        name = "day",
        dark = false,
        fill = Color(0xFFF6DFC8), // secondaryContainer, MushroomContainerLight
        label = Color(0xFF3A2008), // onSecondaryContainer, MushroomOnContainerLight
        onSurface = Color(0xFF3B2E24), // Bark
        outlineVariant = Color(0xFFD5CEBF), // OutlineVariantLight
    )

    private val night = Look(
        name = "night",
        dark = true,
        fill = Color(0xFFA2612C), // MushroomDeep, the rust red
        label = Color(0xFFF6DFC8), // the dark scheme's onSecondaryContainer, MushroomContainerLight
        onSurface = Color(0xFFEDE3D0), // Cream
        outlineVariant = Color(0xFF444444), // OutlineVariantDark
    )

    private fun open(entry: EntryPoint, look: Look, withDownloadedArea: Boolean = false) {
        val regions = if (withDownloadedArea) listOf(MapTypePanelChipHarness.downloadedRegion()) else emptyList()
        when (entry) {
            EntryPoint.MAPS_TAB -> harness.setMapsTab(look.dark, regions)
            EntryPoint.ENTRY_MAP -> harness.setEntryMap(look.dark, regions)
        }
        harness.openPanel()
    }

    private fun px(dp: Float): Int = with(composeRule.density) { dp.dp.roundToPx() }

    private fun assertSelectedFill(entry: EntryPoint, look: Look) {
        open(entry, look)
        val where = "${entry.label}, ${look.name}"
        composeRule.onNodeWithTag(mapModeChipTag(MapMode.TRAIL)).assertIsSelected()
        assertEquals("$where: the selected chip's drawn fill, solid", look.fill.hex(), harness.drawnFill(MapMode.TRAIL).hex())
    }

    private fun assertSelectedLabel(entry: EntryPoint, look: Look) {
        open(entry, look)
        val where = "${entry.label}, ${look.name}"
        val pixels = harness.labelPixels(MapMode.TRAIL)
        val exact = pixels.count { it == look.label }
        val white = pixels.count { it == PURE_WHITE }
        val fill = harness.drawnFill(MapMode.TRAIL)
        println("MEASURED $where: label ${look.label.hex()}, $exact exact pixels of ${pixels.size}; on the drawn fill ${fill.hex()} %.2f:1".format(contrastRatio(look.label, fill)))
        assertTrue("$where: the selected chip's label is drawn ${look.label.hex()}: $exact exact pixels of ${pixels.size}, at least $LABEL_FLOOR expected", exact >= LABEL_FLOOR)
        assertEquals("$where: no #FFFFFF pixel in the selected chip's label", 0, white)
    }

    private fun assertSelectedOutline(entry: EntryPoint, look: Look) {
        open(entry, look)
        val where = "${entry.label}, ${look.name}"
        val width = harness.outlineWidthPx()
        val (panel, background) = harness.panelAndBackground()
        for ((side, edge) in harness.edges(MapMode.TRAIL)) {
            val run = runOf(edge, look.onSurface)
            assertNotNull("$where, $side: a ${look.onSurface.hex()} outline is drawn; the edge reads ${edge.map { it.hex() }}", run)
            run!!
            assertEquals("$where, $side: the outline is ceil(1 dp) = $width px wide; the edge reads ${edge.map { it.hex() }}", width, run.last - run.first + 1)
            assertTrue("$where, $side: the outline is at the chip's own edge, ${run.first} px in", run.first <= px(2f))
            val inside = edge.getOrNull(run.last + 1)
            if (side == "left edge" && inside != null) {
                println(
                    "MEASURED $where: outline ${look.onSurface.hex()} on the chip's drawn fill ${inside.hex()} %.2f:1; on the panel as drawn ${panel.hex()} (over ${background.hex()}, this test's own background) %.2f:1"
                        .format(contrastRatio(look.onSurface, inside), contrastRatio(look.onSurface, panel)),
                )
            }
        }
    }

    private fun assertUnselected(entry: EntryPoint, look: Look) {
        open(entry, look)
        val width = harness.outlineWidthPx()
        MapMode.entries.filter { it != MapMode.TRAIL }.forEach { mode ->
            val where = "${entry.label}, ${look.name}, $mode (unselected)"
            composeRule.onNodeWithTag(mapModeChipTag(mode)).assertIsNotSelected()
            for ((side, edge) in harness.edges(mode)) {
                val run = runOf(edge, look.outlineVariant)
                assertNotNull("$where, $side: Material's ${look.outlineVariant.hex()} outline; the edge reads ${edge.map { it.hex() }}", run)
                run!!
                assertEquals("$where, $side: ceil(1 dp) = $width px wide; the edge reads ${edge.map { it.hex() }}", width, run.last - run.first + 1)
                assertTrue("$where, $side: no ${look.onSurface.hex()} outline; the edge reads ${edge.map { it.hex() }}", edge.none { it == look.onSurface })
            }
        }
    }

    /** Topo chosen by touch with signal, then no signal with a downloaded area: Trail Map is drawn in its place, Topo grey. */
    private fun assertGrey(entry: EntryPoint, look: Look) {
        open(entry, look, withDownloadedArea = true)
        harness.chooseByTouch(MapMode.TOPOGRAPHIC)
        harness.noSignal.value = true
        composeRule.waitForIdle()
        harness.openPanel()
        val where = "${entry.label}, ${look.name}, grey Topo"
        composeRule.onNodeWithTag(mapModeChipTag(MapMode.TOPOGRAPHIC)).assertIsSelected()
        assertEquals("$where: the grey fill, solid", look.outlineVariant.hex(), harness.drawnFill(MapMode.TOPOGRAPHIC).hex())
        val exact = harness.labelPixels(MapMode.TOPOGRAPHIC).count { it == look.onSurface }
        assertTrue("$where: the label is drawn ${look.onSurface.hex()}: $exact exact pixels, at least $LABEL_FLOOR expected", exact >= LABEL_FLOOR)
        for ((side, edge) in harness.edges(MapMode.TOPOGRAPHIC)) {
            assertTrue("$where, $side: the grey fill reaches the edge; the edge reads ${edge.map { it.hex() }}", edge.contains(look.outlineVariant))
            assertTrue("$where, $side: no ${look.onSurface.hex()} outline (\"No, normal chip only\"); the edge reads ${edge.map { it.hex() }}", edge.none { it == look.onSurface })
        }
    }

    // Day (A2).

    @Test
    fun `Maps tab, day, the selected chip keeps its pale fill`() = assertSelectedFill(EntryPoint.MAPS_TAB, day)

    @Test
    fun `entry map, day, the selected chip keeps its pale fill`() = assertSelectedFill(EntryPoint.ENTRY_MAP, day)

    @Test
    fun `Maps tab, day, the selected chip keeps its dark label`() = assertSelectedLabel(EntryPoint.MAPS_TAB, day)

    @Test
    fun `entry map, day, the selected chip keeps its dark label`() = assertSelectedLabel(EntryPoint.ENTRY_MAP, day)

    @Test
    fun `Maps tab, day, the selected chip has a 1 dp Bark outline`() = assertSelectedOutline(EntryPoint.MAPS_TAB, day)

    @Test
    fun `entry map, day, the selected chip has a 1 dp Bark outline`() = assertSelectedOutline(EntryPoint.ENTRY_MAP, day)

    // Night (A3, A6, A7).

    @Test
    fun `Maps tab, night, the selected chip is filled rust red`() = assertSelectedFill(EntryPoint.MAPS_TAB, night)

    @Test
    fun `entry map, night, the selected chip is filled rust red`() = assertSelectedFill(EntryPoint.ENTRY_MAP, night)

    @Test
    fun `Maps tab, night, the selected chip's label is pale`() = assertSelectedLabel(EntryPoint.MAPS_TAB, night)

    @Test
    fun `entry map, night, the selected chip's label is pale`() = assertSelectedLabel(EntryPoint.ENTRY_MAP, night)

    @Test
    fun `Maps tab, night, the selected chip has a 1 dp cream outline`() = assertSelectedOutline(EntryPoint.MAPS_TAB, night)

    @Test
    fun `entry map, night, the selected chip has a 1 dp cream outline`() = assertSelectedOutline(EntryPoint.ENTRY_MAP, night)

    // Unchanged (A4).

    @Test
    fun `Maps tab, day, the unselected chips keep Material's own outline`() = assertUnselected(EntryPoint.MAPS_TAB, day)

    @Test
    fun `entry map, day, the unselected chips keep Material's own outline`() = assertUnselected(EntryPoint.ENTRY_MAP, day)

    @Test
    fun `Maps tab, night, the unselected chips keep Material's own outline`() = assertUnselected(EntryPoint.MAPS_TAB, night)

    @Test
    fun `entry map, night, the unselected chips keep Material's own outline`() = assertUnselected(EntryPoint.ENTRY_MAP, night)

    @Test
    fun `Maps tab, day, the grey chip keeps its colours and has no outline`() = assertGrey(EntryPoint.MAPS_TAB, day)

    @Test
    fun `entry map, day, the grey chip keeps its colours and has no outline`() = assertGrey(EntryPoint.ENTRY_MAP, day)

    @Test
    fun `Maps tab, night, the grey chip keeps its colours and has no outline`() = assertGrey(EntryPoint.MAPS_TAB, night)

    @Test
    fun `entry map, night, the grey chip keeps its colours and has no outline`() = assertGrey(EntryPoint.ENTRY_MAP, night)

    private companion object {
        val PURE_WHITE = Color(0xFFFFFFFF)

        /** Far fewer than a label's glyph cores at xxhdpi, far more than none: a label drawn another colour has none. */
        const val LABEL_FLOOR = 20
    }
}

package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * RECORD -750 (dispatch 2026-09-28-750), items 1, 2 and 4, through the real [AvailabilityScreen] and the real
 * [AvailabilityViewModel] ([RealSearchScreenRig]), every touch real. Each test starts in portrait and turns the phone to the
 * S22's landscape windows (780 x 360 and 823 x 384 dp) at ROTATION_90 or ROTATION_270 (the bar on the left at both since RECORD -956; was on the right at 270),
 * as the owner did.
 *
 * Item 1, the owner: "The drop down search panel has a gap between the top of the panel and the bottom of the bar. It should
 * sit flush against the bar to look continuous." Item 2: "The drop down search panel does not scroll to the bottom like it
 * does it portrait view"; "If the keyboard is floating keep it to the one side. The bar still should scroll up to reveal the
 * search button at the bottom". Item 4: the bar never drops out of line with the strip, whatever its text.
 *
 * Robolectric has no keyboard, so item 2 injects the keyboard's insets into the screen's own view, as the platform delivers
 * them: docked (a bottom inset, visible) and floating (visible, no inset; a floating keyboard reports none). Whether a real
 * keyboard reports those values, and where a floating one sits, is a device item.
 */
abstract class LandscapeSearchFixesTests(private val portrait: String, private val landscape: String) {

    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * The host declares MainActivity's configChanges (orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden),
     * so the turn reaches the same Activity, as on the phone, instead of recreating it (the MapViewportResizeTest precedent).
     */
    private val rotatesInPlace = object : org.junit.rules.ExternalResource() {
        override fun before() {
            val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
            val info = android.content.pm.ActivityInfo().apply {
                packageName = app.packageName
                name = ComponentActivity::class.java.name
                configChanges = android.content.pm.ActivityInfo.CONFIG_ORIENTATION or android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE or
                    android.content.pm.ActivityInfo.CONFIG_SCREEN_LAYOUT or android.content.pm.ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
                    android.content.pm.ActivityInfo.CONFIG_KEYBOARD_HIDDEN
            }
            Shadows.shadowOf(app.packageManager).addOrUpdateActivity(info)
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(rotatesInPlace).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun window(): DpRect = composeRule.onAllNodes(isRoot()).onFirst().getUnclippedBoundsInRoot()
    private fun DpRect.d() = "[%.2f, %.2f][%.2f, %.2f]".format(left.value, top.value, right.value, bottom.value)

    /** The portrait screen, then a turn to [rotation] in the landscape window. */
    private fun setScreenThenTurn(rotation: Int, beforeTurn: () -> Unit = {}) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        // The class's own qualifiers are the portrait window; setting them again here would recreate the activity.
        rig.setScreen()
        assertTrue("positive control: portrait first (window ${window().d()})", (window().bottom - window().top) > (window().right - window().left))
        beforeTurn()
        turn(rotation)
    }

    private fun turn(rotation: Int) {
        // Robolectric sets the display's rotation from the new orientation (ROTATION_90 for land) on every qualifier change,
        // so a turn to ROTATION_270 is the orientation change, then the rotation set and the same configuration dispatched
        // again to the screen's view, which reads the rotation on a configuration change (currentDisplayRotation). On the
        // phone that is one configuration change; the screen measured here is the state after it.
        composeRule.activityRule.scenario.onActivity { RuntimeEnvironment.setQualifiers(landscape) }
        rig.settle()
        if (rotation != Surface.ROTATION_90) {
            Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
            composeRule.runOnUiThread {
                val view = composeView(composeRule.activity.window.decorView) ?: error("no AndroidComposeView")
                // Equal configurations are not a change to Compose, so the long/notlong layout bit (nothing here reads it) is
                // flipped to make this one a change, as the real turn's own configuration is.
                val config = android.content.res.Configuration(composeRule.activity.resources.configuration)
                val long = config.screenLayout and android.content.res.Configuration.SCREENLAYOUT_LONG_MASK
                config.screenLayout = (config.screenLayout and android.content.res.Configuration.SCREENLAYOUT_LONG_MASK.inv()) or
                    (if (long == android.content.res.Configuration.SCREENLAYOUT_LONG_YES) android.content.res.Configuration.SCREENLAYOUT_LONG_NO else android.content.res.Configuration.SCREENLAYOUT_LONG_YES)
                view.dispatchConfigurationChanged(config)
            }
            rig.settle()
        }
        assertEquals("positive control: the display reads the rotation", rotation, ShadowDisplay.getDefaultDisplay().rotation)
        val w = window()
        assertTrue("positive control: landscape after the turn (window ${w.d()})", (w.right - w.left) > (w.bottom - w.top))
        assertTrue("positive control: the landscape strip is up", shown(STRIP_TAG))
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        // RECORD -956 (the owner: "One should look exactly line the other"): the bar is on the left at both rotations. Was, at ROTATION_270: assertEquals("positive control: at ROTATION_270 the bar is on the right", w.right.value, bar.right.value, 0.5f)
        assertEquals("positive control: the bar is on the left", w.left.value, bar.left.value, 0.5f)
    }

    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun closeDropdown() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        rig.settle()
        check(!rig.dropdownShown()) { "Back did not close the dropdown" }
    }

    // ── Item 1: the panel flush against the bar ──

    private fun assertFlush(rotation: Int) {
        setScreenThenTurn(rotation)
        rig.tapBar()
        assertTrue("positive control: the bar's tap opened the dropdown", rig.dropdownShown())
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        // Dispatch 2026-10-12-06: over the map the bar is the rounded pill, with no divider (RECORD -934); its own bottom is its
        // visible bottom edge. Was: the divider's bottom, asserted equal to the bar's.
        val panel = bounds(SEARCH_DROPDOWN_TAG)
        println("MEASURED -750 item 1 $landscape rotation $rotation: bar ${bar.d()}, panel ${panel.d()}, gap ${(panel.top - bar.bottom).value}")
        assertEquals("the panel's top is the pill's bottom, no gap", bar.bottom.value, panel.top.value, 0.5f)
        assertEquals("the panel keeps the bar's left edge", bar.left.value, panel.left.value, 0.5f)
        assertEquals("the panel keeps the bar's right edge", bar.right.value, panel.right.value, 0.5f)
    }

    @Test fun `item 1 at ROTATION_90 the open panel sits flush against the bar`() = assertFlush(Surface.ROTATION_90)

    @Test fun `item 1 at ROTATION_270 the open panel sits flush against the bar`() = assertFlush(Surface.ROTATION_270)

    // ── Item 2: the keyboard keeps the bottom row in view ──

    /** The keyboard's insets as the platform delivers them, into the screen's own Compose view. */
    private fun keyboard(bottom: Dp, visible: Boolean) {
        composeRule.runOnUiThread {
            val view = composeView(composeRule.activity.window.decorView) ?: error("no AndroidComposeView")
            val px = with(composeRule.density) { bottom.roundToPx() }
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
                .setVisible(WindowInsetsCompat.Type.ime(), visible)
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        rig.settle()
    }

    private fun composeView(view: View): View? {
        if (view.javaClass.name.endsWith("AndroidComposeView")) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) composeView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun openPanelBottomRowHidden(rotation: Int): DpRect {
        setScreenThenTurn(rotation)
        rig.tapBar()
        assertTrue("positive control: the bar's tap opened the dropdown", rig.dropdownShown())
        val panel = bounds(SEARCH_DROPDOWN_TAG)
        val search = bounds(SEARCH_DROPDOWN_SEARCH_TAG)
        assertTrue("positive control: before the keyboard, Search ${search.d()} is below the panel's bottom ${panel.d()}", search.bottom > panel.bottom)
        return panel
    }

    private fun assertDocked(rotation: Int) {
        val before = openPanelBottomRowHidden(rotation)
        val keyboardHeight = 180.dp
        keyboard(keyboardHeight, visible = true)
        val keyboardTop = window().bottom - keyboardHeight
        val panel = bounds(SEARCH_DROPDOWN_TAG)
        val search = bounds(SEARCH_DROPDOWN_SEARCH_TAG)
        val setOnMap = bounds(SEARCH_DROPDOWN_SET_ON_MAP_TAG)
        println("MEASURED -750 item 2 docked $landscape rotation $rotation: keyboard top ${keyboardTop.value}, panel before ${before.d()} after ${panel.d()}, Search ${search.d()}, Set on map ${setOnMap.d()}")
        assertTrue("the panel ends at or above the keyboard's top (${panel.bottom} vs $keyboardTop)", panel.bottom <= keyboardTop + 0.5.dp)
        for ((name, b) in listOf("Search" to search, "Set on map" to setOnMap)) {
            assertTrue("$name ${b.d()} ends above the keyboard's top $keyboardTop", b.bottom <= keyboardTop)
            assertTrue("$name ${b.d()} sits just above the keyboard's top $keyboardTop (within 24 dp)", b.bottom >= keyboardTop - 24.dp)
        }
        assertEquals("the panel did not move sideways (left)", before.left.value, panel.left.value, 0.5f)
        assertEquals("the panel did not move sideways (right)", before.right.value, panel.right.value, 0.5f)
        // And Search takes a real touch there: it runs the current-location search.
        val requests = rig.locationRequests
        rig.touch(composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_TAG))
        assertEquals("a real touch on Search above the keyboard ran its search", requests + 1, rig.locationRequests)
    }

    @Test fun `item 2 at ROTATION_90 a docked keyboard lifts Search and Set on map just above it`() = assertDocked(Surface.ROTATION_90)

    @Test fun `item 2 at ROTATION_270 a docked keyboard lifts Search and Set on map just above it`() = assertDocked(Surface.ROTATION_270)

    private fun assertFloating(rotation: Int) {
        val before = openPanelBottomRowHidden(rotation)
        keyboard(0.dp, visible = true)
        val panel = bounds(SEARCH_DROPDOWN_TAG)
        val search = bounds(SEARCH_DROPDOWN_SEARCH_TAG)
        println("MEASURED -750 item 2 floating $landscape rotation $rotation: panel before ${before.d()} after ${panel.d()}, Search ${search.d()}")
        assertTrue("Search ${search.d()} is inside the panel ${panel.d()}", search.bottom <= panel.bottom)
        assertTrue("the panel scrolled to its end: Search ${search.d()} within 24 dp of its bottom ${panel.bottom}", search.bottom >= panel.bottom - 24.dp)
        assertEquals("the panel stays on its side (left)", before.left.value, panel.left.value, 0.5f)
        assertEquals("the panel stays on its side (right)", before.right.value, panel.right.value, 0.5f)
        assertEquals("a floating keyboard does not resize the panel", (before.bottom - before.top).value, (panel.bottom - panel.top).value, 0.5f)
    }

    @Test fun `item 2 at ROTATION_90 a floating keyboard scrolls the panel to its end, the panel staying on its side`() = assertFloating(Surface.ROTATION_90)

    @Test fun `item 2 at ROTATION_270 a floating keyboard scrolls the panel to its end, the panel staying on its side`() = assertFloating(Surface.ROTATION_270)

    /** A user drag stops it, as in portrait (#202). */
    @Test
    fun `item 2 after a user drag a keyboard no longer moves the panel`() {
        openPanelBottomRowHidden(Surface.ROTATION_90)
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).performTouchInput { swipeDown() }
        rig.settle()
        keyboard(0.dp, visible = true)
        val panel = bounds(SEARCH_DROPDOWN_TAG)
        val search = bounds(SEARCH_DROPDOWN_SEARCH_TAG)
        assertTrue("the user's scroll stands: Search ${search.d()} is still below the panel ${panel.d()}", search.bottom > panel.bottom)
    }

    // ── Item 4: the bar stays in line with the strip, whatever its text ──

    private val longSpecies = TaxonSearchResult(
        990_001L,
        "Hygrophoropsis aurantiaca var. pallidissima",
        // Long enough to overflow the bar in both windows under Robolectric's native text, whose glyphs measure about 1.8 dp
        // each here (measured: 131 characters in 236 dp), far narrower than on the phone.
        "pale false chanterelle of the coastal dune pinewoods and the sandy heathland margins of the far north, " +
            "growing among the mosses and lichens under shore pine and Sitka spruce along the windward slopes",
        "variety",
        "Fungi",
        null,
    )

    /** Picks the long species through the bar, as a user would, and closes the dropdown. */
    private fun pickLongSpecies() {
        rig.taxonResults = listOf(longSpecies)
        rig.tapBar()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG, useUnmergedTree = true).performTextInput("pale")
        rig.settle()
        composeRule.onNodeWithText(longSpecies.commonName!!).performTouchInput { click(center) }
        rig.settle()
        assertEquals("positive control: the long species is selected", longSpecies.toFilter(), rig.viewModel.uiState.value.taxonFilter)
        closeDropdown()
    }

    private fun assertInLine(rotation: Int, withSearch: Boolean) {
        setScreenThenTurn(rotation) {
            if (withSearch) rig.searchCoordinates("45.326", "-122.634")
            pickLongSpecies()
        }
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        println("MEASURED -750 item 4 $landscape rotation $rotation search=$withSearch: bar ${bar.d()}, strip ${strip.d()}")
        assertEquals("the bar's top is the strip's top", strip.top.value, bar.top.value, 0.5f)
        assertEquals("the bar is the strip's height", (strip.bottom - strip.top).value, (bar.bottom - bar.top).value, 0.5f)
        // RECORD -956 (the owner: "One should look exactly line the other"): the bar is on the left at both rotations. Was, at ROTATION_270: assertEquals("the bar begins where the strip ends", strip.right.value, bar.left.value, 0.5f)
        assertEquals("the bar ends where the strip begins", strip.left.value, bar.right.value, 0.5f)
        val (text, layout) = summaryLayout()
        val visible = layout.getLineEnd(0, visibleEnd = true)
        println("MEASURED -750 item 4 summary <$text>: lines ${layout.lineCount}, visible $visible of ${text.length}, overflow ${layout.layoutInput.overflow}, lineRight ${layout.getLineRight(0)}, width ${layout.size.width}, ellipsised ${layout.isLineEllipsized(0)}, visualOverflow ${layout.hasVisualOverflow}, fontSize ${layout.layoutInput.style.fontSize}, offsetAtRight ${layout.getOffsetForPosition(androidx.compose.ui.geometry.Offset(layout.size.width - 1f, 10f))}")
        assertTrue("positive control: the summary names the long species <$text>", text.startsWith(longSpecies.commonName!!))
        assertEquals("the summary is one line", 1, layout.lineCount)
        assertTrue("positive control: the summary is longer than the bar's room ($visible of ${text.length} shown)", visible < text.length)
        assertEquals("the cut summary ends in an ellipsis", androidx.compose.ui.text.style.TextOverflow.Ellipsis, layout.layoutInput.overflow)
        assertTrue("its text box ends inside the bar", (node().boundsInRoot.right / composeRule.density.density) <= bar.right.value + 0.5f)
        if (withSearch) assertTrue("Clear shows with the search", shown(SEARCH_BAR_CLEAR_TAG))
    }

    private fun node() = composeRule.onAllNodes(
        hasAnyAncestor(hasTestTag(SEARCH_ENTRY_BAR_TAG)) and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().single { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }?.contains(" · ") == true }

    private fun summaryLayout(): Pair<String, TextLayoutResult> {
        val node = composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(SEARCH_ENTRY_BAR_TAG)) and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().single { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }?.contains(" · ") == true }
        val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        println("MEASURED -750 summary node bounds ${node.boundsInRoot}, layout size ${results.single().size}, maxWidth ${results.single().layoutInput.constraints.maxWidth}")
        return text to results.single()
    }

    @Test fun `item 4 at ROTATION_90 a long species keeps the bar in line with the strip, ending in an ellipsis`() = assertInLine(Surface.ROTATION_90, withSearch = false)

    @Test fun `item 4 at ROTATION_270 a long species keeps the bar in line with the strip, ending in an ellipsis`() = assertInLine(Surface.ROTATION_270, withSearch = false)

    @Test fun `item 4 at ROTATION_90 a long species with a search and Clear keeps the bar in line`() = assertInLine(Surface.ROTATION_90, withSearch = true)

    @Test fun `item 4 at ROTATION_270 a long species with a search and Clear keeps the bar in line`() = assertInLine(Surface.ROTATION_270, withSearch = true)

    /**
     * Item 3 with item 4, the owner's own path: a species searched, Clear, then the turn. Clear resets the species, and the bar
     * still meets the strip ("make sure that the panel doesn't switch also, in case I'm wrong").
     */
    @Test
    fun `item 3 a species searched, Clear, then the turn, the selection is the default and the bar meets the strip`() {
        setScreenThenTurn(Surface.ROTATION_90) {
            pickLongSpecies()
            rig.searchCoordinates("45.326", "-122.634")
            rig.touch(composeRule.onNodeWithTag(SEARCH_BAR_CLEAR_TAG))
        }
        assertEquals("Clear put the selection back to the default", TaxonFilter.FUNGI, rig.viewModel.uiState.value.taxonFilter)
        assertFalse("the bar does not name the species", summaryLayout().first.contains(longSpecies.commonName!!))
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        assertEquals("the bar's top is the strip's top", strip.top.value, bar.top.value, 0.5f)
        assertEquals("the bar ends where the strip begins", strip.left.value, bar.right.value, 0.5f)
    }

    // ── RECORD -754 (dispatch 2026-09-28-755, item 1): the bar stays put while the dropdown is open ──

    /**
     * The owner, on an S26 landscape screenshot: "I see that the entire bar gets larger along with the corresponding panel"; the
     * ruling, "Bar stays put, panel hangs below (Recommended)". With the dropdown open, and again with a docked keyboard up (the
     * phone's state once the field has focus), the bar keeps its closed bounds, the strip's height, ending at the join, and the
     * panel starts flush beneath it.
     */
    private fun assertBarStaysPut(rotation: Int, long: Boolean) {
        setScreenThenTurn(rotation) { if (long) pickLongSpecies() }
        val closed = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        assertEquals("positive control: closed, the bar is the strip's height", (strip.bottom - strip.top).value, (closed.bottom - closed.top).value, 0.5f)
        rig.tapBar()
        assertTrue("positive control: the bar's tap opened the dropdown", rig.dropdownShown())
        for (state in listOf("open", "open with a docked keyboard")) {
            if (state != "open") keyboard(180.dp, visible = true)
            val bar = bounds(SEARCH_ENTRY_BAR_TAG)
            val stripNow = bounds(STRIP_TAG)
            val panel = bounds(SEARCH_DROPDOWN_TAG)
            println("MEASURED -754 $landscape rotation $rotation long=$long $state: closed ${closed.d()}, bar ${bar.d()}, strip ${stripNow.d()}, panel ${panel.d()}")
            assertEquals("$state: the bar keeps its closed top", closed.top.value, bar.top.value, 0.5f)
            assertEquals("$state: the bar keeps its closed height, the strip's", (closed.bottom - closed.top).value, (bar.bottom - bar.top).value, 0.5f)
            assertEquals("$state: the bar keeps its closed left edge", closed.left.value, bar.left.value, 0.5f)
            assertEquals("$state: the bar keeps its closed right edge", closed.right.value, bar.right.value, 0.5f)
            assertEquals("$state: the strip is where it was", strip, stripNow)
            // RECORD -956 (the owner: "One should look exactly line the other"): the bar is on the left at both rotations. Was, at ROTATION_270: assertEquals("$state: the bar begins where the strip ends", stripNow.right.value, bar.left.value, 0.5f)
            assertEquals("$state: the bar ends where the strip begins", stripNow.left.value, bar.right.value, 0.5f)
            assertEquals("$state: the panel hangs flush beneath the bar", bar.bottom.value, panel.top.value, 0.5f)
        }
    }

    /** Back to the portrait window, as the phone turned upright (the same Activity, as on the phone). */
    private fun turnToPortrait() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        composeRule.activityRule.scenario.onActivity { RuntimeEnvironment.setQualifiers(portrait) }
        rig.settle()
        val w = window()
        assertTrue("positive control: portrait again (window ${w.d()})", (w.bottom - w.top) > (w.right - w.left))
    }

    /**
     * RECORD -754, the owner's reproduction on build 1.0.3103 (S26, ROTATION_90): after turning from portrait to landscape the bar
     * kept a portrait-sized box, taller than the strip and reaching the window's centre rather than the join. A first turn is
     * fine (the tests above); this is a turn into landscape after the phone had already been in landscape and back.
     */
    private fun assertSecondTurn(rotation: Int) {
        setScreenThenTurn(rotation)
        val firstBar = bounds(SEARCH_ENTRY_BAR_TAG)
        turnToPortrait()
        turn(rotation)
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        println("MEASURED -754 second turn $landscape rotation $rotation: first bar ${firstBar.d()}, bar ${bar.d()}, strip ${strip.d()}")
        assertEquals("the bar is the strip's height after the second turn", (strip.bottom - strip.top).value, (bar.bottom - bar.top).value, 0.5f)
        // RECORD -956 (the owner: "One should look exactly line the other"): the bar is on the left at both rotations. Was, at ROTATION_270: assertEquals("the bar begins where the strip ends after the second turn", strip.right.value, bar.left.value, 0.5f)
        assertEquals("the bar ends where the strip begins after the second turn", strip.left.value, bar.right.value, 0.5f)
        assertEquals("the bar is where the first turn put it", firstBar, bar)
        rig.tapBar()
        assertTrue("positive control: the bar's tap opened the dropdown", rig.dropdownShown())
        val open = bounds(SEARCH_ENTRY_BAR_TAG)
        assertEquals("open, the bar stays put", bar, open)
        assertEquals("open, the panel hangs flush beneath it", bounds(SEARCH_ENTRY_BAR_TAG).bottom.value, bounds(SEARCH_DROPDOWN_TAG).top.value, 0.5f)
    }

    @Test fun `754 at ROTATION_90 after a second turn into landscape the bar meets the strip, closed and open`() = assertSecondTurn(Surface.ROTATION_90)

    @Test fun `754 at ROTATION_270 after a second turn into landscape the bar meets the strip, closed and open`() = assertSecondTurn(Surface.ROTATION_270)

    @Test fun `754 at ROTATION_90 with a short summary the open bar stays put`() = assertBarStaysPut(Surface.ROTATION_90, long = false)

    @Test fun `754 at ROTATION_270 with a short summary the open bar stays put`() = assertBarStaysPut(Surface.ROTATION_270, long = false)

    @Test fun `754 at ROTATION_90 with a long summary the open bar stays put`() = assertBarStaysPut(Surface.ROTATION_90, long = true)

    @Test fun `754 at ROTATION_270 with a long summary the open bar stays put`() = assertBarStaysPut(Surface.ROTATION_270, long = true)

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeSearchFixes780Test : LandscapeSearchFixesTests("w360dp-h780dp-xxhdpi", "w780dp-h360dp-land-xxhdpi")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeSearchFixes823Test : LandscapeSearchFixesTests("w384dp-h823dp-xxhdpi", "w823dp-h384dp-land-xxhdpi")

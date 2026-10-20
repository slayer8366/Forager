package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.map.BUBBLE_TAIL_LENGTH
import com.zynergylabs.forager.app.ui.theme.ForagerTypography
import org.junit.Assert.assertEquals
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
 * RECORD -1466 (Sorrel's observation on #328, confirmed from the code in Hazel's 2.3 verify-first) and the owner's ruling "Fix now,
 * show me (Recommended)", whose step path is: Maps tab upright > turn to landscape > tap a marker > the bubble opens just below the
 * landscape bar, as it does if you start in landscape.
 *
 * The bug: the portrait strip's measured height (CompactMapLayoutState.compassStripHeightPx) was zeroed on leaving the tab and on
 * starting navigation, never on a turn into a short landscape window, where the strip takes its landscape branch and stops writing
 * it. The rotation keeps the Activity and the tab, so the portrait height stayed in use for the bubble's top limit (bubbleMinY),
 * which in landscape should be the one-line floor below the bar, as on a start in landscape.
 *
 * How the test sees it, through the real screen and ViewModel ([RealSearchScreenRig]) and the map's own sighting-tap callback: the
 * dot is placed [ANCHOR_ABOVE_STRIP_BOTTOM] above where the portrait strip's height would reach below the bar, and below the bar
 * plus one text line. A card that does not fit above its dot opens below it when it fits there against the top limit
 * (bubblePlacement's "Flip below"). With the one-line floor it fits, and the card's top sits under the dot, within the tail. With the
 * portrait height left in use it does not, and the card is clamped down to that stale limit, below the dot by more than the tail.
 *
 * Turning back upright, the same placement relative to the portrait strip must clamp to the strip's measured bottom again: a guard
 * that the fix does not leave the portrait height at 0 once the strip is upright.
 */
abstract class LandscapeStripHeightTests(private val portrait: String, private val landscape: String) {

    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The turn reaches the same Activity, as on the phone (MainActivity declares these configChanges; LandscapeSearchFixesTest). */
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
    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun DpRect.d() = "[%.2f, %.2f][%.2f, %.2f]".format(left.value, top.value, right.value, bottom.value)

    /** The one-line clearance the tab measures ("Mg" in labelMedium), the larger of the app's typography and Material's default. */
    private fun oneLine(): Dp {
        val activity = composeRule.activity
        val density = androidx.compose.ui.unit.Density(activity)
        val measurer = TextMeasurer(createFontFamilyResolver(activity), density, LayoutDirection.Ltr)
        fun height(style: androidx.compose.ui.text.TextStyle) = with(density) { measurer.measure("Mg", style).size.height.toDp() }
        return max(height(ForagerTypography.labelMedium), height(Typography().labelMedium))
    }

    /** Opens the observation bubble on the rig's sighting at [anchorDp], in the map slot's own coordinates, as the map's dot tap does. */
    private fun tapDotAt(anchorDp: androidx.compose.ui.unit.DpOffset) {
        val tap = rig.mapOnSightingTap ?: error("the map slot was never handed a sighting-tap callback")
        val px = with(composeRule.density) { Offset(anchorDp.x.toPx(), anchorDp.y.toPx()) }
        composeRule.runOnUiThread { tap(RealSearchScreenRig.sightingAt(Region(45.0, -122.0, 10)), px, 0f) }
        rig.settle()
        assertTrue("positive control: the dot's tap opened the observation bubble", shown(BUBBLE_TAG))
    }

    private fun turn(rotation: Int) {
        composeRule.activityRule.scenario.onActivity { RuntimeEnvironment.setQualifiers(landscape) }
        rig.settle()
        if (rotation != Surface.ROTATION_90) {
            Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
            composeRule.runOnUiThread {
                val view = composeView(composeRule.activity.window.decorView) ?: error("no AndroidComposeView")
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
    }

    private fun turnToPortrait() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        composeRule.activityRule.scenario.onActivity { RuntimeEnvironment.setQualifiers(portrait) }
        rig.settle()
        val w = window()
        assertTrue("positive control: portrait again (window ${w.d()})", (w.bottom - w.top) > (w.right - w.left))
    }

    private fun composeView(view: View): View? {
        if (view.javaClass.name.endsWith("AndroidComposeView")) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) composeView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun assertBubbleAfterTurn(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        rig.setScreen()
        assertTrue("positive control: portrait first (window ${window().d()})", (window().bottom - window().top) > (window().right - window().left))
        assertTrue("positive control: the portrait strip is up", shown(STRIP_TAG))
        val portraitStrip = bounds(STRIP_TAG)
        val portraitHeight = portraitStrip.bottom - portraitStrip.top
        val line = oneLine()
        val tail = BUBBLE_TAIL_LENGTH

        turn(rotation)
        val map = bounds(LAYOUT_FIXES_MAP_TAG)
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val barBottom = bar.bottom - map.top
        // Between the bar plus one line (the landscape limit) and the bar plus the portrait strip's height (the stale one).
        val anchorY = barBottom + portraitHeight - ANCHOR_ABOVE_STRIP_BOTTOM
        // The flipped card's top-left sits 0.293 of the tail above the dot at the bubble's 45 degrees; it must clear the limit.
        assertTrue(
            "positive control: the portrait strip ($portraitHeight) is tall enough that the dot sits below the bar plus one line ($line)",
            anchorY - tail * 0.3f >= barBottom + line + 1.dp,
        )
        val anchorX = (map.right - map.left) / 2
        tapDotAt(androidx.compose.ui.unit.DpOffset(anchorX, anchorY))
        val card = bounds(BUBBLE_TAG)
        val cardTop = card.top - map.top
        println(
            "MEASURED strip-height $landscape rotation $rotation: portrait strip ${portraitStrip.d()} (height $portraitHeight), one line $line, " +
                "map ${map.d()}, bar ${bar.d()}, dot y $anchorY, card ${card.d()} (top in map $cardTop), stale limit would be ${barBottom + portraitHeight}",
        )
        assertTrue(
            "after the turn the bubble opens below its dot just under the landscape bar (card top $cardTop, dot $anchorY, tail $tail); " +
                "a card top at or past ${barBottom + portraitHeight + tail} is the portrait strip's height still in use as the top limit",
            cardTop <= anchorY + tail + 1.dp,
        )

        // Upright again: the portrait strip is measured again, and the same placement clamps to its bottom, not to the one-line floor.
        turnToPortrait()
        assertTrue("positive control: the portrait strip is up again", shown(STRIP_TAG))
        val mapUp = bounds(LAYOUT_FIXES_MAP_TAG)
        val stripUp = bounds(STRIP_TAG)
        val stripBottomUp = stripUp.bottom - mapUp.top
        val anchorUpY = stripBottomUp - ANCHOR_ABOVE_STRIP_BOTTOM
        tapDotAt(androidx.compose.ui.unit.DpOffset((mapUp.right - mapUp.left) / 2, anchorUpY))
        val cardUp = bounds(BUBBLE_TAG)
        val cardUpTop = cardUp.top - mapUp.top
        println("MEASURED strip-height upright again: strip ${stripUp.d()}, map ${mapUp.d()}, dot y $anchorUpY, card ${cardUp.d()} (top in map $cardUpTop)")
        assertTrue(
            "upright again the bubble's top limit is the measured strip's bottom ($stripBottomUp), so the card is held there (card top " +
                "$cardUpTop, dot $anchorUpY); a card under the dot means the portrait height was left at 0",
            cardUpTop >= stripBottomUp + tail - 1.dp,
        )
    }

    @Test fun `at ROTATION_90 a bubble opened after a turn from upright sits just below the landscape bar`() = assertBubbleAfterTurn(Surface.ROTATION_90)

    @Test fun `at ROTATION_270 a bubble opened after a turn from upright sits just below the landscape bar`() = assertBubbleAfterTurn(Surface.ROTATION_270)

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
        const val BUBBLE_TAG = "observation-bubble"
        val ANCHOR_ABOVE_STRIP_BOTTOM = 6.dp
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeStripHeight780Test : LandscapeStripHeightTests("w360dp-h780dp-xxhdpi", "w780dp-h360dp-land-xxhdpi")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeStripHeight823Test : LandscapeStripHeightTests("w384dp-h823dp-xxhdpi", "w823dp-h384dp-land-xxhdpi")

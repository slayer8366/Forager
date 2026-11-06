package com.zynergylabs.forager.app.ui.availability

import android.content.res.Configuration
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.Region
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
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-18-03, item 2 (Forager RECORD -1269; the owner: "Fix it (Recommended)"). Elder's top-up audit, item 2: on
 * List, Seasonal and Journal in a landscape window the pill took the Maps pill's size only after Maps had been shown in that
 * window (the scaffold kept Maps' last strip measurement, 0 until then), and was half the width until then; the owner's own
 * words, "The layout resizing between tabs looks like a UI glitch".
 *
 * The dispatch: the pill uses Maps' size "even before Maps has been shown in that window", worked out from the window as Maps'
 * own pill is; switching to Maps afterwards moves nothing and resizes nothing; and its size survives a process restart in
 * landscape. Width is worked out from the same functions the Maps strip is sized by; height by the strip's one line (H2, the
 * planner's call). The evening lines (sundown, Back by) are not modelled: listed in known-bugs.
 *
 * The first case is the one a user meets: on List in portrait, the phone turned (this app keeps its Activity through a turn,
 * configChanges, so the turn here is a configuration change on the same screen, as LandscapeSearchFixesTest does it). The pill's
 * bounds on List are read before Maps is ever shown in landscape, then compared whole with the Maps pill's.
 */
abstract class TabsFirstVisitPillTests(private val portrait: String, private val landscape: String) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * The host declares MainActivity's configChanges, so the turn reaches the same Activity, as on the phone, instead of
     * recreating it (LandscapeSearchFixesTest's rotatesInPlace, the same reason; without it the first run of this class found no
     * compose hierarchy after the turn).
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
    val rules: RuleChain = RuleChain.outerRule(rotatesInPlace).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val roles = MapChromeRoles()

    /** A fix, its altitude, and a search showing (so the bar's floor carries Clear): every input of the Maps strip's width. */
    private fun withFix(state: MapChromeScreenState) = state.apply {
        ui = ui.copy(
            liveFix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 3_000.0, accuracyMeters = 5f, timestampEpochMillis = 1_700_000_000_000L, provider = FixProvider.GPS),
            region = Region(lat = 45.52, lng = -122.68, radiusKm = 15),
        )
    }

    private fun open(label: String, landscapeNav: Boolean) {
        val nav = if (landscapeNav) COMPACT_NAVIGATION_RAIL_TAG else COMPACT_BOTTOM_NAV_TAG
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(nav)), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun pill(): DpRect = composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun window(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()

    private fun turn(rotation: Int) {
        composeRule.activityRule.scenario.onActivity { RuntimeEnvironment.setQualifiers(landscape) }
        composeRule.waitForIdle()
        if (rotation != Surface.ROTATION_90) {
            Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
            composeRule.runOnUiThread {
                val view = composeView(composeRule.activity.window.decorView) ?: error("no compose view")
                // Equal configurations are not a change to Compose; the long-layout bit (nothing reads it) is flipped so this is one
                // (LandscapeSearchFixesTest's turn, the same reason).
                val config = Configuration(composeRule.activity.resources.configuration)
                val long = config.screenLayout and Configuration.SCREENLAYOUT_LONG_MASK
                config.screenLayout = (config.screenLayout and Configuration.SCREENLAYOUT_LONG_MASK.inv()) or
                    (if (long == Configuration.SCREENLAYOUT_LONG_YES) Configuration.SCREENLAYOUT_LONG_NO else Configuration.SCREENLAYOUT_LONG_YES)
                view.dispatchConfigurationChanged(config)
            }
            composeRule.waitForIdle()
        }
        assertEquals("positive control: the display reads the rotation", rotation, ShadowDisplay.getDefaultDisplay().rotation)
        val w = window()
        assertTrue("positive control: landscape after the turn (window $w)", (w.right - w.left) > (w.bottom - w.top))
    }

    private fun composeView(view: View): View? {
        if (view.javaClass.name.endsWith("AndroidComposeView")) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) composeView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun assertSameBounds(what: String, expected: DpRect, actual: DpRect) {
        assertEquals("$what: left", expected.left.value, actual.left.value, 0.5f)
        assertEquals("$what: top", expected.top.value, actual.top.value, 0.5f)
        assertEquals("$what: right", expected.right.value, actual.right.value, 0.5f)
        assertEquals("$what: bottom", expected.bottom.value, actual.bottom.value, 0.5f)
    }

    protected fun checkFirstVisit(tab: String, rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        val state = withFix(MapChromeScreenState())
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
        val w = window()
        assertTrue("positive control: portrait first (window $w)", (w.bottom - w.top) > (w.right - w.left))
        open(tab, landscapeNav = false)
        turn(rotation)
        // Maps has not been shown in this landscape window: what List, Seasonal or Journal shows now is the first visit.
        val firstVisit = pill()
        open("Maps", landscapeNav = true)
        val maps = pill()
        assertSameBounds("$tab's pill on the first landscape visit, against the Maps pill", maps, firstVisit)
        open(tab, landscapeNav = true)
        assertSameBounds("$tab's pill after Maps, against its first visit", firstVisit, pill())
    }

    @Test fun `ROTATION_90, turned on List, the pill is the Maps pill's size and place before Maps is shown`() = checkFirstVisit("List", Surface.ROTATION_90)
    @Test fun `ROTATION_270, turned on List, the pill is the Maps pill's size and place before Maps is shown`() = checkFirstVisit("List", Surface.ROTATION_270)
    @Test fun `ROTATION_90, turned on Seasonal, the pill is the Maps pill's size and place before Maps is shown`() = checkFirstVisit("Seasonal", Surface.ROTATION_90)

    /**
     * "Its size also survives a process restart in landscape": the screen's saved state restored into a fresh composition (what
     * Android does after it ends the process behind a landscape List tab), which lands on List without Maps being composed.
     */
    @Test
    fun `after a restart in landscape on List, the pill is the Maps pill's size and place`() {
        composeRule.activityRule.scenario.onActivity { RuntimeEnvironment.setQualifiers(landscape) }
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        val state = withFix(MapChromeScreenState())
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
        val maps = pill()
        open("List", landscapeNav = true)
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        // Positive control: the restore came back on List (its rail item is the selected one).
        composeRule.onNode(hasText("List") and hasAnyAncestor(hasTestTag(COMPACT_NAVIGATION_RAIL_TAG))).assertIsSelected()
        assertSameBounds("List's pill after the restore, against the Maps pill", maps, pill())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class TabsFirstVisitPill823Test : TabsFirstVisitPillTests("w384dp-h823dp-xxhdpi", "w823dp-h384dp-land-xxhdpi")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
class TabsFirstVisitPill780Test : TabsFirstVisitPillTests("w360dp-h780dp-xxhdpi", "w780dp-h360dp-land-xxhdpi")

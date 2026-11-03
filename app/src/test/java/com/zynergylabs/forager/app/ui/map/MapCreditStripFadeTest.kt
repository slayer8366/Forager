package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.availability.layoutFixesHostActivityRule
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D25 and D26 (dispatch 2026-10-30-08, RECORD -1968, -1969): the map's credit strip shows when a map is made and whenever its
 * words change (a basemap switch, the Trails toggle, topo at night), and fades into the "i" 5 s after it shows or at the user's
 * first pan or zoom, whichever is first, the 5 s starting again each time it shows. It never fades while OpenStreetMap's raster
 * tiles are drawn. A rotation or a return from the background does not show it again. The fade is kept under reduced motion.
 *
 * [MapCreditStrip] is what `SightingsMapLayout` composes, with the inputs it is given there; the real MapView cannot be built
 * under Robolectric, so its gesture count (MapListeners.kt, MapLibre's gesture reason) is [gestures] here, and that wiring is a
 * device item. The clock is driven by hand from the first frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class MapCreditStripFadeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    private var basemap by mutableStateOf(Basemap.OPEN_TOPO_MAP)
    private var trails by mutableStateOf(false)
    private var night by mutableStateOf(false)
    private var ownTrail by mutableStateOf(true)
    private var gestures by mutableIntStateOf(0)
    private var hosted by mutableStateOf(true)
    private var landscape by mutableStateOf(false)

    private val trailsCredit = MAP_LAYER_REGISTRY.firstNotNullOf { it.credit }

    @Before
    fun manualClock() {
        composeRule.mainClock.autoAdvance = false
    }

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    private fun show() {
        composeRule.setContent {
            ForagerTheme(darkTheme = false) {
                // A rotation, as the map sees it with MainActivity's configChanges: the same composition, a new window shape.
                Box(Modifier.size(if (landscape) 640.dp else 360.dp, if (landscape) 360.dp else 640.dp)) {
                    if (hosted) {
                        MapCreditStrip(
                            basemap = basemap,
                            useOfflineTiles = false,
                            layerCredits = if (trails) listOf(trailsCredit) else emptyList(),
                            nightMode = night,
                            ownTrail = ownTrail,
                            userCameraGestures = gestures,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
            }
        }
        frames(2)
    }

    private fun frames(n: Int) = repeat(n) { composeRule.mainClock.advanceTimeByFrame() }

    private fun advance(millis: Long) {
        composeRule.mainClock.advanceTimeBy(millis)
        composeRule.waitForIdle()
    }

    private fun change(block: () -> Unit) {
        composeRule.runOnUiThread { block(); Snapshot.sendApplyNotifications() }
        frames(2)
    }

    private fun alpha(): Float =
        composeRule.onNodeWithTag(MAP_CREDIT_CAPTION_TAG).fetchSemanticsNode().config[MapCreditStripAlphaKey]

    private fun assertShown(what: String) = assertEquals("$what: the strip is shown", 1f, alpha(), 0.001f)
    private fun assertFaded(what: String) = assertEquals("$what: the strip has faded into the i", 0f, alpha(), 0.001f)

    /** Long enough for any fade to settle. */
    private val settle = 1_500L

    @Test
    fun `a new map shows the strip at once, and it fades 5 s later without moving`() {
        show()
        assertShown("as the map is made")
        val before = composeRule.onNodeWithTag(MAP_CREDIT_CAPTION_TAG).getUnclippedBoundsInRoot()
        advance(MAP_CREDIT_STRIP_SHOWN_MILLIS - 200)
        assertShown("4.8 s after")
        advance(200 + settle)
        assertFaded("5 s after")
        // D17's Re-center reads the strip's size (onCreditSizeChanged): the fade is drawn only, so nothing beside it moves.
        assertEquals("the faded strip keeps its place and size", before, composeRule.onNodeWithTag(MAP_CREDIT_CAPTION_TAG).getUnclippedBoundsInRoot())
    }

    @Test
    fun `the first pan or zoom fades it before the 5 s are up`() {
        show()
        advance(1_000)
        assertShown("1 s after, before any gesture")
        change { gestures++ }
        advance(settle)
        assertFaded("${1_000 + settle} ms after, a gesture at 1 s")
    }

    @Test
    fun `a basemap switch shows it again, and its 5 s start again`() {
        show()
        advance(MAP_CREDIT_STRIP_SHOWN_MILLIS + settle)
        assertFaded("before the switch")
        change { basemap = Basemap.FORAGER_TRAIL }
        advance(settle)
        assertShown("after a switch to Trail Map")
        advance(MAP_CREDIT_STRIP_SHOWN_MILLIS - settle - 200)
        assertShown("4.8 s after the switch")
        advance(200 + settle)
        assertFaded("5 s after the switch")
    }

    @Test
    fun `a gesture made before a switch does not fade the strip the switch shows`() {
        show()
        change { gestures++ }
        advance(settle)
        assertFaded("after the gesture")
        change { basemap = Basemap.FORAGER_TRAIL }
        advance(settle)
        assertShown("the switch shows it again, the earlier gesture spent")
        change { gestures++ }
        advance(settle)
        assertFaded("a new gesture fades it")
    }

    @Test
    fun `the Trails toggle and topo at night show it again, since its words change`() {
        show()
        advance(MAP_CREDIT_STRIP_SHOWN_MILLIS + settle)
        assertFaded("before the Trails toggle")
        change { trails = true }
        advance(settle)
        assertShown("the Trails credit added")
        advance(MAP_CREDIT_STRIP_SHOWN_MILLIS)
        assertFaded("5 s after the toggle")
        change { night = true }
        advance(settle)
        assertShown("topo at night adds Trail Map's credit below it")
    }

    @Test
    fun `with OpenStreetMap drawn it never fades, after a minute or at a gesture`() {
        // The added map types whose tiles ask for it (dispatch 2026-11-03-05 D7: today the Street map's, OpenStreetMap's).
        basemap = ADDED_MAP_TYPES.firstOrNull { it.creditNeverFades }?.mapMode?.basemap ?: return
        show()
        advance(60_000)
        assertShown("a minute on the OSM map")
        change { gestures++ }
        advance(settle)
        assertShown("after a pan on the OSM map")
    }

    @Test
    fun `the Trail Map fallback and topo at night after it never fade, topo by day after it does`() {
        // While a stand-in whose credit never fades is registered (the Street map's; D7, D8). With none the fallback is
        // Topographical, whose credit fades as on any map.
        if (trailMapStandIn?.creditNeverFades() != true) return
        basemap = Basemap.FORAGER_TRAIL
        ownTrail = false
        show()
        change { gestures++ }
        advance(60_000)
        assertShown("Trail Map fallen back to OpenStreetMap's tiles")
        change { basemap = Basemap.OPEN_TOPO_MAP; night = true }
        change { gestures++ }
        advance(60_000)
        assertShown("topo at night after the fallback, OpenStreetMap below it")
        change { night = false }
        advance(MAP_CREDIT_STRIP_SHOWN_MILLIS + settle)
        assertFaded("topo by day after the fallback draws no OpenStreetMap tiles")
    }

    @Test
    fun `leaving OpenStreetMap shows the strip for 5 s and then it fades`() {
        basemap = ADDED_MAP_TYPES.firstOrNull { it.creditNeverFades }?.mapMode?.basemap ?: return
        show()
        change { gestures++ }
        advance(30_000)
        assertShown("on the OSM map")
        change { basemap = Basemap.OPEN_TOPO_MAP }
        advance(MAP_CREDIT_STRIP_SHOWN_MILLIS - 200)
        assertShown("4.8 s after switching to topo")
        advance(200 + settle)
        assertFaded("5 s after switching to topo")
    }

    @Test
    fun `a map made again, as on a return to the Maps tab, shows it again`() {
        show()
        advance(MAP_CREDIT_STRIP_SHOWN_MILLIS + settle)
        assertFaded("before the map goes")
        change { hosted = false }
        assertTrue("no strip while there is no map", composeRule.onAllNodesWithTag(MAP_CREDIT_CAPTION_TAG).fetchSemanticsNodes().isEmpty())
        change { hosted = true }
        assertShown("the new map")
    }

    @Test
    fun `a rotation does not show it again`() {
        show()
        advance(MAP_CREDIT_STRIP_SHOWN_MILLIS + settle)
        assertFaded("in portrait")
        change { landscape = true }
        advance(settle)
        assertFaded("after turning to landscape")
    }

    @Test
    fun `a return from the background does not show it again`() {
        show()
        advance(MAP_CREDIT_STRIP_SHOWN_MILLIS + settle)
        assertFaded("before the app goes to the background")
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        frames(2)
        advance(settle)
        assertFaded("back from the background")
    }

    private fun assertFadesRatherThanVanishing(what: String) {
        show()
        assertShown("$what, as the map is made")
        advance(MAP_CREDIT_STRIP_SHOWN_MILLIS)
        frames(2)
        val mid = alpha()
        assertTrue("$what: caught fading, alpha $mid between 0 and 1", mid > 0.02f && mid < 0.98f)
        advance(settle)
        assertFaded("$what, after the fade")
    }

    @Test
    fun `the strip fades rather than vanishing`() = assertFadesRatherThanVanishing("full motion")

    /** D26 as corrected (-1970): "the fade alone, kept under reduced motion", like the map pop-up's (docs/motion-spec.md section 4). */
    @Test
    fun `under reduced motion the strip keeps its fade`() {
        // The transition scale alone at 0, read by ForagerTheme's ProvideReduceMotion, so Compose's own animations still run.
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        assertTrue("positive control: the app reads reduced motion as on", com.zynergylabs.forager.app.ui.motion.isReduceMotionEnabled(resolver))
        assertFadesRatherThanVanishing("reduced motion")
    }
}

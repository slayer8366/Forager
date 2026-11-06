package com.zynergylabs.forager.app.ui.motion

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Inverted per docs/plans/understory-design-system.md §4S and
 * docs/adr/0002-motion-scheme-adoption.md: no exposed category resolves to a [TweenSpec] any
 * more, each resolves to the [MaterialTheme.motionScheme] spec its category calls for, and
 * effects-category specs carry the scheme's critically-damped ratio.
 *
 * Robolectric-backed, unlike the plain-JUnit test this replaces: every `MotionTokens` function is
 * now `@Composable @ReadOnlyComposable`, since [MaterialTheme.motionScheme] only resolves inside a
 * composition, so reading a spec back out means running one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MotionTokensTest {

    private val composeRule = createComposeRule()

    // createComposeRule() launches a host ComponentActivity via ActivityScenarioRule under the
    // hood; Robolectric can't resolve that launch unless the shadow package manager already knows
    // about the activity. Same fix AvailabilityScreenMapIconStackTest uses.
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private lateinit var feedback: FiniteAnimationSpec<Float>
    private lateinit var panel: FiniteAnimationSpec<Float>
    private lateinit var navigation: FiniteAnimationSpec<Float>
    private lateinit var tabCrossfade: FiniteAnimationSpec<Float>
    private lateinit var mapPopUpFade: FiniteAnimationSpec<Float>
    private lateinit var mapPopUpGrow: FiniteAnimationSpec<Float>
    private lateinit var navigationViewChrome: FiniteAnimationSpec<Float>
    private lateinit var wordSwap: FiniteAnimationSpec<Float>
    private lateinit var pageSlide: FiniteAnimationSpec<Float>
    private lateinit var listRow: FiniteAnimationSpec<Float>
    private lateinit var nightModeFade: FiniteAnimationSpec<Float>
    private lateinit var schemeFastEffects: FiniteAnimationSpec<Float>

    private lateinit var schemeDefaultSpatial: FiniteAnimationSpec<Float>
    private lateinit var schemeFastSpatial: FiniteAnimationSpec<Float>
    private lateinit var schemeSlowSpatial: FiniteAnimationSpec<Float>
    private lateinit var schemeDefaultEffects: FiniteAnimationSpec<Float>

    @Before
    fun resolveAllSpecsInsideForagerTheme() {
        composeRule.setContent {
            ForagerTheme {
                feedback = MotionTokens.feedbackMotionSpec()
                panel = MotionTokens.panelMotionSpec()
                navigation = MotionTokens.navigationMotionSpec()
                tabCrossfade = MotionTokens.tabCrossfadeSpec()
                mapPopUpFade = MotionTokens.mapPopUpFadeSpec()
                mapPopUpGrow = MotionTokens.mapPopUpGrowSpec()
                navigationViewChrome = MotionTokens.navigationViewChromeSpec()
                wordSwap = MotionTokens.wordSwapSpec()
                pageSlide = MotionTokens.pageSlideSpec()
                listRow = MotionTokens.listRowSpec()
                // Read above the theme in production, from the scheme the theme provides: the same scheme here.
                nightModeFade = MotionTokens.nightModeFadeSpec(MaterialTheme.motionScheme)
                schemeFastEffects = MaterialTheme.motionScheme.fastEffectsSpec()

                schemeDefaultSpatial = MaterialTheme.motionScheme.defaultSpatialSpec()
                schemeFastSpatial = MaterialTheme.motionScheme.fastSpatialSpec()
                schemeSlowSpatial = MaterialTheme.motionScheme.slowSpatialSpec()
                schemeDefaultEffects = MaterialTheme.motionScheme.defaultEffectsSpec()
            }
        }
        composeRule.waitForIdle()
    }

    private fun allExposedSpecs() = listOf(
        feedback, panel, navigation, tabCrossfade, mapPopUpFade, mapPopUpGrow, wordSwap, pageSlide, listRow, nightModeFade,
    )

    /** Every exposed spec must actually be a spring at runtime before its damping ratio means anything. */
    private fun FiniteAnimationSpec<Float>.assertIsSpringAndReturnIt(): SpringSpec<Float> {
        assertTrue("$this is not a SpringSpec -- ADR-0002 moved every category onto MotionScheme", this is SpringSpec<Float>)
        return this as SpringSpec<Float>
    }

    @Test
    fun `no exposed category resolves to a TweenSpec`() {
        for (spec in allExposedSpecs()) {
            assertTrue("$spec is a TweenSpec -- ADR-0002 moved every category onto MotionScheme", spec !is TweenSpec<Float>)
        }
    }

    @Test
    fun `feedback motion maps to fastSpatialSpec, per ADR-0002's category table`() {
        assertEquals(schemeFastSpatial.assertIsSpringAndReturnIt(), feedback.assertIsSpringAndReturnIt())
    }

    @Test
    fun `panel motion maps to slowSpatialSpec, the only category with a real production caller`() {
        assertEquals(schemeSlowSpatial.assertIsSpringAndReturnIt(), panel.assertIsSpringAndReturnIt())
    }

    @Test
    fun `navigation motion maps to defaultSpatialSpec, split from the panel row`() {
        assertEquals(schemeDefaultSpatial.assertIsSpringAndReturnIt(), navigation.assertIsSpringAndReturnIt())
        assertTrue(
            "navigation and panel motion must stay distinct rows per ADR-0002",
            navigation.assertIsSpringAndReturnIt() != panel.assertIsSpringAndReturnIt(),
        )
    }

    @Test
    fun `effects-category specs are critically damped, so interruption can never overshoot`() {
        // docs/adr/0002-motion-scheme-adoption.md, "R1": dampingRatio >= 1.0 is what makes the
        // interruption-safety proof hold. Guards against a future scheme substitution quietly
        // reintroducing overshoot on an alpha/colour animation.
        for (spec in listOf(tabCrossfade, mapPopUpFade, wordSwap, pageSlide, listRow, nightModeFade)) {
            val spring = spec.assertIsSpringAndReturnIt()
            assertTrue("$spring is not critically damped", spring.dampingRatio >= 1.0f)
        }
    }

    @Test
    fun `spatial-category specs are allowed to be underdamped, since ADR-0002 accepts overshoot there`() {
        // The mirror of the assertion above: spatial specs are *not* required to be critically
        // damped (fastSpatialSpec is 0.6 under expressive()), so this only guards that the
        // exposed categories really are drawing from the scheme's spatial family rather than
        // something stricter -- not a claim about a specific ratio, which is provisional pending
        // Gate G.
        for (spec in listOf(feedback, panel, navigation, mapPopUpGrow)) {
            val spring = spec.assertIsSpringAndReturnIt()
            assertTrue("$spring has an unexpectedly high damping ratio for a spatial spec", spring.dampingRatio <= 1.0f)
        }
    }

    // Motion Part 2 (dispatch 2026-09-28-666): the five categories it adds, each onto the scheme spec its comment names.

    @Test
    fun `the tab crossfade and the word swap map to fastEffectsSpec`() {
        assertEquals(schemeFastEffects.assertIsSpringAndReturnIt(), tabCrossfade.assertIsSpringAndReturnIt())
        assertEquals(schemeFastEffects.assertIsSpringAndReturnIt(), wordSwap.assertIsSpringAndReturnIt())
    }

    @Test
    fun `a map pop-up fades on defaultEffectsSpec and grows on defaultSpatialSpec`() {
        assertEquals(schemeDefaultEffects.assertIsSpringAndReturnIt(), mapPopUpFade.assertIsSpringAndReturnIt())
        assertEquals(schemeDefaultSpatial.assertIsSpringAndReturnIt(), mapPopUpGrow.assertIsSpringAndReturnIt())
    }

    /**
     * The one exception to ADR-0002 (Amendment 1 to motion Part 2, RECORD -672; the owner: "Allow one exception"): a tween exactly
     * as long as the map's navigation tilt, read from the constant the map's camera uses. Left out of [allExposedSpecs], which
     * holds every other category to "no TweenSpec".
     */
    @Test
    fun `the navigation view's chrome is the one tween, exactly as long as the map's tilt`() {
        assertTrue("$navigationViewChrome is the timed exception", navigationViewChrome is TweenSpec<Float>)
        assertEquals(
            com.zynergylabs.forager.app.ui.map.NAVIGATION_VIEW_TRANSITION_MILLIS.toInt(),
            (navigationViewChrome as TweenSpec<Float>).durationMillis,
        )
    }

    @Test
    fun `a map pop-up's grow is slight`() {
        assertTrue("grows from ${MotionTokens.MAP_POPUP_ENTER_SCALE}", MotionTokens.MAP_POPUP_ENTER_SCALE in 0.7f..0.95f)
    }

    // Motion Part 3 (dispatch 2026-09-28-676): the three categories it adds.

    @Test
    fun `a Journal page slides, and a list row moves, on defaultEffectsSpec, which cannot overshoot`() {
        assertEquals(schemeDefaultEffects.assertIsSpringAndReturnIt(), pageSlide.assertIsSpringAndReturnIt())
        assertEquals(schemeDefaultEffects.assertIsSpringAndReturnIt(), listRow.assertIsSpringAndReturnIt())
    }

    @Test
    fun `night mode's colours blend on fastEffectsSpec, the quick one`() {
        assertEquals(schemeFastEffects.assertIsSpringAndReturnIt(), nightModeFade.assertIsSpringAndReturnIt())
    }

    @Test
    fun `night mode's spec read from the app's own motion scheme is the one the theme provides`() {
        assertEquals(
            schemeFastEffects.assertIsSpringAndReturnIt(),
            MotionTokens.nightModeFadeSpec<Float>(com.zynergylabs.forager.app.ui.theme.ForagerMotionScheme).assertIsSpringAndReturnIt(),
        )
    }
}

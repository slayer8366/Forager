package com.forager.app.ui.motion

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.forager.app.ui.theme.ForagerTheme
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
    private lateinit var markerEntrance: FiniteAnimationSpec<Float>
    private lateinit var selectionPulse: FiniteAnimationSpec<Float>
    private lateinit var narrativeReveal: FiniteAnimationSpec<Float>
    private lateinit var routeMorph: FiniteAnimationSpec<Float>
    private lateinit var dataLayerOverlay: FiniteAnimationSpec<Float>

    private lateinit var schemeDefaultSpatial: FiniteAnimationSpec<Float>
    private lateinit var schemeFastSpatial: FiniteAnimationSpec<Float>
    private lateinit var schemeSlowSpatial: FiniteAnimationSpec<Float>
    private lateinit var schemeDefaultEffects: FiniteAnimationSpec<Float>
    private lateinit var schemeSlowEffects: FiniteAnimationSpec<Float>

    @Before
    fun resolveAllSpecsInsideForagerTheme() {
        composeRule.setContent {
            ForagerTheme {
                feedback = MotionTokens.feedbackMotionSpec()
                panel = MotionTokens.panelMotionSpec()
                navigation = MotionTokens.navigationMotionSpec()
                markerEntrance = MotionTokens.markerEntranceSpec()
                selectionPulse = MotionTokens.selectionPulseSpec()
                narrativeReveal = MotionTokens.narrativeRevealSpec()
                routeMorph = MotionTokens.routeRecalculationMorphSpec()
                dataLayerOverlay = MotionTokens.dataLayerOverlaySpec()

                schemeDefaultSpatial = MaterialTheme.motionScheme.defaultSpatialSpec()
                schemeFastSpatial = MaterialTheme.motionScheme.fastSpatialSpec()
                schemeSlowSpatial = MaterialTheme.motionScheme.slowSpatialSpec()
                schemeDefaultEffects = MaterialTheme.motionScheme.defaultEffectsSpec()
                schemeSlowEffects = MaterialTheme.motionScheme.slowEffectsSpec()
            }
        }
        composeRule.waitForIdle()
    }

    private fun allExposedSpecs() = listOf(
        feedback, panel, navigation, markerEntrance, selectionPulse, narrativeReveal, routeMorph, dataLayerOverlay,
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
    fun `marker entrance maps to defaultSpatialSpec`() {
        assertEquals(schemeDefaultSpatial.assertIsSpringAndReturnIt(), markerEntrance.assertIsSpringAndReturnIt())
    }

    @Test
    fun `selection pulse maps to slowSpatialSpec`() {
        assertEquals(schemeSlowSpatial.assertIsSpringAndReturnIt(), selectionPulse.assertIsSpringAndReturnIt())
    }

    @Test
    fun `narrative reveal maps to slowEffectsSpec`() {
        assertEquals(schemeSlowEffects.assertIsSpringAndReturnIt(), narrativeReveal.assertIsSpringAndReturnIt())
    }

    @Test
    fun `route recalculation morph maps to slowEffectsSpec`() {
        assertEquals(schemeSlowEffects.assertIsSpringAndReturnIt(), routeMorph.assertIsSpringAndReturnIt())
    }

    @Test
    fun `data layer overlay maps to defaultEffectsSpec`() {
        assertEquals(schemeDefaultEffects.assertIsSpringAndReturnIt(), dataLayerOverlay.assertIsSpringAndReturnIt())
    }

    @Test
    fun `effects-category specs are critically damped, so interruption can never overshoot`() {
        // docs/adr/0002-motion-scheme-adoption.md, "R1": dampingRatio >= 1.0 is what makes the
        // interruption-safety proof hold. Guards against a future scheme substitution quietly
        // reintroducing overshoot on an alpha/colour animation.
        for (spec in listOf(narrativeReveal, routeMorph, dataLayerOverlay)) {
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
        for (spec in listOf(feedback, panel, navigation, markerEntrance, selectionPulse)) {
            val spring = spec.assertIsSpringAndReturnIt()
            assertTrue("$spring has an unexpectedly high damping ratio for a spatial spec", spring.dampingRatio <= 1.0f)
        }
    }

    @Test
    fun `selection pulse scale range is low-amplitude`() {
        // "low-amplitude breathing pulse" -- guards against a future edit turning this into a
        // large, alarm-style scale swing.
        val amplitude = MotionTokens.SELECTION_PULSE_MAX_SCALE - MotionTokens.SELECTION_PULSE_MIN_SCALE
        assertTrue("selection pulse amplitude $amplitude is not low-amplitude", amplitude in 0.0f..0.15f)
    }
}

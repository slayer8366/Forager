package com.forager.app.ui.motion

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Inverted, not deleted, per `docs/plans/understory-design-system.md` §4S and ADR-0002
 * (`docs/adr/0002-motion-scheme-adoption.md`). The version this replaces asserted that every named
 * spec *was* a [TweenSpec], failure message "spring-based specs can overshoot" — mechanically
 * pinning the tween-only rule ADR-0002 supersedes. Weakening that assertion to something
 * type-agnostic ("is some kind of `FiniteAnimationSpec`") would stop discriminating anything;
 * this asserts the new property with the same specificity the old one asserted the property it
 * replaced.
 *
 * Robolectric-backed, not plain JUnit: every accessor below reads `MaterialTheme.motionScheme`,
 * which is `@Composable @ReadOnlyComposable` — there is no way to read it without a real
 * composition, unlike [com.forager.app.ui.theme.MapPaletteTest] or
 * [com.forager.app.ui.theme.ThemeContrastTest], which operate on plain data objects.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MotionTokensTest {

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

    /** Every named category [MotionTokens] exposes, alongside the raw motion-scheme tier ADR-0002 says it should map to — the property [MotionTokensTest] exists to check, not a hardcoded list of expected values. */
    private lateinit var captured: Map<String, Pair<FiniteAnimationSpec<Float>, FiniteAnimationSpec<Float>>>

    @Composable
    private fun captureSpecs(): Map<String, Pair<FiniteAnimationSpec<Float>, FiniteAnimationSpec<Float>>> {
        val scheme = MaterialTheme.motionScheme
        return mapOf(
            "feedbackMotionSpec" to (MotionTokens.feedbackMotionSpec to scheme.fastSpatialSpec()),
            "navigationMotionSpec" to (MotionTokens.navigationMotionSpec to scheme.defaultSpatialSpec()),
            "panelMotionSpec" to (MotionTokens.panelMotionSpec to scheme.slowSpatialSpec()),
            "markerEntranceSpec" to (MotionTokens.markerEntranceSpec to scheme.defaultSpatialSpec()),
            "selectionPulseSpec" to (MotionTokens.selectionPulseSpec to scheme.slowSpatialSpec()),
            "dataLayerOverlaySpec" to (MotionTokens.dataLayerOverlaySpec to scheme.defaultEffectsSpec()),
            "narrativeRevealSpec" to (MotionTokens.narrativeRevealSpec to scheme.slowEffectsSpec()),
        )
    }

    /** Category names whose §2 behaviour is "effects" (cross-fade/overlay alpha/reveal), not spatial motion — the set R1/ADR-0002's "must not overshoot" argument actually covers. */
    private val effectsCategories = setOf("dataLayerOverlaySpec", "narrativeRevealSpec")

    private fun setUp() {
        composeRule.setContent {
            ForagerTheme {
                captured = captureSpecs()
                Box {}
            }
        }
        composeRule.waitForIdle()
    }

    /** Carried over unchanged from the pre-ADR-0002 test: this constant pair survived the rewrite untouched, and the property it guards (§2 "low-amplitude breathing pulse") has nothing to do with tween vs. spring. */
    @Test
    fun `selection pulse scale range is low-amplitude`() {
        val amplitude = MotionTokens.SELECTION_PULSE_MAX_SCALE - MotionTokens.SELECTION_PULSE_MIN_SCALE
        assertTrue("selection pulse amplitude $amplitude is not low-amplitude", amplitude in 0.0f..0.15f)
    }

    @Test
    fun `no exposed spec is a TweenSpec -- the tween-only rule ADR-0002 supersedes`() {
        setUp()
        for ((name, pair) in captured) {
            val (spec, _) = pair
            assertFalse("$name is a TweenSpec -- ADR-0002 supersedes the tween-only rule for spatial motion", spec is TweenSpec<Float>)
        }
    }

    @Test
    fun `each named category maps to the MotionScheme spec its docs-motion-spec-md category calls for`() {
        setUp()
        for ((name, pair) in captured) {
            val (spec, expectedTierSpec) = pair
            assertTrue("$name must be a SpringSpec (MotionScheme's own specs all are)", spec is SpringSpec<Float>)
            assertTrue("$name's expected tier spec must be a SpringSpec too", expectedTierSpec is SpringSpec<Float>)
            val actual = spec as SpringSpec<Float>
            val expected = expectedTierSpec as SpringSpec<Float>
            assertEquals("$name.dampingRatio must match its named tier", expected.dampingRatio, actual.dampingRatio)
            assertEquals("$name.stiffness must match its named tier", expected.stiffness, actual.stiffness)
        }
    }

    @Test
    fun `effects-category specs have dampingRatio at least 1_0 -- critically damped, never overshoots (R1)`() {
        setUp()
        for (name in effectsCategories) {
            val (spec, _) = captured.getValue(name)
            val springSpec = spec as SpringSpec<Float>
            assertTrue(
                "$name has dampingRatio=${springSpec.dampingRatio}, below 1.0 -- an effects spec must be critically damped or higher so it cannot overshoot (R1, ADR-0002)",
                springSpec.dampingRatio >= 1.0f,
            )
        }
    }

    @Test
    fun `spatial-category specs are not required to be critically damped -- overshoot is the accepted taste call`() {
        setUp()
        val spatialCategories = captured.keys - effectsCategories
        assertTrue("expected at least one spatial category to check", spatialCategories.isNotEmpty())
        // Not asserting dampingRatio < 1.0 here: MotionScheme.standard() is a reversible
        // substitution (ADR-0002, Gate G question 4) whose spatial springs sit at damping 0.9,
        // still below 1.0 but calmer than expressive()'s 0.6-0.8 -- this test must not fail merely
        // because a future session flips that one-line parameter. What matters is only that R1's
        // "must not overshoot" constraint is not wrongly applied to spatial motion, which the
        // effects-only assertion above already keeps true by construction.
        for (name in spatialCategories) {
            val (spec, _) = captured.getValue(name)
            assertTrue("$name must be a SpringSpec", spec is SpringSpec<Float>)
        }
    }
}

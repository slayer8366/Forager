package com.forager.app.ui.motion

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-backed because [isReduceMotionEnabled] reads real [Settings.Global] entries
 * through a [android.content.ContentResolver], which plain JUnit has no Android framework to
 * back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReduceMotionTest {

    private val contentResolver by lazy {
        ApplicationProvider.getApplicationContext<Application>().contentResolver
    }

    @Before
    fun resetSettingsToDefault() {
        Settings.Global.putFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    @Test
    fun `reduce motion is disabled when both scales are at their default`() {
        assertFalse(isReduceMotionEnabled(contentResolver))
    }

    @Test
    fun `reduce motion is enabled when animator duration scale is zero`() {
        Settings.Global.putFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertTrue(isReduceMotionEnabled(contentResolver))
    }

    @Test
    fun `reduce motion is enabled when transition animation scale is zero`() {
        Settings.Global.putFloat(contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        assertTrue(isReduceMotionEnabled(contentResolver))
    }

    @Test
    fun `reduce motion mapping never maps a treatment to nothing`() {
        // docs/motion-spec.md §4: "a mapping layer, not a global kill switch." Every treatment
        // must resolve to a distinct, still-visible ReducedMotionTreatment.
        for (treatment in MotionTreatment.entries) {
            val reduced = reducedMotionEquivalent(treatment)
            assertTrue("no reduced-motion mapping for $treatment", reduced in ReducedMotionTreatment.entries)
        }
    }

    @Test
    fun `reduce motion mapping matches the spec table exactly`() {
        assertEquals(ReducedMotionTreatment.INSTANT_FULL_PATH, reducedMotionEquivalent(MotionTreatment.ROUTE_PROGRESSIVE_DRAW))
        assertEquals(ReducedMotionTreatment.ALPHA_CROSS_FADE_OR_INSTANT, reducedMotionEquivalent(MotionTreatment.MARKER_ENTRANCE))
        assertEquals(ReducedMotionTreatment.STATIC_EMPHASIS, reducedMotionEquivalent(MotionTreatment.SELECTION_PULSE))
        assertEquals(ReducedMotionTreatment.FADE_OR_INSTANT, reducedMotionEquivalent(MotionTreatment.PANEL_MOTION))
    }
}

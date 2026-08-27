package com.forager.app.ui.theme

import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Guards understory-design-system.md's step 2: [ForagerTheme] must actually be a
 * [androidx.compose.material3.MaterialExpressiveTheme] supplying all four token axes, not
 * [androidx.compose.material3.MaterialTheme] silently reverted underneath it, or one axis quietly
 * left to default while the other three are supplied.
 *
 * Pure-Kotlin, like [ThemeCompletenessTest] and [ThemeContrastTest] beside it — [ForagerTypography],
 * [ForagerShapes] and [ForagerMotionScheme] are plain values, not Composables, so asserting on them
 * directly answers "did this theme wire in something real" without needing a Robolectric
 * composition to read the values back out of.
 */
class ExpressiveThemeTest {

    @Test
    fun `ForagerTypography is not the stock default`() {
        assertNotEquals(
            "ForagerTypography must differ from bare Typography() — otherwise ForagerTheme is " +
                "not actually supplying a type scale, whatever the call site claims.",
            Typography(),
            ForagerTypography,
        )
    }

    @Test
    fun `ForagerShapes is not the stock default`() {
        assertNotEquals(
            "ForagerShapes must differ from bare Shapes() — otherwise ForagerTheme is not " +
                "actually supplying a shape scale, whatever the call site claims.",
            Shapes(),
            ForagerShapes,
        )
    }

    /**
     * [MotionScheme.expressive] and [MotionScheme.standard] each return a fixed singleton
     * (`ExpressiveMotionSchemeImpl`/`StandardMotionSchemeImpl` in the real 1.5.0-alpha26 sources),
     * so reference equality is the correct check here, not structural equality on some derived
     * property.
     */
    @Test
    fun `ForagerMotionScheme is expressive, not standard`() {
        assertSame(MotionScheme.expressive(), ForagerMotionScheme)
        assertNotSame(MotionScheme.standard(), ForagerMotionScheme)
    }
}

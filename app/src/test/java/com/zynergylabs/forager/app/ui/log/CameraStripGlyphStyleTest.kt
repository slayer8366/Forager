package com.zynergylabs.forager.app.ui.log

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import com.zynergylabs.forager.app.domain.GridMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RECORD -970 (the owner, on the strip icons: "C both"): the thin-line (Outlined) style of the same symbols, and a thinner
 * outline. The flash, timer and location glyphs are held by their own chips' tests; the grid chip had no glyph test and the
 * gear's glyph was inline, so both are held here. Was `Icons.Filled.*`, same names. The outline's
 * width is not held as a constant here; that it is still drawn around every glyph is `CameraStripOutlinePixelsTest`'s, and how
 * wide it looks is the screenshots' (`CameraStripIconsScreenshotTest`) and the device's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraStripGlyphStyleTest {

    @Test
    fun `the grid chip uses the Outlined glyphs`() {
        assertEquals(GridGlyph(Icons.Outlined.GridOff, GRID_OFF_LABEL), gridGlyph(GridMode.Off))
        assertEquals(GridGlyph(Icons.Outlined.GridOn, GRID_ON_LABEL), gridGlyph(GridMode.Grid))
        assertEquals(GridGlyph(Icons.Outlined.Straighten, GRID_AND_LEVEL_LABEL), gridGlyph(GridMode.GridLevel))
    }

    @Test
    fun `the gear uses the Outlined glyph`() {
        assertEquals(Icons.Outlined.Settings, CAMERA_SETTINGS_GLYPH)
    }
}

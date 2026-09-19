package com.zynergylabs.forager.app.ui.log

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [systemBarIconsAreWhite], the one decision behind the system bars' icon colour. Pure and headless,
 * because the thing it replaced was not: the camera used to set the appearance on the window itself
 * and lose it a second later to `MainActivity`'s own `enableEdgeToEdge`, which re-runs on every
 * recomposition. Measured on the emulator — `useWhiteIcons: previous=true readBack=false` at the
 * call, `poll: lightStatusBars=true` on every poll 1.5 s later — and the answer was to have one
 * place decide rather than two assert.
 *
 * What no test here can show is what the icons then look like on a phone, or the grey band One UI
 * draws behind a revealed bar, which is not this app's to set at all (`CameraWindowChrome.kt`).
 */
class CameraSystemBarIconsTest {

    @Test
    fun `a light app theme with the camera closed keeps dark icons, which is what the app's own background wants`() {
        assertFalse(systemBarIconsAreWhite(appThemeIsDark = false, cameraIsOpen = false))
    }

    @Test
    fun `the camera makes them white even under a light theme, because a viewfinder is not the app's background`() {
        assertTrue(systemBarIconsAreWhite(appThemeIsDark = false, cameraIsOpen = true))
    }

    @Test
    fun `a dark app theme keeps them white with the camera closed, unchanged from before the camera existed`() {
        assertTrue(systemBarIconsAreWhite(appThemeIsDark = true, cameraIsOpen = false))
    }

    @Test
    fun `and with both, still white`() {
        assertTrue(systemBarIconsAreWhite(appThemeIsDark = true, cameraIsOpen = true))
    }
}

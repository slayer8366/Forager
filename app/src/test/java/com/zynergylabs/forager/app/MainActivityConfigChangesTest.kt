package com.zynergylabs.forager.app

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins `MainActivity`'s `android:configChanges` to exactly the set the manifest's own comment
 * argues for, read back from the merged manifest through `PackageManager` rather than from the
 * source file: this is what the platform will consult. Both directions are asserted, the four
 * that are handled and the two that are deliberately not (`uiMode`, `smallestScreenSize`), so a
 * later "helpful" widening is a failing test with the reasoning one hop away.
 *
 * Whether a handled rotation keeps the Compose dialog window correctly sized is not knowable
 * here; Robolectric does not resize anything. That is the device-only item of this step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityConfigChangesTest {

    @Test
    fun `MainActivity handles orientation, screenSize, screenLayout and keyboardHidden itself, and nothing else`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)

        val handled = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SCREEN_LAYOUT or ActivityInfo.CONFIG_KEYBOARD_HIDDEN
        assertEquals("handled set, read from the merged manifest", handled, info.configChanges and handled)
        assertEquals("uiMode is left to recreate, on purpose", 0, info.configChanges and ActivityInfo.CONFIG_UI_MODE)
        assertEquals("smallestScreenSize is left to recreate, on purpose", 0, info.configChanges and ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE)
    }
}

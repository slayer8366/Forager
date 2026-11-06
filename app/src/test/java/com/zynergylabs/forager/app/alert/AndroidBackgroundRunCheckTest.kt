package com.zynergylabs.forager.app.alert

import android.app.ActivityManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.BackgroundRun
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * [AndroidBackgroundRunCheck]'s one platform read (dispatch 2026-09-28-626; Amendment 1, RECORD
 * -627), driven through Robolectric's `ActivityManager`: Restricted is blocked, anything else is
 * allowed, and a phone older than Android 9 says it cannot tell.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidBackgroundRunCheckTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    private fun setRestricted(restricted: Boolean) {
        Shadows.shadowOf(context().getSystemService(ActivityManager::class.java)).setBackgroundRestricted(restricted)
    }

    @Test
    @Config(sdk = [36])
    fun `restricted is blocked, unrestricted is allowed`() {
        setRestricted(true)
        assertEquals(BackgroundRun.BLOCKED, AndroidBackgroundRunCheck(context()).current())
        setRestricted(false)
        assertEquals(BackgroundRun.ALLOWED, AndroidBackgroundRunCheck(context()).current())
    }

    @Test
    @Config(sdk = [27])
    fun `Android 8 has no restricted mode to read, and says so`() {
        assertEquals(BackgroundRun.UNSUPPORTED, AndroidBackgroundRunCheck(context()).current())
    }
}

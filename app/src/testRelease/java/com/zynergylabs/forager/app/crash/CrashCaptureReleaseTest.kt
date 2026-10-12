package com.zynergylabs.forager.app.crash

import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ForagerApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Release builds capture no crash files (dispatch 2026-10-11; the planner's call in RECORD -830:
 * "crash-log capture follows its viewer to debug only, since nothing in a release build can read
 * it"). Starting the app installs no crash-capturing handler, the platform's own stays in place, and
 * the capturing class is not in the release build. `CrashCaptureDebugTest` is the debug half.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrashCaptureReleaseTest {

    @Test
    fun `starting a release build installs no crash-capturing handler`() {
        ApplicationProvider.getApplicationContext<ForagerApplication>()
        assertNotEquals(
            "a release build installed the crash-capturing handler",
            CAPTURING_HANDLER,
            Thread.getDefaultUncaughtExceptionHandler()?.javaClass?.name,
        )
    }

    @Test
    fun `the crash-capturing handler is not in a release build`() {
        assertEquals("present in release: $CAPTURING_HANDLER", false, runCatching { Class.forName(CAPTURING_HANDLER) }.isSuccess)
    }

    private companion object {
        const val CAPTURING_HANDLER = "com.zynergylabs.forager.app.crash.CrashUncaughtExceptionHandler"
    }
}

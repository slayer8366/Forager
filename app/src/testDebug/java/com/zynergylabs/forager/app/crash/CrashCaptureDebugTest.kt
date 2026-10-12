package com.zynergylabs.forager.app.crash

import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ForagerApplication
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Debug builds keep crash capture (dispatch 2026-10-11, RECORD -830): starting the app installs
 * [CrashUncaughtExceptionHandler] as the default handler. What the handler writes is
 * `CrashUncaughtExceptionHandlerTest`'s; this is that the app installs it, the step the release
 * build no longer takes (`CrashCaptureReleaseTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrashCaptureDebugTest {

    @Test
    fun `starting a debug build installs the crash-capturing handler`() {
        ApplicationProvider.getApplicationContext<ForagerApplication>()
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertTrue("the default handler after start is ${handler?.javaClass?.name}", handler is CrashUncaughtExceptionHandler)
    }
}

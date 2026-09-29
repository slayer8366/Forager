package com.zynergylabs.forager.app

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exported GPX files older than an hour leave the cache at app start (F5, dispatch 2026-09-28-216, owner "3 A"),
 * through the real entry point: the application's own `onCreate`. Robolectric already ran it once before the
 * test body, so the test lays its files down and runs it again, as a new process start would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForagerApplicationGpxStartTest {

    @Test
    fun `startup removes an export older than an hour from the cache and keeps a recent one`() {
        val app = ApplicationProvider.getApplicationContext<ForagerApplication>()
        val dir = File(app.cacheDir, "tracks").apply { mkdirs() }
        val stale = File(dir, "forager-track-old.gpx").apply { writeText("<gpx/>"); check(setLastModified(System.currentTimeMillis() - 61 * 60_000L)) }
        val fresh = File(dir, "forager-track-recent.gpx").apply { writeText("<gpx/>"); check(setLastModified(System.currentTimeMillis() - 59 * 60_000L)) }

        app.onCreate()

        val deadline = System.currentTimeMillis() + 5_000
        while (stale.exists() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertFalse("an export 61 minutes old is still in the cache after startup", stale.exists())
        assertTrue("an export 59 minutes old was deleted at startup", fresh.exists())
    }
}

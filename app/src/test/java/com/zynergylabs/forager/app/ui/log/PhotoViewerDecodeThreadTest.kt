package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LogPhoto
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The photo viewer's decode follows [PhotoDecodeDispatcher], as [DecodedPhoto]'s does (dispatch 2026-09-28-351), so under
 * the unit tests' default its decode, its state write and the recomposition after it all run on the main test thread.
 *
 * Why (pre-main run 41766149924, `PhotoViewerDialogTest` "several photos step…"): the viewer's `ZoomablePhoto` decoded
 * with `withContext(Dispatchers.IO)` directly, outside the seam. The Compose test rule resumes an effect inline on the
 * thread that resumed it (its UnconfinedTestDispatcher), so the effect's state write, and then a whole recomposition
 * frame, ran on the IO worker while the main thread was observing layout; Compose threw "Detected multithreaded access
 * to SnapshotStateObserver" from that worker. The same mechanism [PhotoDecodeDispatcher]'s doc records for
 * `DecodedPhoto`. A race, so this test reads the threads, which it can do every time, rather than waiting for the race.
 * Recorded as [PhotoDecodeThreadTest] records them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ThreadRecordingBitmapFactoryShadow::class])
class PhotoViewerDecodeThreadTest {

    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        object : ExternalResource() {
            override fun before() {
                val app = ApplicationProvider.getApplicationContext<Application>()
                Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
            }
        },
    ).around(composeRule)

    private val applyThreads = CopyOnWriteArrayList<Thread>()

    @Before
    fun clearRecordings() {
        ThreadRecordingBitmapFactoryShadow.DECODE_THREADS.clear()
    }

    private fun photo(name: String): LogPhoto {
        val context = ApplicationProvider.getApplicationContext<Application>()
        File(context.filesDir, "photos/$name").apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3, 4))
        return LogPhoto(id = name, relativePath = "photos/$name", createdAtEpochMillis = 1_000L)
    }

    private fun awaitPhoto() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(VIEWER_PHOTO_DESCRIPTION).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun describe(threads: List<Thread>) = threads.map { it.name }.distinct().toString()

    @Test
    fun `under the test default the viewer's decodes, opening and stepping, and their state writes run on the main test thread`() {
        val main = Looper.getMainLooper().thread
        val photos = listOf(photo("viewer-a.jpg"), photo("viewer-b.jpg"))
        val handle = Snapshot.registerApplyObserver { _, _ -> applyThreads.add(Thread.currentThread()) }
        try {
            composeRule.setContent { PhotoViewerDialog(photos = photos, initialIndex = 0, onDismiss = {}) }
            awaitPhoto()
            composeRule.onNodeWithContentDescription("Next photo").performTouchInput { click() }
            composeRule.waitForIdle()
            awaitPhoto()
        } finally {
            handle.dispose()
        }

        val decodeThreads = ThreadRecordingBitmapFactoryShadow.DECODE_THREADS.toList()
        assertEquals("reachability: the viewer decoded each of the two photos once", 2, decodeThreads.size)
        assertTrue("reachability: the decodes' state writes produced apply notifications", applyThreads.isNotEmpty())
        assertTrue("the viewer's decode ran on ${describe(decodeThreads)}, not the main thread", decodeThreads.all { it === main })
        assertTrue("a snapshot apply (the viewer's state write) ran on ${describe(applyThreads)}, not the main thread", applyThreads.all { it === main })
    }
}

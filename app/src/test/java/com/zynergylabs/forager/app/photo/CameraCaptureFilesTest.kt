package com.zynergylabs.forager.app.photo

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [CameraCaptureFiles.sweepOrphans], and what [CameraCaptureFiles.newCapture] does and does not
 * create.
 *
 * **The sweep tests run on an isolated `filesDir`, deliberately.** `ForagerApplication.onCreate`
 * launches a sweep of the *real* `filesDir/captures/` on `Dispatchers.IO`, and Robolectric
 * instantiates `ForagerApplication` for every test method, so that sweep is running beside every
 * test in this suite. A sweep test on the real directory would race it: a file this test
 * backdates to look like an orphan could be deleted by the application's sweep before this test's
 * own call, and the count assertion would fail on whichever thread lost. A [ContextWrapper] whose
 * `getFilesDir` points somewhere the application never looks removes the race by construction
 * rather than by timing. The `newCapture` test uses the real context, because `FileProvider`
 * only knows the real `filesDir`.
 *
 * The directory name is asserted, not assumed: the sweep tests write straight into `captures/`,
 * and `newCapture issues …` checks that is where a capture actually lands, so the two cannot
 * silently diverge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraCaptureFilesTest {

    @get:Rule
    val fileProviderCache = FileProviderCacheReset()

    private val realContext: Application get() = ApplicationProvider.getApplicationContext()

    private fun isolated(): Pair<Context, File> {
        val filesDir = File(realContext.cacheDir, "isolated-files-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(realContext) {
            override fun getFilesDir(): File = filesDir
        }
        return context to File(filesDir, "captures").apply { mkdirs() }
    }

    @Test
    fun `newCapture issues a content URI under captures and creates the directory but not the file`() {
        val capture = CameraCaptureFiles(realContext).newCapture()

        assertEquals("the production URI shape", "content", capture.uri.scheme)
        assertEquals("captures", capture.file.parentFile!!.name)
        assertTrue(capture.file.parentFile!!.isDirectory)
        assertFalse("the camera writes the file; issuing a destination must not", capture.file.exists())
    }

    @Test
    fun `sweepOrphans deletes files older than the process and keeps this process's own`() {
        val (context, captures) = isolated()
        val processStart = System.currentTimeMillis()
        val orphan = File(captures, "orphan.jpg").apply { writeBytes(byteArrayOf(1)); assertTrue(setLastModified(processStart - 60_000)) }
        val live = File(captures, "live.jpg").apply { writeBytes(byteArrayOf(2)); assertTrue(setLastModified(processStart + 60_000)) }

        val deleted = CameraCaptureFiles(context).sweepOrphans(processStartedAtMillis = processStart)

        assertEquals(1, deleted)
        assertFalse("a minute-old file belongs to a process that is gone", orphan.exists())
        assertTrue("a file from this process is live and must survive", live.exists())
    }

    /** The guard that makes the sweep safe to run beside live captures on a coarse-mtime filesystem. */
    @Test
    fun `a file inside the guard window is kept even though it predates the process start`() {
        val (context, captures) = isolated()
        val processStart = System.currentTimeMillis()
        val young = File(captures, "young.jpg").apply { writeBytes(byteArrayOf(3)); assertTrue(setLastModified(processStart - 1_000)) }

        val deleted = CameraCaptureFiles(context).sweepOrphans(processStartedAtMillis = processStart)

        assertEquals(0, deleted)
        assertTrue("1 s before start is inside the 2 s guard", young.exists())
    }

    @Test
    fun `sweepOrphans on an empty directory deletes nothing and reports zero`() {
        val (context, _) = isolated()

        assertEquals(0, CameraCaptureFiles(context).sweepOrphans(processStartedAtMillis = System.currentTimeMillis()))
    }
}

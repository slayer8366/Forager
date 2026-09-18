package com.zynergylabs.forager.app.ui.diagnostics

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.diagnostics.DiagnosticsLog
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FilePhotoStore
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.photo.GalleryImportPhotoSource
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
 * The debug-only [DiagnosticsPanel], driven through its real controls against the real Robolectric
 * `filesDir` and the real `FileProvider` — the share tests need the provider's own roots, so the
 * directories are the app's actual `photos/` and `captures/`, not a temp folder, and the
 * [FileProviderCacheReset] rule is here for the same reason it is on every other class that mints a
 * capture URI. What a share test asserts is the chooser's inner intent: `ACTION_SEND`, the MIME
 * type, and a `content://` URI under this app's authority whose last segment names the file.
 *
 * `ForagerApplication` is instantiated per test and sweeps `captures/` of anything older than the
 * process at startup; the capture written here is younger than the process, so the sweep leaves it.
 * It also writes to the production diagnostics log at startup, which is why the log under test is
 * not that one — see `setUp`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class DiagnosticsPanelTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FileProviderCacheReset()).around(declareHostActivity).around(composeRule)

    private lateinit var context: Application
    private lateinit var photosDir: File
    private lateinit var capturesDir: File
    private lateinit var log: DiagnosticsLog
    private var backPressed = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        photosDir = File(context.filesDir, PHOTOS_DIRECTORY).apply { mkdirs() }
        capturesDir = File(context.filesDir, CAPTURES_DIRECTORY).apply { mkdirs() }
        // Not DiagnosticsLog.forContext: that is the production file, and ForagerApplication —
        // instantiated for every Robolectric test — writes its own process-start and sweep lines
        // there on a worker thread, so a test reading it can never see it empty and can race the
        // write. This one is the same file name under the internal `diagnostics/` root, which the
        // debug file_paths.xml serves too (`diagnostics-internal`), so the share test still mints a
        // real provider URI for it.
        log = DiagnosticsLog(File(File(context.filesDir, DiagnosticsLog.DIRECTORY_NAME), DiagnosticsLog.FILE_NAME))
    }

    private fun setPanel() {
        composeRule.setContent {
            DiagnosticsPanel(photosDir = photosDir, capturesDir = capturesDir, log = log, onBack = { backPressed++ })
        }
    }

    /** The listing is read on IO, so the first assertion on any file name waits for it. */
    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    /** The log row exists only once the listing has landed; see the note in the log-row test. */
    private fun awaitLogRow() {
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithTag(DIAGNOSTICS_LOG_ROW_TAG).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `lists each directory's files with name, size and modified time, and a count per directory`() {
        val older = File(photosDir, "older.jpg").apply { writeBytes(ByteArray(3_000)); setLastModified(1_700_000_000_000L) }
        val newer = File(photosDir, "newer.jpg").apply { writeBytes(ByteArray(1_500)); setLastModified(1_700_000_060_000L) }
        val capture = File(capturesDir, "in-flight.jpg").apply { writeBytes(ByteArray(10)) }

        setPanel()
        awaitText("older.jpg")

        composeRule.onNodeWithText(directoryHeading("photos/", 2)).assertIsDisplayed()
        composeRule.onNodeWithText(directoryHeading("captures/", 1)).assertIsDisplayed()
        composeRule.onNodeWithText("newer.jpg").assertIsDisplayed()
        composeRule.onNodeWithText("in-flight.jpg").assertIsDisplayed()
        composeRule.onNodeWithText("${formatBytes(3_000)} · ${formatModified(older.lastModified())}").assertIsDisplayed()
        composeRule.onNodeWithText("${formatBytes(1_500)} · ${formatModified(newer.lastModified())}").assertIsDisplayed()
        composeRule.onNodeWithText("${formatBytes(10)} · ${formatModified(capture.lastModified())}").assertIsDisplayed()
        assertEquals("2.9 KB", formatBytes(3_000))
        assertEquals("10 B", formatBytes(10))
        assertEquals("3.0 MB", formatBytes(3 * 1024 * 1024L))
    }

    /** The directory names are asserted against the real writers, not assumed to match their private constants. */
    @Test
    fun `a photo persisted by FilePhotoStore and a capture issued by CameraCaptureFiles both appear`() {
        val source = File(context.cacheDir, "import-source.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())) }
        val persisted = runBlocking { FilePhotoStore(context).persist(GalleryImportPhotoSource(Uri.fromFile(source))).getOrThrow() }
        val capture = CameraCaptureFiles(context).newCapture().also { it.file.writeBytes(byteArrayOf(1, 2, 3)) }

        setPanel()
        awaitText(capture.file.name)

        composeRule.onNodeWithText(File(persisted.relativePath).name).assertIsDisplayed()
        composeRule.onNodeWithText(directoryHeading("photos/", 1)).assertIsDisplayed()
        composeRule.onNodeWithText(directoryHeading("captures/", 1)).assertIsDisplayed()
    }

    @Test
    fun `empty directories say so with a zero count, and a missing one lists as empty too`() {
        assertTrue(photosDir.delete())

        setPanel()
        awaitText(directoryHeading("photos/", 0))

        composeRule.onNodeWithText(directoryHeading("captures/", 0)).assertIsDisplayed()
        composeRule.onAllNodesWithText(NO_FILES_LABEL).fetchSemanticsNodes().let { assertEquals(2, it.size) }
    }

    @Test
    fun `sharing a photo starts an ACTION_SEND chooser with a content URI naming that file`() {
        File(photosDir, "share-me.jpg").writeBytes(ByteArray(64))
        setPanel()
        awaitText("share-me.jpg")

        composeRule.onNodeWithTag(diagnosticsShareTag(File(photosDir, "share-me.jpg"))).performClick()
        val started = awaitStartedActivity()

        assertEquals(Intent.ACTION_CHOOSER, started.action)
        val inner = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertEquals("image/jpeg", inner.type)
        val uri = inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertEquals("share-me.jpg", uri.lastPathSegment)
        assertTrue(inner.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        composeRule.onAllNodesWithTag(DIAGNOSTICS_SHARE_ERROR_TAG).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    fun `sharing the log starts a text chooser with a content URI for the log file`() {
        log.append("sweep deleted=0 orphaned capture file(s)")
        setPanel()
        awaitText(directoryHeading("photos/", 0))

        composeRule.onNodeWithTag(DIAGNOSTICS_LOG_SHARE_TAG).performClick()
        val started = awaitStartedActivity()

        val inner = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertEquals("text/plain", inner.type)
        val uri = inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertEquals(DiagnosticsLog.FILE_NAME, uri.lastPathSegment)
    }

    @Test
    fun `the log row shows the log's own text, and an empty log says so`() {
        setPanel()
        awaitText(directoryHeading("photos/", 0))
        composeRule.onNodeWithText(formatBytes(0)).assertIsDisplayed()

        composeRule.onNodeWithTag(DIAGNOSTICS_LOG_ROW_TAG).performClick()
        awaitText(EMPTY_LOG_LABEL)
        composeRule.onNodeWithContentDescription("Back to Diagnostics").performClick()
        // Leaving the detail drops the listing state; the list re-reads on IO and shows
        // "Reading…" until it lands, with no log row to tap. Locally the read won that race in
        // four full runs; on the CI runner it lost once (0efce43, AssertionError at the click
        // below), so the row is awaited, the same way the first listing is.
        awaitLogRow()

        log.append("sweep deleted=3 orphaned capture file(s)")
        log.append("strictmode DiskReadViolation", "at com.example.Persist.run(Persist.kt:1)")
        composeRule.onNodeWithTag(DIAGNOSTICS_LOG_ROW_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DIAGNOSTICS_LOG_TEXT_TAG).fetchSemanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString()?.contains("sweep deleted=3") == true
            }
        }
        val shown = composeRule.onNodeWithTag(DIAGNOSTICS_LOG_TEXT_TAG).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        assertTrue(shown, shown.contains("strictmode DiskReadViolation\n    at com.example.Persist.run(Persist.kt:1)"))
    }

    /**
     * The condition that killed the app on the AVD (a `diagnostics.log` the process cannot open)
     * makes a *read* fail as well as a write, so the panel meant to show the failure must survive
     * opening it. A directory where the file should be stands in for the device's EACCES: the same
     * `FileNotFoundException`, without depending on permissions the test's own user could override.
     */
    @Test
    fun `a log that cannot be read shows why when opened, instead of taking the panel down`() {
        log = DiagnosticsLog(File(File(context.filesDir, "unreadable-diagnostics"), DiagnosticsLog.FILE_NAME).apply { mkdirs() })
        setPanel()
        awaitLogRow()

        composeRule.onNodeWithTag(DIAGNOSTICS_LOG_ROW_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DIAGNOSTICS_LOG_TEXT_TAG).fetchSemanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString()?.startsWith("Couldn't read the log:") == true
            }
        }
        val shown = composeRule.onNodeWithTag(DIAGNOSTICS_LOG_TEXT_TAG).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        assertTrue("expected the read's own error, got: $shown", shown.contains("Is a directory"))
    }

    /**
     * The dispatch's requirement: a runner can tell "the log stopped recording" from "nothing
     * happened". Two writes into a file that cannot be opened, then the row, which must say so in
     * the words [logWriteFailureText] builds — count, time and error — rather than show a size that
     * has quietly stopped changing.
     */
    @Test
    fun `a log whose writes fail says so on its row`() {
        log = DiagnosticsLog(File(File(context.filesDir, "unwritable-diagnostics"), DiagnosticsLog.FILE_NAME).apply { mkdirs() })
        log.append("sweep deleted=0 orphaned capture file(s)")
        log.append("process started pid=1")
        val failure = checkNotNull(log.writeFailure) { "precondition: both writes should have failed" }
        assertEquals(2, failure.failedEntries)

        setPanel()
        awaitLogRow()

        val shown = composeRule.onNodeWithTag(DIAGNOSTICS_LOG_WRITE_FAILURE_TAG, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        assertEquals(logWriteFailureText(failure), shown)
        assertTrue(shown, shown.startsWith("Not recording: 2 entries could not be written since "))
    }

    @Test
    fun `a log that is writing shows no failure line`() {
        log.append("sweep deleted=0 orphaned capture file(s)")
        setPanel()
        awaitLogRow()

        composeRule.onAllNodesWithTag(DIAGNOSTICS_LOG_WRITE_FAILURE_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * The production panel must read the instance that writes, or it reports "no failure" whatever
     * happened: a fresh `forContext` instance has never tried a write. Identity, not equality —
     * [DiagnosticsLog] has no `equals`, and the property that matters is which object holds the state.
     */
    @Test
    fun `the production panel reads the log instance the app's diagnostics writes through`() {
        val app = ApplicationProvider.getApplicationContext<ForagerApplication>()
        assertSame(app.diagnostics.log, writerLog(app))
    }

    @Test
    fun `nothing is started until a share action is tapped`() {
        File(photosDir, "quiet.jpg").writeBytes(ByteArray(8))
        setPanel()
        awaitText("quiet.jpg")

        composeRule.onNodeWithTag(DIAGNOSTICS_LOG_ROW_TAG).performClick()
        awaitText(EMPTY_LOG_LABEL)

        assertNull(Shadows.shadowOf(composeRule.activity).nextStartedActivity)
    }

    /** Read-only, asserted on bytes and names rather than promised: listing, viewing and sharing change nothing in the directories they measure. */
    @Test
    fun `listing, viewing and sharing leave both directories byte for byte as they were`() {
        val photo = File(photosDir, "keep.jpg").apply { writeBytes(ByteArray(2_048) { it.toByte() }) }
        val capture = File(capturesDir, "keep-too.jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val before = listOf(photo, capture).map { it.name to it.readBytes() }

        setPanel()
        awaitText("keep.jpg")
        composeRule.onNodeWithTag(diagnosticsShareTag(photo)).performClick()
        awaitStartedActivity()
        composeRule.onNodeWithTag(diagnosticsShareTag(capture)).performClick()
        awaitStartedActivity()
        composeRule.onNodeWithTag(DIAGNOSTICS_LOG_ROW_TAG).performClick()
        awaitText(EMPTY_LOG_LABEL)
        composeRule.onNodeWithContentDescription("Back to Diagnostics").performClick()
        composeRule.onNodeWithContentDescription("Back to Settings").performClick()

        assertEquals(1, backPressed)
        assertEquals(listOf("keep.jpg"), photosDir.list()!!.toList())
        assertEquals(listOf("keep-too.jpg"), capturesDir.list()!!.toList())
        before.forEach { (name, bytes) -> assertArrayEquals(name, bytes, File(if (name == "keep.jpg") photosDir else capturesDir, name).readBytes()) }
    }

    /**
     * The share hops to IO for the URI before starting the chooser, so this polls for the started
     * activity the way the Recorded Tracks share test does; `nextStartedActivity` dequeues, so the
     * first hit is kept rather than re-read.
     */
    private fun awaitStartedActivity(): Intent {
        var started: Intent? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            started = started ?: Shadows.shadowOf(composeRule.activity).nextStartedActivity
            started != null
        }
        return started!!
    }
}

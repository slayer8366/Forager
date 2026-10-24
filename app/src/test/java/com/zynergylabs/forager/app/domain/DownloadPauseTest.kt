package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.map.downloadErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An offline-area download pauses on signal loss instead of failing (dispatch 2026-10-23-06 Amendment 3). */
class DownloadPauseTest {

    @Test
    fun `the pause limit is the owner's 5 minutes`() {
        assertEquals(300_000L, DOWNLOAD_PAUSE_LIMIT_MS)
    }

    @Test
    fun `a connection or server error pauses the download, timed from the first error`() {
        val pause = DownloadPause()
        assertEquals(DownloadPauseDecision.Paused(giveUpAtMs = 301_000), pause.onError(DownloadErrorKind.Pausable, 40, 1_000))
        assertTrue(pause.isPaused)
        assertEquals(DownloadPauseDecision.StillPaused, pause.onError(DownloadErrorKind.Pausable, 40, 60_000))
        assertFalse("due before 5 minutes", pause.giveUpDue(300_999))
        assertTrue("due at 5 minutes from the first error", pause.giveUpDue(301_000))
    }

    @Test
    fun `progress ends the pause, and a later loss starts a new one`() {
        val pause = DownloadPause()
        pause.onError(DownloadErrorKind.Pausable, 40, 1_000)
        assertFalse("no new resource is not progress", pause.onProgress(40))
        assertTrue(pause.onProgress(41))
        assertFalse(pause.isPaused)
        assertFalse(pause.giveUpDue(400_000))
        assertEquals(DownloadPauseDecision.Paused(giveUpAtMs = 800_000), pause.onError(DownloadErrorKind.Pausable, 90, 500_000))
    }

    @Test
    fun `any other error fails the download at once, as before`() {
        val pause = DownloadPause()
        assertEquals(DownloadPauseDecision.Fail, pause.onError(DownloadErrorKind.Final, 0, 0))
        pause.onError(DownloadErrorKind.Pausable, 5, 0)
        assertEquals("a 404 while paused still fails", DownloadPauseDecision.Fail, pause.onError(DownloadErrorKind.Final, 5, 1_000))
    }

    @Test
    fun `MapLibre's connection and server reasons pause, and not-found and other reasons fail`() {
        assertEquals(DownloadErrorKind.Pausable, downloadErrorKind("REASON_CONNECTION"))
        assertEquals(DownloadErrorKind.Pausable, downloadErrorKind("REASON_SERVER"))
        assertEquals(DownloadErrorKind.Final, downloadErrorKind("REASON_NOT_FOUND"))
        assertEquals(DownloadErrorKind.Final, downloadErrorKind("REASON_OTHER"))
    }
}

package com.zynergylabs.forager.app.domain

/**
 * Whether an offline-area download that reports an error is paused or failed (dispatch 2026-10-23-06 Amendment 3; the
 * owner chose "Pause + own lane"). Pure Kotlin, so the rule is tested without MapLibre; the download that applies it
 * is `MapLibreOfflineMapRepository`.
 *
 * **Why a pause is enough.** MapLibre Native 13.5.0 keeps a failed download request alive and retries it itself: a
 * non-404 error is only reported (`offline_download.cpp:522-533`, tag android-v13.5.0), and the request stays with the
 * engine's retry and its network-return signal (`online_file_source.cpp:587-593`). What used to end the download was
 * the app taking that first report as final (verify-first for Amendment 2, step 4 on the S22: the download failed
 * 0.4 s after Wi-Fi went off). So the app now waits instead, and lets the engine continue.
 *
 * The owner's words for it (Amendment 1): "Up to 5 minutes without signal, then the usual error. Nothing to tap if
 * signal comes back."
 */
class DownloadPause(private val limitMs: Long = DOWNLOAD_PAUSE_LIMIT_MS) {
    private var pausedSince: Long? = null
    private var completedAtPause = 0L

    init {
        require(limitMs > 0) { "limitMs must be positive" }
    }

    /** Whether the download is paused now. */
    val isPaused: Boolean get() = pausedSince != null

    /**
     * The download reported [kind] at [nowMs], having completed [completed] resources. A [DownloadErrorKind.Pausable]
     * error starts a pause, or continues the one already running (its clock is not restarted by a later error); any
     * other error fails the download as before.
     */
    fun onError(kind: DownloadErrorKind, completed: Long, nowMs: Long): DownloadPauseDecision {
        return DownloadPauseDecision.Fail
        if (pausedSince != null) return DownloadPauseDecision.StillPaused
        pausedSince = nowMs
        completedAtPause = completed
        return DownloadPauseDecision.Paused(giveUpAtMs = nowMs + limitMs)
    }

    /**
     * The download reported [completed] resources. More than when the pause began ends the pause: the engine is
     * downloading again. Returns whether this call ended a pause.
     */
    fun onProgress(completed: Long): Boolean {
        if (pausedSince == null || completed <= completedAtPause) return false
        pausedSince = null
        return true
    }

    /** Whether the pause has run [limitMs] at [nowMs] with no progress, so the download fails with the usual error. */
    fun giveUpDue(nowMs: Long): Boolean {
        val since = pausedSince ?: return false
        return nowMs - since >= limitMs
    }
}

/** What an error reported by a download means for it. */
enum class DownloadErrorKind {
    /** A connection error, a timeout or a server error (5xx): the engine retries it, so the download waits. */
    Pausable,

    /** Anything else (404, rate limit, other): the download fails, as before Amendment 3. */
    Final,
}

sealed interface DownloadPauseDecision {
    /** A pause began; if no progress comes by [giveUpAtMs], the download fails. */
    data class Paused(val giveUpAtMs: Long) : DownloadPauseDecision

    /** Already paused; the pause keeps its first start. */
    data object StillPaused : DownloadPauseDecision

    /** The download fails with the usual error now. */
    data object Fail : DownloadPauseDecision
}

/**
 * How long a download waits without progress before the usual error: 5 minutes, the owner's "Up to 5 minutes without
 * signal, then the usual error" (dispatch 2026-10-23-06 Amendment 1). The same figure as a held map tile's limit
 * ([TILE_RETRY_HOLD_LIMIT_MS]) by the same decision, named apart because it times a different thing.
 */
const val DOWNLOAD_PAUSE_LIMIT_MS: Long = 5 * 60_000L

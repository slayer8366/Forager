package com.zynergylabs.forager.app.domain

/**
 * The off-track reminder's two stored values (dispatch 2026-09-28-626, plan task T14; Amendment 1,
 * RECORD -627): whether the reminder is on, and what the phone's background setting was the last
 * time a recording started.
 *
 * DataStore, not Room: both are flat preferences that nothing joins to. The same `Result`-returning
 * shape as [SundownPreferencesRepository], in its own file, one repository per concern.
 */
interface OffTrackReminderPreferenceRepository {

    /**
     * Settings' "Off-track reminder" checkbox. On by default (the owner, Amendment 1: "on by
     * default and stored like Sundown alerts"). Off, the off-track alert does not fire; the
     * Return button's off-track colour is unchanged.
     */
    suspend fun getEnabled(): Result<Boolean>

    suspend fun setEnabled(enabled: Boolean): Result<Unit>

    /**
     * The checkbox as last read, for the one caller that cannot suspend: [ReturnWatch.onFix], on the
     * recording service's collector thread. On (the default) until the store has been read, which
     * happens when the app starts, long before a recording can.
     */
    fun enabledNow(): Boolean

    /**
     * Whether the phone was blocking Forager from running in the background the last time a
     * recording started ([OffTrackReminderCheck]). `false` until a block has been seen.
     */
    suspend fun getLastSeenBlocked(): Result<Boolean>

    suspend fun setLastSeenBlocked(blocked: Boolean): Result<Unit>
}

/**
 * Whether the phone lets Forager run in the background, as Android reports it.
 *
 * [BLOCKED] is the phone's "Restricted" battery mode (`ActivityManager.isBackgroundRestricted`):
 * the owner's "only when truly blocked" (Amendment 1). Ordinary battery optimisation is not
 * [BLOCKED]: almost every app starts optimised, and the recording runs as a foreground service.
 *
 * [UNSUPPORTED] is a phone older than Android 9, where that mode and the call that reads it do not
 * exist. Nothing is claimed about it either way.
 *
 * What none of these can see: a phone maker's own battery manager (Samsung's "Sleeping apps").
 */
enum class BackgroundRun { ALLOWED, BLOCKED, UNSUPPORTED }

/** Owned seam over `ActivityManager`; the Android implementation is `com.zynergylabs.forager.app.alert.AndroidBackgroundRunCheck`. */
fun interface BackgroundRunCheck {
    fun current(): BackgroundRun
}

/**
 * The prompt shown when a recording starts and the phone would block the off-track reminder. The
 * owner's words (dispatch 2026-09-28-626). Tapping it opens Forager's App info page.
 */
const val BACKGROUND_RUN_PROMPT = "To make sure your off-track reminder can buzz, let Forager run in the background"

/**
 * The check made when a recording starts (dispatch 2026-09-28-626; Amendment 1, RECORD -627).
 *
 * The owner's answers: "At Record", "Only when truly blocked", "App info page, once per block".
 * So the prompt is shown when the reminder is on, the phone is [BackgroundRun.BLOCKED], and it was
 * not already blocked the last time a recording started. Once allowed again, a later block prompts
 * again. The last state seen is stored, so this survives the app being closed.
 */
class OffTrackReminderCheck(
    private val backgroundRun: BackgroundRunCheck,
    private val preferences: OffTrackReminderPreferenceRepository,
    private val errorLog: ErrorLog,
) {

    /** Whether to show [BACKGROUND_RUN_PROMPT] for the recording that has just started. */
    suspend fun atRecordingStart(): Boolean {
        val enabled = preferences.getEnabled().getOrElse { error ->
            errorLog.w(TAG, "Couldn't read whether the off-track reminder is on; checking as if on, the default.", error)
            true
        }
        if (!enabled) return false
        return when (backgroundRun.current()) {
            // Android 8 and older: no Restricted mode to read. Nothing stored, nothing shown.
            BackgroundRun.UNSUPPORTED -> false
            BackgroundRun.ALLOWED -> {
                preferences.setLastSeenBlocked(false).onFailure { error ->
                    errorLog.w(TAG, "Couldn't store that background running is allowed; a later block may not prompt.", error)
                }
                false
            }
            BackgroundRun.BLOCKED -> {
                val alreadySeen = preferences.getLastSeenBlocked().getOrElse { error ->
                    // Showing the prompt once too often is better than hiding it.
                    errorLog.w(TAG, "Couldn't read whether the block was already seen; prompting.", error)
                    false
                }
                if (alreadySeen) {
                    false
                } else {
                    preferences.setLastSeenBlocked(true).onFailure { error ->
                        errorLog.w(TAG, "Couldn't store that the block was seen; the prompt may show again.", error)
                    }
                    true
                }
            }
        }
    }

    private companion object {
        const val TAG = "OffTrackReminderCheck"
    }
}

package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Told when a settings file could not be read and was reset to its defaults (dispatch 2026-09-28-658,
 * Amendment 1, RECORD -660, item 2). The owner: "Reset it and say so once (Recommended)".
 */
fun interface SettingsResetListener {
    /** [fileName] is the settings file that was reset, for the log. */
    fun onSettingsReset(fileName: String)

    companion object {
        /** For a repository built where nothing shows the message (most tests). The reset is still logged where it happens. */
        val None = SettingsResetListener { }
    }
}

/**
 * Holds whether the one-time message [SETTINGS_RESET_MESSAGE] is owed. Any number of files reset in
 * one run raise it once; the screen that shows it calls [shown]. Held by `AppContainer` and handed to
 * every settings repository it builds.
 */
class SettingsResetNotice : SettingsResetListener {
    private val _pending = MutableStateFlow(false)

    /** `true` from the first reset until the message has been shown. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    override fun onSettingsReset(fileName: String) {
        _pending.value = true
    }

    /** The message was shown; it is not shown again for the resets already counted. */
    fun shown() {
        _pending.value = false
    }
}

/** The owner's words (RECORD -660, item 2). Shown as a snackbar that stays until dismissed (RECORD -661). */
const val SETTINGS_RESET_MESSAGE = "Some settings couldn't be read and were reset. Check Settings."

/** The snackbar's action, which opens Settings (the owner, RECORD -661: "Snackbar with 'Settings' button"). */
const val SETTINGS_RESET_ACTION_LABEL = "Settings"

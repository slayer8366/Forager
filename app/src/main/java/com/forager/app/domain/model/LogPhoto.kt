package com.forager.app.domain.model

/**
 * One photo attached to a log entry. [relativePath] is relative to app-private storage
 * (`context.filesDir`), never an absolute path — an absolute path breaks across app
 * reinstall/restore, since `filesDir`'s absolute location isn't guaranteed stable. See [PhotoStore].
 */
data class LogPhoto(
    val id: String,
    val relativePath: String,
)

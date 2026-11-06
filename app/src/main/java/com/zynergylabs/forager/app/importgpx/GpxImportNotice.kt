package com.zynergylabs.forager.app.importgpx

import android.content.Context
import android.content.Intent
import com.zynergylabs.forager.app.MainActivity
import com.zynergylabs.forager.app.domain.GpxImportFailure
import com.zynergylabs.forager.app.domain.GpxImportOutcome

/**
 * One finished GPX import for the screen to show: Records, on the first new track, with its note or
 * message (plan T16). [seq] makes two imports with the same outcome two notices.
 */
data class GpxImportNotice(val seq: Long, val outcome: GpxImportOutcome)

/**
 * What the screen says after an import: the owner's words for each failure (-636), the note
 * "Imported N tracks" for a file with several, and nothing for one track, which simply opens.
 * [GpxImportFailure.NO_TRACK] and [GpxImportFailure.SAVE_FAILED] had no ruling; their words are this
 * coder's, disclosed in the T16 report.
 */
fun gpxImportMessage(outcome: GpxImportOutcome): String? = when (outcome) {
    is GpxImportOutcome.Imported -> if (outcome.trackIds.size > 1) "Imported ${outcome.trackIds.size} tracks" else null
    is GpxImportOutcome.Failed -> gpxImportFailureMessage(outcome.reason)
}

fun gpxImportFailureMessage(reason: GpxImportFailure): String = when (reason) {
    GpxImportFailure.UNREADABLE -> "This file couldn't be read as GPX"
    GpxImportFailure.ROUTE_ONLY -> "This file has a route but no track; routes aren't imported yet"
    GpxImportFailure.TOO_BIG -> "This file is too big to import (over 10 MB)."
    GpxImportFailure.NO_TRACK -> "This file has no track to import"
    GpxImportFailure.SAVE_FAILED -> "Couldn't save this file's tracks. Nothing was imported."
}

/** The new tracks' ids, on the Intent that opens [MainActivity] after an Open with or Share import. */
const val EXTRA_GPX_IMPORTED_TRACK_IDS = "com.zynergylabs.forager.app.GPX_IMPORTED_TRACK_IDS"

/** The [GpxImportFailure]'s name, on the same Intent, when the file was not imported. */
const val EXTRA_GPX_IMPORT_FAILURE = "com.zynergylabs.forager.app.GPX_IMPORT_FAILURE"

/**
 * The Intent [GpxImportActivity] opens Forager with once the file is saved: [MainActivity] in Forager's
 * own task, reusing the one that is already open. `NEW_TASK` puts it in Forager's task rather than the
 * sending app's; `CLEAR_TOP | SINGLE_TOP` hands it to the existing [MainActivity] through `onNewIntent`
 * rather than starting a second one. A second one would build a second TrackRecordingViewModel, which
 * takes up a running recording and sends the service ACTION_START again (the verify finding in -636).
 */
fun mainActivityIntentAfterImport(context: Context, outcome: GpxImportOutcome): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .apply {
            when (outcome) {
                is GpxImportOutcome.Imported -> putExtra(EXTRA_GPX_IMPORTED_TRACK_IDS, outcome.trackIds.toTypedArray())
                is GpxImportOutcome.Failed -> putExtra(EXTRA_GPX_IMPORT_FAILURE, outcome.reason.name)
            }
        }

/**
 * The import outcome an Intent carries, or `null` for any other Intent. A failure name this build does
 * not know reads as [GpxImportFailure.UNREADABLE], never as no outcome, so a failed import is never silent.
 */
fun gpxImportOutcomeFrom(intent: Intent?): GpxImportOutcome? {
    if (intent == null) return null
    intent.getStringArrayExtra(EXTRA_GPX_IMPORTED_TRACK_IDS)?.takeIf { it.isNotEmpty() }?.let { return GpxImportOutcome.Imported(it.toList()) }
    val failure = intent.getStringExtra(EXTRA_GPX_IMPORT_FAILURE) ?: return null
    return GpxImportOutcome.Failed(GpxImportFailure.entries.firstOrNull { it.name == failure } ?: GpxImportFailure.UNREADABLE)
}

/** Removes the outcome from [intent] once read, so an Activity recreation does not show it again. */
fun clearGpxImportOutcome(intent: Intent?) {
    intent?.removeExtra(EXTRA_GPX_IMPORTED_TRACK_IDS)
    intent?.removeExtra(EXTRA_GPX_IMPORT_FAILURE)
}

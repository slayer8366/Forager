package com.zynergylabs.forager.app.ui.backup

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.Role
import com.zynergylabs.forager.app.domain.BackupFrequency
import com.zynergylabs.forager.app.domain.RestoreMode
import com.zynergylabs.forager.app.domain.backupFileName
import com.zynergylabs.forager.app.ui.theme.Spacing

/** Test tags for the Backup section. */
internal const val BACKUP_AUTOMATIC_SWITCH_TAG = "backup-automatic-switch"
internal const val BACKUP_MESSAGE_TAG = "backup-message"
internal const val BACKUP_FOLDER_NAME_TAG = "backup-folder-name"
internal const val RESTORE_PROMPT_TAG = "restore-prompt"

internal fun backupFrequencyTag(frequency: BackupFrequency) = "backup-frequency-${frequency.name}"

/**
 * The Backup section of Tools, then Settings (dispatch 2026-09-28-127; the owner's copy, exactly, and no other):
 * "Back up now", "Automatic backup" (a switch), "How often" (Daily, Weekly, Monthly), "Backup folder" with
 * "Choose folder", "Restore from backup", and the five approved messages. The three system pickers (create a
 * file, choose a folder, open a file) are launched from here and their answers go straight to [controls].
 *
 * The chosen folder is shown by its own name, which is data from the picker and not new copy. The prompt is the
 * owner's, with its body verbatim; Replace and Merge are its two decisions and Cancel closes it.
 */
@Composable
internal fun BackupSection(controls: BackupControls, modifier: Modifier = Modifier) {
    val state = controls.state
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* granted or declined: the schedule is already on */ }
    val createBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) controls.onBackUpNow(uri.toString())
    }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) controls.onFolderChosen(uri.toString())
    }
    val chooseBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controls.onRestoreFileChosen(uri.toString())
    }

    // "Try again" after a failed write asks for a new file: the file it made is gone, so the Save picker opens again.
    LaunchedEffect(state.createFileRequested) {
        if (state.createFileRequested) {
            controls.onCreateFileRequestHandled()
            createBackupFile.launch(backupFileName(System.currentTimeMillis()))
        }
    }

    // Turning it on with a folder chosen, the first time only (owner 3.4), is the moment a scheduled run's notification becomes
    // possible, so the ViewModel raises this one-shot and it is asked for here (API 33+). Declining does not stop the schedule: a
    // run's notice then waits for the app, and the permission is not asked for again for backups.
    LaunchedEffect(state.askNotificationPermission) {
        if (state.askNotificationPermission) {
            controls.onNotificationPermissionRequestHandled()
            if (needsNotificationPermission(context)) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Backup", style = MaterialTheme.typography.titleMedium)

        OutlinedButton(
            onClick = { createBackupFile.launch(backupFileName(System.currentTimeMillis())) },
            enabled = !state.busy,
        ) { Text("Back up now") }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Automatic backup", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = state.schedule.enabled,
                onCheckedChange = { on -> controls.onAutomaticChanged(on) },
                modifier = Modifier.testTag(BACKUP_AUTOMATIC_SWITCH_TAG),
            )
        }

        Text("How often", style = MaterialTheme.typography.bodyLarge)
        BackupFrequency.entries.forEach { frequency ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(backupFrequencyTag(frequency))
                    .selectable(
                        selected = frequency == state.schedule.frequency,
                        role = Role.RadioButton,
                        onClick = { controls.onFrequencyChanged(frequency) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RadioButton(selected = frequency == state.schedule.frequency, onClick = null)
                Text(frequency.label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Text("Backup folder", style = MaterialTheme.typography.bodyLarge)
        state.schedule.folderUri?.let { folder ->
            Text(
                folderName(folder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(BACKUP_FOLDER_NAME_TAG),
            )
        }
        OutlinedButton(onClick = { chooseFolder.launch(null) }) { Text("Choose folder") }

        OutlinedButton(
            onClick = { if (controls.onRestoreRequested()) chooseBackupFile.launch(RESTORE_MIME_TYPES) },
            enabled = !state.busy,
        ) { Text("Restore from backup") }

        state.message?.let {
            Text(it.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(BACKUP_MESSAGE_TAG))
        }
    }

    if (state.pendingRestoreUri != null) RestorePrompt(controls)
    when (val prompt = state.prompt) {
        is BackupPrompt.UnreadablePhotos -> UnreadablePhotosPrompt(prompt, controls)
        BackupPrompt.WriteFailed -> WriteFailedPrompt(controls)
        is BackupPrompt.ReplaceExisting -> ReplaceExistingPrompt(controls)
        null -> Unit
    }
}

/** "N photos couldn't be backed up." with Try again, Continue without file(s) and Cancel (owner, "3 A"; copy "5 approve, add a Continue button..."). */
@Composable
private fun UnreadablePhotosPrompt(prompt: BackupPrompt.UnreadablePhotos, controls: BackupControls) {
    AlertDialog(
        onDismissRequest = controls.onPhotosCancel,
        text = { Text(prompt.text) },
        dismissButton = { TextButton(onClick = controls.onPhotosCancel) { Text("Cancel") } },
        confirmButton = {
            Row {
                TextButton(onClick = controls.onPhotosTryAgain) { Text("Try again") }
                TextButton(onClick = controls.onPhotosContinue) { Text("Continue without file(s)") }
            }
        },
    )
}

/** "Replace the existing backup file?" with Replace and Cancel (owner, "3 A"): asked before anything is written into a file that has contents. */
@Composable
private fun ReplaceExistingPrompt(controls: BackupControls) {
    AlertDialog(
        onDismissRequest = controls.onReplaceExistingCancelled,
        text = { Text(BackupPrompt.ReplaceExisting.TEXT) },
        dismissButton = { TextButton(onClick = controls.onReplaceExistingCancelled) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = controls.onReplaceExistingConfirmed) { Text("Replace") } },
    )
}

/** "Couldn't finish the backup. The incomplete file was removed." with Try again and Cancel (owner, "7 A"). */
@Composable
private fun WriteFailedPrompt(controls: BackupControls) {
    AlertDialog(
        onDismissRequest = controls.onWriteFailedCancel,
        text = { Text(BackupPrompt.WriteFailed.TEXT) },
        dismissButton = { TextButton(onClick = controls.onWriteFailedCancel) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = controls.onWriteFailedTryAgain) { Text("Try again") } },
    )
}

@Composable
private fun RestorePrompt(controls: BackupControls) {
    AlertDialog(
        onDismissRequest = controls.onRestoreCancelled,
        title = { Text("Restore this backup?") },
        text = {
            Column(modifier = Modifier.testTag(RESTORE_PROMPT_TAG), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Select Replace if you want to delete the journal data on this device, and move the backup into its place.")
                Text("Select Merge if you want to keep the journal data on this device, and restore the rest of the backup, skipping any duplicates.")
                Text("Select Cancel to go back.")
            }
        },
        dismissButton = { TextButton(onClick = controls.onRestoreCancelled) { Text("Cancel") } },
        confirmButton = {
            Row {
                TextButton(onClick = { controls.onRestoreConfirmed(RestoreMode.REPLACE) }) { Text("Replace") }
                TextButton(onClick = { controls.onRestoreConfirmed(RestoreMode.MERGE) }) { Text("Merge") }
            }
        },
    )
}

/** API 33 and later, and the permission not yet granted. */
private fun needsNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

private val BackupFrequency.label: String
    get() = when (this) {
        BackupFrequency.DAILY -> "Daily"
        BackupFrequency.WEEKLY -> "Weekly"
        BackupFrequency.MONTHLY -> "Monthly"
    }

/** The picked folder's own name: the last part of its tree id (`primary:Backups` is shown as `Backups`), or the URI itself when it has none. */
private fun folderName(uri: String): String {
    val last = Uri.parse(uri).lastPathSegment ?: return uri
    return last.substringAfterLast(':').ifEmpty { last }
}

/** Backups are zips, but some providers report them under a generic type, so the picker offers those too. */
private val RESTORE_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

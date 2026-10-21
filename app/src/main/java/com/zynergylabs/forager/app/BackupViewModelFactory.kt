package com.zynergylabs.forager.app

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel
import com.zynergylabs.forager.app.ui.backup.BackupViewModel
import com.zynergylabs.forager.app.ui.log.CartographyViewModel
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel
import kotlinx.coroutines.joinAll

/**
 * Settings' Backup section (journal backup and restore, dispatches 2026-09-28-127 and -137). After a restore that
 * worked, everything that reads the journal once and holds it is told to read again, and the restore's loading
 * page waits for all of them: there is no Flow anywhere in `data/local`, so nothing else refreshes. The list, with
 * where each read is: the entries and drafts (`CartographyViewModel.reloadAfterRestore`), the finds and drafts
 * (`MushroomLogViewModel.reloadAfterRestore`; both also close an open entry or find whose record the restore removed,
 * dispatch 2026-09-28-182 item 5) and the gallery photos with their reference counts (`MushroomLogViewModel.loadGalleryPhotos`), the tracks
 * and the waypoints with their reference counts (`TrackRecordingViewModel.loadTracks`, `loadWaypoints`), and the
 * planned trips, the offline regions with their reference counts, and the Maps tab's records
 * (`AvailabilityViewModel.reloadAfterRestore`).
 *
 * Moved unchanged out of `MainActivity`'s ViewModel factory (nav step 2, dispatch 2026-10-21-01), the pattern of
 * [createMushroomLogViewModel]. The other ViewModels come as providers, read inside the lambdas exactly where `MainActivity`
 * read its properties, so none is built any earlier than before.
 */
internal fun createBackupViewModel(
    container: AppContainer,
    androidErrorLog: ErrorLog,
    trackRecording: () -> TrackRecordingViewModel,
    cartography: () -> CartographyViewModel,
    mushroomLog: () -> MushroomLogViewModel,
    availability: () -> AvailabilityViewModel,
): BackupViewModel = BackupViewModel(
        backup = container.journalBackup,
        preferences = container.backupSchedulePreferences,
        scheduler = container.backupScheduler,
        files = container.backupFiles,
        errorLog = androidErrorLog,
        isRecording = { trackRecording().uiState.value.isRecording },
        reloadAfterRestore = {
            listOf(
                cartography().reloadAfterRestore(),
                mushroomLog().reloadAfterRestore(),
                mushroomLog().loadGalleryPhotos(),
                trackRecording().loadTracks(),
                trackRecording().loadWaypoints(),
            ).joinAll()
            availability().reloadAfterRestore()
        },
    )

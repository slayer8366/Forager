package com.zynergylabs.forager.app

import android.content.Context
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel

/**
 * The one place the [TrackRecordingViewModel] is built, moved unchanged out of `MainActivity`'s ViewModel factory (nav step 2,
 * dispatch 2026-10-21-01), the pattern of [createMushroomLogViewModel]. [context] is the Activity, read where `MainActivity`
 * read itself: the already-recording message and the phone's 12/24-hour setting.
 */
internal fun createTrackRecordingViewModel(
    container: AppContainer,
    androidErrorLog: ErrorLog,
    context: Context,
): TrackRecordingViewModel = TrackRecordingViewModel(
        container.trackRepository,
        container.startTrackUseCase,
        container.getWaypointsUseCase,
        container.createWaypointUseCase,
        container.deleteWaypointUseCase,
        container.computeReturnToStartUseCase,
        container.locationTracker,
        container.getTracksUseCase,
        container.returnWatch,
        container.alertAudibility,
        androidErrorLog,
        getWaypointReferenceCount = { id -> waypointEntryReferenceCountOrZero(id, container.getEntryReferenceCountUseCase::forWaypoint, androidErrorLog) },
        getTrackReferenceCount = { id -> trackEntryReferenceCountOrZero(id, container.getEntryReferenceCountUseCase::forTrack, androidErrorLog) },
        deleteTrack = container.deleteTrackUseCase,
        getTrackOriginWaypoint = container.getTrackOriginWaypointUseCase,
        alreadyRecordingMessage = context.getString(R.string.track_recording_already_recording),
        abandonedTrackSweepOnce = container.abandonedTrackSweepOnce,
        sundownShown = container.sundownWatch.shown,
        backBy = container.backByWatch,
        shouldPromptBackgroundRun = container.offTrackReminderCheck::atRecordingStart,
        recordingHalts = container.recordingHalts.latest,
        trackEnds = container.trackEnds.latest,
        wayHomeWatch = container.wayHomeWatch,
        // Data part D (RECORD -703): new origin/end waypoint names follow the phone's 12/24-hour setting.
        is24HourClock = { android.text.format.DateFormat.is24HourFormat(context) },
    )

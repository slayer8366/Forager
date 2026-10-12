package com.zynergylabs.forager.app.crash

/**
 * The release build's twin of the debug-only crash capture (dispatch 2026-10-11; the planner's call in
 * RECORD -830: "crash-log capture follows its viewer to debug only, since nothing in a release build
 * can read it (Play Console reports crashes)"). Installs nothing: the platform's own handler stays,
 * and a crash in a release build is reported exactly as it would be without this app's capture.
 */
@Suppress("UNUSED_PARAMETER")
fun installCrashCapture(store: CrashFileStore) = Unit

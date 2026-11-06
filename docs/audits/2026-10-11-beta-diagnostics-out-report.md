# Beta diagnostics out: release loses the Crash Logs row, crash capture and the alert record files

**Dispatch:** the planner's beta-diagnostics-out, on the owner's ruling in RECORD -830 (origin/records-after-173),
verbatim: "The Crash logs row in Settings,The background alert record files". The planner's call, also in -830:
crash-log capture goes to debug only along with its viewer. Follow-up from the planner during the run: release
Settings must not end with a stray divider under Backup.
**Base:** origin/main `0b561481`, as the dispatch said; checked with `git rev-parse origin/main` at the start.
**Branch:** `beta-diagnostics-out`. Commits `f6522161` (tests first), `c1ab617d` (implementation),
`683e6cc9` (divider test first), `ca60c540` (divider fix), plus this report's commit.
**Written:** 2026-10-10T20:51:39Z (from `date -u`).

## What release no longer does

| Thing | Before (release at 0b561481) | After (release) | Debug |
|---|---|---|---|
| Settings "Crash Logs" row, its panel and share | shown | not composed; `CrashLogsEntryRow`/`CrashLogPanel` release twins compose nothing | unchanged |
| Crash capture (`CrashUncaughtExceptionHandler`, `crashes/crash-*.txt`) | installed at start by `ForagerApplication` | not installed; `installCrashCapture` release twin does nothing, so the platform's own handler stays | unchanged |
| `files/return-record.log`, `back-by-record.log`, `sundown-record.log` | written by `FileReturnRecord`/`FileBackByRecord`/`FileSundownRecord` from `AppContainer` | not written; `AlertRecordFiles` release twin returns records that write nothing | unchanged |
| `crashes/` FileProvider path | in main `file_paths.xml` | only in debug's `file_paths.xml` | unchanged |
| Divider under Backup | divider, then the Crash Logs row | Settings ends at Backup; the divider moved into `SettingsDebugRows` (debug: divider, Crash Logs, Diagnostics; release: nothing) | same three items, same order |
| `diagnostics.log` | already not written (writer in `src/debug` before this) | same | unchanged |

The pattern is the Diagnostics panel's: the real code in `app/src/debug`, a twin with the same signatures in
`app/src/release`, and no `BuildConfig.DEBUG` branch. The writers and the viewer are therefore missing from the
release classes, not just never called (see "Release classes" below).

## Before building: every writer and caller (confirmed by `git grep` at 0b561481 unless marked)

- `FileReturnRecord`, `FileSundownRecord`, `FileBackByRecord`: constructed only in `AppContainer.kt:378`, `:419`,
  `:433`; nothing else in main uses them. Confirmed.
- Their interfaces `ReturnRecord`, `BackByRecord`, `SundownRecord` (domain) have only `write`. Nothing in main reads
  the three files: the file-name constants are used only in AppContainer. So no Return, Back by or sundown logic
  reads its own record, and with no-op records those watches behave the same. Confirmed from the interfaces and the
  grep. The write-only reading is also backed by the release tests passing through the real watches.
- Crash capture: `ForagerApplication.installCrashHandler` (`:123`) is the only place `CrashUncaughtExceptionHandler`
  is installed. `CrashFileStore` is built at `AppContainer.kt:217` and by `AvailabilityScreen`'s default parameter
  (`:776`). `list()` is called only in `AvailabilitySettingsUi`'s crash page. Confirmed.
- `diagnostics.log`: `DiagnosticsLog` was already in `src/debug`, and the release `DebugDiagnostics` is a no-op.
  Confirmed. No main code writes it.
- Wrong premise, minor: the dispatch asked for a release-variant unit test "where possible". At 0b561481,
  `testReleaseUnitTest` could not be used at all, for two reasons. (1) The task does not exist under AGP 9's
  default `android.onlyEnableUnitTestForTheTestedBuildType=true`. I passed
  `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false` on the command line and changed no file. (2) `src/test`
  referenced debug-only classes, so a release test compile could not succeed. I moved 23 test files to
  `src/testDebug` with their content unchanged. That is 15 that already depended on debug-only code and 8 whose
  subjects moved to debug here. The planner accepted the move (CI runs `testDebugUnitTest`, ci.yml:163, which still
  runs all 23). The 29 renames are those 23 tests plus the 6 production files moved from main to debug: the three
  record writers, `CrashFileStore`, `CrashUncaughtExceptionHandler` and `CrashLogPanel`.

## Tests (through the real entry points)

Shared helpers in `src/test`:
- `SettingsPageHarness`: the real `AvailabilityScreen`, opened with the taps Tools then Settings.
- `AlertRecordScenario`: the app's own container. It calls `returnWatch.startReturn`, then `backByWatch.onAlarm`
  and `sundownWatch.onAlarm`, which are the calls `WakeUpAlarmReceiver` makes.

| Test | Build type | Failing-first message (R0 = whole implementation reverted to base) |
|---|---|---|
| `AlertRecordFilesReleaseTest`, writes no record file | release | "a release build wrote files/sundown-record.log ([… alarm-delivered nothing-watched]), files/back-by-record.log ([… alarm-delivered nothing-set]), files/return-record.log ([… return-started track=record-scenario-track])" |
| `AlertRecordFilesReleaseTest`, writers absent | release | "release classes that write alert record files expected:<[]> but was:<[…FileReturnRecord, …FileBackByRecord, …FileSundownRecord]>" |
| `AlertRecordFilesReleaseTest`, no diagnostics.log | release | passes on the base too. It only guards an existing state (the writer was already debug-only) and is not evidence for this change |
| `CrashCaptureReleaseTest`, no handler installed | release | "a release build installed the crash-capturing handler. Actual: com.zynergylabs.forager.app.crash.CrashUncaughtExceptionHandler" |
| `CrashCaptureReleaseTest`, handler class absent | release | "present in release: …CrashUncaughtExceptionHandler expected:<false> but was:<true>" |
| `CrashLogsRowReleaseTest` | release | "Settings shows a 'Crash Logs' row in a release build expected:<0> but was:<1>" ("Night Maps" shown is the control that the Settings page did open) |
| `SettingsEndsAtItsLastRowTest` | both | release before the divider fix: "the gap under Settings' last row is 29.0 dp: 12.0 dp is the padding alone, and 24.0 dp or more means something is drawn under the last row". Passes on base, because the Crash Logs row followed the divider there |
| `AlertRecordFilesDebugTest`, `CrashCaptureDebugTest`, `CrashLogsRowDebugTest` | debug | pass on the base and after. They check that debug keeps everything, which is what "unchanged" means. They also act as the control for the release scenario, since the same scenario does write in debug |

The first form of the divider test asserted a gap of exactly 12 dp. In release it measured 29 dp, not the 25 dp I
predicted. The extra 4 dp is the last Backup node, a Material button whose 48 dp touch target surrounds a 40 dp
body. I rewrote the bound as "at least 12 dp and under 24 dp", because anything drawn after the last row adds at
least the column's 12 dp spacing. Debug measures within that bound; its last row is Diagnostics.

## Runs (memory-capped: systemd-run MemoryMax=5G, no daemon, Gradle -Xmx1536m, Kotlin daemon 2g)

Targeted debug set: 15 classes, 65 tests. Targeted release set: 4 classes, 7 tests.

- Forward run at `ca60c540`: debug 65/65 pass. Release 7/7 pass.
- An earlier forward run at `683e6cc9` (before the divider fix) failed only the release divider test, at 29.0 dp.
- R0, the whole implementation reverted to base (main, debug and release source sets restored from `0b561481`):
  debug 65/65 pass. Release has 5 failures, the five messages in the table, and the divider and diagnostics.log
  tests pass, as predicted.
- R1, records reverted (base AppContainer plus main writers, twins removed): only the 2 alert-record release tests
  fail.
- R2, crash capture reverted (base ForagerApplication plus main crash classes, twins removed): only the 2
  crash-capture tests fail.
- R3, row put back in the release twin by one-line edits to the release `CrashLogsEntryRow` and
  `SettingsDebugRows`: only `CrashLogsRowReleaseTest` fails. The divider test passes, because the row is what
  follows Backup.
- R4, divider put back in release `SettingsDebugRows`: only the divider test fails, at 29.0 dp.

Every revert run was checked in the same way. The build log had no compile errors (`e:`) before results were read.
Each failure was one that edit could cause. The tree was restored from a tar saved before the edit, not from git,
and `git status --porcelain` was empty afterwards, so the forward change is present. Not run: the full suite (not
authorized; CI runs it on the PR), the moved tests outside the targeted set (CI covers them), and any phone check.

## Release classes

No assembleRelease: signing.properties exists only in the owner's main checkout, and it was not copied (the planner
agreed). Instead I checked the compiled Kotlin classes (`app/build/tmp/kotlin-classes/{debug,release}`):
- `FileReturnRecord`, `FileBackByRecord`, `FileSundownRecord`, `CrashUncaughtExceptionHandler` and `DiagnosticsLog`
  are present in debug and absent in release.
- `grep -a` for "return-record", "back-by-record", "sundown-record", "No crash reports" and
  "CrashUncaughtExceptionHandler" finds no release class.
- The positive control: the same search finds `FileReturnRecordKt`, `AlertRecordFiles` and `CrashLogPanelKt` in
  debug. A first search without `-a` had found nothing in debug either, so it was not used.

This checks classes, not a dex in an APK. With minification off, the release dex comes from these classes. That
last step is inferred, not checked.

## Not done, and findings for the owner

- Testers' phones keep any record files and crash files that earlier betas wrote: `files/*-record.log` and the
  external `crashes/`. Release no longer writes or shows them, and nothing deletes them; uninstall clears them.
  Deleting them would be new behaviour, so I did not add it.
- The KDoc lines in `BackByWatch`, `SundownWatch`, `BackByRecord` and `SundownRecord` still name the `File*Record`
  classes without saying "debug builds". They are accurate for debug, and I left them unedited.
- Gradle was stopped (`./gradlew --stop`, no daemons left) before this report was written.

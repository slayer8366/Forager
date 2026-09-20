# A debug-only diagnostics instrument for the device check: StrictMode into a file, the sweep's count, photos and captures shared off the phone

**Date:** 2026-09-15 · **Branch:** `claude/new-session-vto65i` (PR #102) · **Commits:** `a4524e9` (code and tests), `88c4cc1` (Application-level test, StrictMode reset rule), plus this report's commit · **Base:** PR #102 head `2275ca5`.

**Dispatch.** The device check for PR #102 runs on a phone with no adb and no logcat, so three of its nine steps had no instrument: StrictMode violations (step 1), the startup sweep's count (step 4), and the EXIF content of a persisted photo (step 5, which needs the file off the device). Build the instrument, not the check; follow `CrashLogPanel`/`CrashFileStore`; debug builds only; read-only over the directories it measures; change no observed behaviour.

**One-paragraph outcome.** A debug-only Diagnostics entry below Crash Logs in Settings lists `filesDir/photos` and `filesDir/captures` (name, size, modified time, count per directory) and shares any listed file, or the new diagnostics log, through the app's `FileProvider`. The log is an append-only file beside `crashes/`, written by a StrictMode `penaltyListener` on a single worker and by the startup sweep. **StrictMode had no policy at all before this**; the checklist assumed one. Everything is debug-only by build-type source set, verified on the release dex, not by a `BuildConfig.DEBUG` branch. The scrub is untouched, so step 6 (bytes after EOI) has no instrument from this dispatch, for a reason given in §2.5. Suite 180/1454 → 185/1476, 0 failures, 24 skipped unchanged; `assembleDebug` exit 0; seven revert checks, each failing on a message specific to its edit.

---

## §1 — Premises checked before building

Every claim below names a file, per CLAUDE.md; each was read, not assumed from the dispatch.

1. **StrictMode is not enabled in this app, in any build.** `grep -rn StrictMode app/src/main` finds two comments and no call: `CrashUncaughtExceptionHandler.kt:7` lists "no StrictMode" among the things the app had none of, and `FilePhotoStore.kt:79` defers the main-thread question to "a StrictMode run" on the device check. There is no `penaltyLog` to change into a listener. On a retail (user) build the platform installs no default thread policy for a debuggable app either (`StrictMode.conditionallyEnableDebugLogging` returns early on user builds), so **step 1 had nothing to observe with or without logcat**. The dispatch anticipated this ("if StrictMode is not actually enabled … say so"); it is said here, and the policy built in §2.2 is new, not modified.
2. **The pattern.** `CrashLogPanel.kt` is a drill-in composable: a header row that is itself the back control, a plain list, a share icon per row that mints a `FileProvider` URI and starts an `ACTION_SEND` chooser (`:199-207`). `CrashFileStore.kt` is pure `java.io.File` plus `CurrentTimeProvider`, tested as a plain JVM class, with `forContext` as the single source of its path (`:76-77`) and `list()` recognising only its own filename shape (`:48-53`). Both call sites of the panel were found, not one: the medium/expanded drawer (`AvailabilityScreen.kt:1078`, `DrawerPanel.CrashLogs`) and `CompactSettingsTab` (`:2446`), which the compact Tools drawer delegates to (`:2726`). `SettingsContent` (`:2515`) draws the entry row for both.
3. **One thing the pattern does that this dispatch's own instrument would then record.** `CrashLogPanel`'s list is produced by `crashFileStore.list()` called during composition (`AvailabilityScreen.kt:1082`, `:2449`), which is a directory read on the main thread. Under the policy installed here, opening Crash Logs will write a `DiskReadViolation` into the diagnostics log. Pre-existing, outside this dispatch's scope, reported rather than changed; the new panel's own reads are on IO for exactly this reason (§2.4).
4. **`photos/` is deliberately not a FileProvider root.** `res/xml/file_paths.xml`'s comment: "`photos/` … never needs a FileProvider URI — this app only ever reads and writes it directly as files, it never grants another app access to it." Sharing a photo out requires a root for it, so the question was where to add one without changing that promise for release. Answered in §2.1.
5. **There is no test of `CrashLogPanel`.** `grep -rl CrashLogPanel app/src/test` is empty. "The existing panel tests" therefore means `AvailabilityScreenSettingsPanelTest`, whose Recorded Tracks share test (`:443-480`) is the model for asserting a share: poll `nextStartedActivity`, unwrap the chooser, check the inner intent's action, type and `EXTRA_STREAM`.
6. **`minSdk` is 26** (`app/build.gradle.kts:244`); `StrictMode.ThreadPolicy.Builder.penaltyListener` is API 28. Gated, with the unsupported case written into the log.
7. **The release build type has `isMinifyEnabled = false`** (`app/build.gradle.kts:284`). This decides the gate: a `BuildConfig.DEBUG` branch would leave the panel in the release APK, unreachable but present.
8. **`ForagerApplication` is instantiated for every Robolectric test** (recorded on `CameraCaptureFilesTest`). Anything `onCreate` now does, every test does: the diagnostics install and its process-start line run 1,476 times per suite. This mattered twice (§4.2, §4.3).

## §2 — What was built

### §2.1 The gate: build-type source sets, verified on the dex

`app/src/debug/java` holds `diagnostics/DiagnosticsLog.kt`, `diagnostics/DebugDiagnostics.kt` and `ui/diagnostics/DiagnosticsPanel.kt`; `app/src/release/java` holds twins of the two types `src/main` calls, `DebugDiagnostics` (two no-op entry points) and `DiagnosticsPanel.kt` (two composables that compose nothing). `ForagerApplication` and `AvailabilityScreen` call them unconditionally and compile in both build types. In release the entry row draws nothing, so `DrawerPanel.Diagnostics` and `CompactSettingsTab`'s `showDiagnostics` are unreachable; more to the point, the panel's code is not in the artifact.

Verified, not asserted: `./gradlew :app:dexBuilderRelease :app:mergeDexRelease :app:dexBuilderDebug`, exit 0, then the project dex archives listed by class:

| Variant | Classes under `…/diagnostics/` |
|---|---|
| release | `DebugDiagnostics`, `DebugDiagnostics$Companion`, `ui/diagnostics/DiagnosticsPanelKt` — the no-op twins, 3 classes |
| debug | the above plus `DiagnosticsLog`, `DiagnosticsLog$Companion`, `DiagnosticsListing`, `ListedFile`, `ComposableSingletons$DiagnosticsPanelKt` and nine lambda classes — 17 |

The merged release dex (`intermediates/dex/release/mergeDexRelease/classes*.dex`) contains zero occurrences of the panel's strings (`Diagnostics (debug build)`, `diagnostics-log-row`, `Share diagnostics log`). **`assembleRelease` was not run**: it refuses without the owner's signing identity by design (`verifyReleaseNeverSignsWithDebugKeystore`), so the claim is about the merged release dex, which is what `packageRelease` wraps, not about a signed APK.

The debug build also gets its own `res/xml/file_paths.xml` (`app/src/debug/res/xml/`), which Android's resource merge substitutes for main's in debug builds only. It repeats main's three roots and adds `photos/` (`files-path`) and `diagnostics/` (under both the external-files and files roots, because `forContext` has the same `filesDir` fallback `CrashFileStore.forContext` has). Release's provider config is main's, untouched; the promise in premise 4 still holds there. Because a whole-file override can silently drop a root main adds later, `DebugFileProviderPathsTest` parses both source files and asserts debug's entries are a superset of main's.

### §2.2 StrictMode: a new policy, its penalty a file

`DebugDiagnostics.install` runs first in `ForagerApplication.onCreate`, before `AppContainer` is built, so what it observes is the whole process from the first line rather than the part after startup. The policy: `detectDiskReads`, `detectDiskWrites`, `detectNetwork`, `detectCustomSlowCalls`; `penaltyLog` kept (one logcat line); `penaltyListener` on the executor below, recording the violation's class name and full stack. **It is installed on the main thread** because `Application.onCreate` runs there, the fact the sweep dispatch was corrected on. A thread policy is per-thread, so an entry means "on main", and work under `withContext(Dispatchers.IO)` cannot appear, which is exactly step 1's question about `persist`. On API 26–27 the listener cannot be installed and the log carries one line saying so; `penaltyLog` still applies.

**Repeats.** The first occurrence of a stack per process is written in full; later identical stacks are one line with a running count. Keyed on the full stack text, per process, so each launch's first occurrence carries its stack again. Without this a framework path that reads disk on main at startup would fill the file with the same trace at every launch.

**The executor.** A `ThreadPoolExecutor(0, 1, 5 s keep-alive)` with a daemon thread: single worker, so entries land in order and the repeat tally needs no lock; core size zero, so the thread exists only while there is something to write. The second property is what keeps 1,476 Application instances per suite (premise 8) from leaving 1,476 idle threads.

**Two limits of the instrument, from the framework, not from this code.** `StrictMode` batches a thread's violations per looper pass and ignores any beyond ten in one pass (`MAX_OFFENSES_PER_LOOP`); a single main-loop message that violates eleven times records ten. And `penaltyLog`'s logcat line is rate-limited to one per second per stack; the listener is not (the repeat test shows two identical violations milliseconds apart both arriving), which is why the tally is done here.

### §2.3 The log: a sibling of `crashes/`, append-only, rotated

`DiagnosticsLog` writes `<external files root>/diagnostics/diagnostics.log`, with the same `filesDir` fallback as `CrashFileStore.forContext`. **Why a sibling and not a file in `crashes/`:** that directory has a contract, one file per crash, pruned to the ten newest on every write, `list()` recognising only `crash-<millis>.txt`; an append-only log that grows across launches is a different lifecycle. It would be tolerated there (`list()` ignores strays) but it would be a stray, and the prune rule would say nothing about it. Its own class, so its own rule is written beside it. **Why the external root:** the same reason `CrashFileStore` chose it, a file manager can reach it with no root and no adb, and this dispatch exists because the owner has neither. The share path (§3) is the primary route regardless, since Android 11+ restricts third-party access to `Android/data`.

**Force stop and relaunch.** Nothing truncates on open; `append` opens for append every time and holds nothing, so a new process continues the file. `DiagnosticsLogTest` has the relaunch analogue, a second instance over the same file appending after the first's entries, and `ForagerApplicationDiagnosticsTest` reads the production file after Robolectric runs the real `onCreate`. The "process started pid=… api=… build=…" line is what separates one launch from the next when reading.

**Rotation.** Past 1 MB the file is renamed to `.1` (replacing any earlier `.1`) and a fresh one started, checked before each append, so at most two generations exist and the newest entries are always in the file the panel shows. Rename over head-truncate because rename is atomic and a truncate would rewrite the whole file on every append past the cap. A failed rename is logged, never silent.

**Format.** One entry per event: ISO-8601 UTC timestamp, a space, the summary; detail lines (a stack) indented four spaces beneath. Three summaries exist: `process started …`, `sweep deleted=N orphaned capture file(s)`, `strictmode <ViolationClass>` (or `… again (xN …)`).

### §2.4 The sweep's count, and the panel

`ForagerApplication.sweepOrphanedCaptures` gains one line after its existing `Log.i`: `diagnostics.recordSweep(deleted)`. Recorded on every launch, **zero included**, because zero is the expected reading after the leak fix and a log that only wrote nonzero counts would leave step 4 reading absence as either "zero" or "not recorded". `CameraCaptureFiles.sweepOrphans` is untouched; the count is the one it already returns.

`DiagnosticsPanel` (debug) follows `CrashLogPanel`'s shape: `DiagnosticsEntryRow` in `SettingsContent` directly below Crash Logs (drawing its own divider, so the release twin leaves no orphaned rule); a header row that is the back control; the log row (size, tap to view, share icon); then `photos/ · N files` and `captures/ · N files`, each row a monospace filename, `<size> · <modified with seconds>`, and a share icon. Listing and log reads happen in `LaunchedEffect` on `Dispatchers.IO`; `FileProvider.getUriForFile` too, since it canonicalises the path, a disk read. The panel would otherwise write its own violations into the log it exists to show. A share the provider refuses (a root not in `file_paths.xml`) is shown on the panel and logged at WARN, never dropped.

**Read-only** is asserted, not promised: `DiagnosticsPanelTest` lists, views, shares two files and navigates back, then checks both directories' names and bytes are exactly what they were.

### §2.5 The scrub: left as it is, and what it would take

`copyScansThroughEoi` (`PhotoMetadataScrub.kt:186-225`) returns `true` at the first EOI and reads nothing further; the bytes it drops are never counted because they are never read. Counting them means draining the input after EOI (cheap: one `read()` returning −1 when there is no trailer, a sequential read of the trailer when there is) **and** carrying the count out through two private functions' `Boolean` returns and `ScrubOutcome.Scrubbed`, then recording it somewhere. The only two places that could record it are the scrub itself and `persist`, and the dispatch names both as not to be changed. The runtime cost is not measurable; the code change is a restructuring of the scrub's return path and its outcome type, with its thirteen tests' `Scrubbed(...)` assertions following. Per the dispatch, said and left. The change is about fifteen lines and can be its own dispatch after the device check; step 6 stays an observation with no instrument here.

## §3 — What you will see on the phone

**Reaching the panel.** On a phone: bottom navigation **Tools** → the drawer opens → **Settings** at the bottom of the drawer → scroll the Settings list to its end → **Diagnostics (debug build)**, the row directly below **Crash Logs**. On a tablet or a wide window: the side drawer's **Settings** → same row. The row is absent in a release build. In the panel, tapping the **← Diagnostics** header row goes back to Settings; the phone's back button does the same.

**The panel.** Top row: **Diagnostics log** with its size; tap the row to read it in monospace; the share icon at its right sends the file. Below, **photos/ · N files** then **captures/ · N files**, each file as its name, a line like `3.1 MB · Sep 15, 1:02:03 PM`, and a share icon. Newest first. Before the first photo, `photos/` reads `No files.`

**Step 1, StrictMode during a burst.** Take five or more photos in a row with the in-app camera, tap Done, wait a few seconds, then open Diagnostics → tap **Diagnostics log** and scroll to the end. What you are looking for is any entry whose summary is `strictmode DiskWriteViolation` or `strictmode DiskReadViolation` with a timestamp inside the burst and `FilePhotoStore` or `persist` in the indented stack beneath it. **The expected reading after the IO switch is none.** Entries from startup, with timestamps at launch and framework or `AppContainer` frames, are separate stacks and are expected; they are the noise §2.2's repeat tally keeps to one stack each. To read it comfortably, tap the share icon on the log row and send it to yourself (Gmail, Drive, Quick Share); it is plain text.

**Step 4, the sweep's count.** Force stop the app (system Settings → Apps → Forager → Force stop), open it again, open Diagnostics → **Diagnostics log**, scroll to the end. The last two lines are `… process started pid=… api=… build=…` and `… sweep deleted=N orphaned capture file(s)`. `N` counts capture files older than the new process. On the first launch of this build over an install that took photos under the old leak, `N` is the number of leaked captures from before the fix; on every launch after that, with photos taken normally, **the expected reading is 0**, and `captures/ · 0 files` on the panel corroborates it (the panel reads the directory fresh each time it opens).

**Step 5, the EXIF content of a persisted photo.** Take one photo with the in-app camera and tap Done. Open Diagnostics; under **photos/** the top row is the newest file. Tap its share icon and send it to yourself by **Gmail (as an attachment), Drive, or Quick Share**; on the desktop open it in an EXIF reader (`exiftool`, or any viewer's properties). **Do not send it through a messaging app that recompresses images** (WhatsApp, SMS/MMS, most chat apps): they rewrite the JPEG and strip or add metadata of their own, and the reading would be about them. Expected: no GPS, no `DateTimeOriginal`, no `Make`/`Model`, no maker notes, no thumbnail; only `Orientation` (when the camera set one; a photo shot upright may have none), JFIF, and an ICC profile if the camera wrote one. The bytes shared are the stored bytes; the panel copies nothing and rewrites nothing.

**Step 6** has no instrument here (§2.5). A trailer's presence on a real capture can still be read from the *original* only, and the original is released after persist; it is not reachable from this panel.

## §4 — Evidence

### §4.1 Suite, build, artifact

| Reading | Value | Scope |
|---|---|---|
| Suite before | 180 suites / 1454 tests / 0 failures / 24 skipped | JUnit XML on disk at `2275ca5`, this container, the run that closed the deadlock dispatch |
| Suite after | 185 / 1476 / 0 / 24 | JUnit XML, `88c4cc1`, this container (Linux, not GitHub Actions); run 4 in §4.3 |
| Skip count | unchanged; CI allowlist untouched | |
| `assembleDebug` | exit 0 | on the `a4524e9` tree; the two commits since change tests only |
| Release compile and dex | `compileReleaseKotlin`, `dexBuilderRelease`, `mergeDexRelease` exit 0 | §2.1 for what the dex contains |

Twenty-two new tests: `DiagnosticsLogTest` 6, `DebugDiagnosticsTest` 4, `DebugFileProviderPathsTest` 2, `DiagnosticsPanelTest` 8, `ForagerApplicationDiagnosticsTest` 1, and one in `AvailabilityScreenSettingsPanelTest` (Settings → Diagnostics → the panel, on the compact route, with back returning to Settings). Suite delta 22 = 1476 − 1454.

### §4.2 A StrictMode violation reaches the log under Robolectric: yes, with a limit

The dispatch asked for this test "if reachable under Robolectric, and a plain statement if it is not". It is reachable, in this form: Robolectric does not hook disk or network calls into `BlockGuard`, so a real `File` read on the test thread raises nothing; but `StrictMode` itself is the real framework class, and `StrictMode.noteSlowCall` is its explicit way to raise a violation under `detectCustomSlowCalls`, which the installed policy includes. `DebugDiagnosticsTest` raises one on the thread under the policy and reads it back from the file with its stack (the violation's message and the test's own frame), through the real listener, executor and file. **What that does not show is that a disk read on a device raises one; that is the platform's behaviour and device-only.**

### §4.3 Four full runs, and a failure that named its own mechanism

| Run | Tree | Result | What it was |
|---|---|---|---|
| 1 | `a4524e9` + the Application test | 1475, **3 failed** | `JournalTabTest:374` photo-pull, the documented flake; and two `DebugDiagnosticsTest` timeouts that had passed with the class alone |
| 2 | + a failure message that prints `StrictMode`'s per-thread state by reflection | 1475, **2 failed** | the same two, now reading `violationsBeingTimed=10 … sameLooper=true`; the flake did not fire |
| 3 | + `StrictModeThreadStateReset` | 1475, 0 failed | but the class ran first this time, so this run never met the polluted state |
| 4 | `88c4cc1`, + the batch-cap test | 1476, 0 failed | the class ran 40th of 185, after `RoomWaypointRepositoryTest` |
| 5 | `0efce43`, **CI** (GitHub Actions, not this container) | 1476, **1 failed** | `DiagnosticsPanelTest`'s log-row test, `AssertionError` at the click on the log row after returning from the detail view: leaving the detail drops the listing state, the list re-reads on IO and shows "Reading…" with no row until it lands, and on the slower runner the click lost that race. A test race, not a panel defect; the test now awaits the row, in the commit after this report's. Not reproduced here (four green local runs); the mechanism is read from the code and the failing line. CI on `a4524e9`, the commit before the reset rule, had shown the §4.3 batch artifact and the flake, 3 failures, as predicted |

Run 2's message is the diagnosis. `StrictMode` batches a thread's violations in a static `ThreadLocal<ArrayList>` until the looper's next pass and ignores every further one at ten (`MAX_OFFENSES_PER_LOOP`, confirmed by `javap` on the Robolectric framework jar). Under Robolectric every test runs on one JVM thread; `ForagerApplication` installs the policy on it in every test (premise 8); the Room tests then read disk on it, ten violations, batched; the flush posted to the looper is dropped with the looper's queue at the test boundary; the list stays at ten for the rest of the JVM, and a later test's violation is "not worth measuring" and reaches no listener. On a device the main looper flushes every pass and the list never stays full: harness-only. The reset rule clears that list (and drops the thread's cached `Handler`, a precaution; the confirmed state had the same looper on both sides). Run 3 passing proved nothing about the rule, and is recorded as such (CLAUDE.md, a check that passes because it never saw the data that could fail it); the batch-cap test in run 4 builds the polluted state on purpose, shows the eleventh violation dropped, applies the clear, and shows delivery restored, so the proof no longer depends on Gradle's class order, which varied between runs. Same family as `FileProviderCacheReset`; the rule's doc records it.

The flake: `JournalTabTest` passed alone immediately after run 1 and in runs 2–4. Not touched.

### §4.4 Seven revert checks, against committed state, with the compile guard

Each ran through the runner that refuses an uncommitted target, deletes stale XML, captures stderr for the compile guard, restores from a saved copy and verifies the restore; `git status` was clean after each and after all seven.

| Revert (one line) | Tests run | Failed | Message |
|---|---|---|---|
| `penaltyListener` not installed | `DebugDiagnosticsTest` | 3 of 4 | `condition not met … violationsBeingTimed=0` on the violation, repeat and batch-cap tests; the process-start/sweep test passes, as it should |
| repeat tally always 1 | same | 1 of 4 | the repeat test, waiting for `again (x2` |
| `diagnostics.recordSweep(deleted)` removed from `ForagerApplication` | `ForagerApplicationDiagnosticsTest` | 1 of 1 | `expected the sweep's count` |
| `EXTRA_STREAM` not put on the share intent | `DiagnosticsPanelTest` | 2 of 8 | NPE on the two share tests' `EXTRA_STREAM!!` |
| `DiagnosticsEntryRow` not drawn in `SettingsContent` | `AvailabilityScreenSettingsPanelTest` | 1 of 23 | `performScrollTo() failed` on the entry label |
| `photos/` root removed from the debug `file_paths.xml` | `DiagnosticsPanelTest`, `DebugFileProviderPathsTest` | 3 of 10 | the paths test's superset/roots assertion; the photo share and read-only tests time out waiting for a chooser the provider refused |
| rotation check removed | `DiagnosticsLogTest` | 2 of 6 | both rotation tests |

### §4.5 Device-only, by construction

- Whether a disk read on the main thread raises a violation on a device (§4.2), and therefore step 1's reading itself.
- Whether the share sheet's targets deliver the stored bytes unchanged (the Gmail/Drive/Quick Share advice in §3 is from those apps' documented behaviour, not measured here).
- Whether `getExternalFilesDir(null)` is non-null on the owner's device; if it is null the `filesDir` fallback applies and the `diagnostics-internal` root covers the share.
- The panel's system-bar insets, as for every layout here (Robolectric reports zero).

### Could not determine

- Whether `mergeDexRelease`'s output is byte-identical to what a signed `assembleRelease` would package. The signing task refuses without the owner's identity; the dex is the input to packaging, so the class-absence claim stands on it.

### Premises that were wrong

- The dispatch's "change the debug thread policy from penaltyLog to a penaltyListener" presupposed a policy; there was none (premise 1). The checklist's step 1 could not have produced a reading on any build before this.

### Decided beyond scope

- Installing StrictMode at all in debug builds is new behaviour of the debug build: penalties are a logcat line and a file write on a worker, nothing user-visible, nothing that changes what the app does. Reported as the one thing here that is not purely additive.
- The two `BackHandler`s in `CompactSettingsTab` (one per submenu flag) rather than an enum: matches what was there.

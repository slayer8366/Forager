# A dev-tools build of every PR: look-only report

Dispatch 2026-10-29-04 (RECORD -1803, -1804). Hazel (coder, Opus), 2026-10-29. Look-only: no workflow, no app
code and no Gradle were touched; no adb. Two trial cherry-picks were run in a throwaway detached worktree under
the session scratchpad, aborted, and the worktree removed; nothing from them was committed or pushed.

## Bases, verified against the remote

- origin/pre-main = `d100a4776` (as the dispatch says). Commit count 4165.
- origin/dev = `82b55f06a` (as the dispatch says). It is **not** current main: its base is main `381515c61`
  (merge-base), 23 commits on top, commit count 3763. origin/main is `177215135` (count 3912).
- The newest copy of the dev tools is on `origin/dev-rebased-177215135` (`7517c146c`), commit `efa52f57d`. That
  branch was never moved onto dev: its CI was red on three out-of-memory map tests and the owner parked it
  (RECORD -1615: "dev stays at 82b55f06a"). The older copy on dev is `7c9cdb0d1`.
- PR #440 (`altitude-sea-level` into pre-main) head is `d1770d5b5` (count 4175), CI run 40858869615 green.
  -1801 recorded the phone build as `g7289aeef` from run 40856123923; the head has moved since, by name only
  checked here, not by content.

## 1. Where the dev tools live today

**One commit**, "Development tools for the dev branch only (RECORD -930)": `7c9cdb0d1` on dev, `efa52f57d` on
dev-rebased-177215135. The other 22 commits on dev are experiments (RAW burst probes, sensor watch, camera reach,
camera engine A, lens chooser; `git log origin/main..origin/dev`), not tools. -930 made it one commit restoring
the tools "exactly as at 921aa9c7"; -939 recorded main losing them (PR #220).

What it adds (`git show --stat 7c9cdb0d1`, 110 files, +7,534/-156):

| Tool | Main files |
| --- | --- |
| Walk logger (passive-provider fixes, raw GNSS, motion and rotation sensors, **pressure**, step sensors) | `app/src/debug/.../diagnostics/WalkLogger.kt`, `diagnostics/walklog/*` (7 files) |
| Diagnostics panel, with the walk-logger and synthetic-forecast switches | `debug/.../ui/diagnostics/DiagnosticsPanel.kt`, `diagnostics/DebugDiagnostics.kt`, `DiagnosticsLog.kt` |
| Crash capture and the Crash Logs panel | `debug/.../crash/*`, `ui/crash/CrashLogPanel.kt` |
| Alert record files (return, sundown, back-by logs) | `debug/.../data/diagnostics/*`; record interfaces in `main/.../domain/ReturnRecord.kt`, `SundownRecord.kt`, `BackByRecord.kt` |
| Synthetic forecast store | `debug/.../forecast/SyntheticForecast.kt` |
| Settings rows "Crash Logs" and "Diagnostics" | `debug/.../ui/availability/SettingsDebugRows.kt` |

By source set (counted from the stat): `debug` 23 files, `release` 9 (no-op twins of each entry point, so the
release variant still compiles), `main` 31, `test` 16, `testDebug` 27, `testRelease` 3, plus `.gitignore`.
`app/src/debug/AndroidManifest.xml` adds four permissions (ACTIVITY_RECOGNITION, READ_BASIC_PHONE_STATE,
ACCESS_NETWORK_STATE, WAKE_LOCK), the first two granted by adb.

**The 31 main-source-set edits are the whole conflict surface.** They are not only wiring:
- wiring: `AppContainer.kt`, `ForagerApplication.kt`, `MainActivity.kt`, the Settings pages, `TrackRecordingService.kt`
  (`WalkLogger.of(this).onRecordingStarted/Stopped`), `CameraXCaptureSession.kt`;
- **inside the alert logic**: `ReturnWatch.kt`, `SundownWatch.kt`, `BackByWatch.kt` gain a record parameter, and
  `ReturnWatch.startReturn` is restructured (the `synchronized` expression body becomes a block that writes a
  record after the lock). These are the off-track, sundown and back-by alert paths.

pre-main has no `app/src/debug` or `app/src/release` (`ls app/src` at `d100a4776`: androidTest, main, test,
testDebug).

## 2. How CI can build "the PR plus the tools"

**Trial, not a build.** `git cherry-pick --no-commit` of each copy onto pre-main and onto #440's head:

| Copy | onto pre-main `d100a4776` | onto #440 `d1770d5b5` |
| --- | --- | --- |
| `efa52f57d` (dev-rebased-177215135) | 4 files conflict | the same 4 |
| `7c9cdb0d1` (dev) | 5 files (+ `TrackRecordingViewModel.kt`) | the same 5 |

The four for `efa52f57d`: `AppContainer.kt` (1 hunk: pre-main's way-home `ReturnWatch(...)` constructor against
the tools' `returnRecord` argument), `ReturnWatch.kt` (2 hunks: `startReturn`, and the off-track verdict where
pre-main added the way-home `alsoOn` trails), `AvailabilityScreen.kt` (2), `AvailabilitySettingsPages.kt` (2).
#440 adds no conflict of its own, though it touches `AppContainer.kt`, `MainActivity.kt`,
`AvailabilityScreen.kt` and `TrackExportPanel.kt`, which the tools also touch. **A clean textual merge is not a
compile**: none of this was compiled (no Gradle), so files that merged cleanly may still break.

Pre-main moves fast: 49 first-parent merges 2026-10-22 to 10-28 (`git log --first-parent --merges`), and 85
commits since 10-15 touched the 31 hooked files.

### Option A: a throwaway tools branch per PR, built by the existing "Run this branch" trigger (no workflow change)

Branch `tools/pr-N` from the PR head, cherry-pick the tools commit, resolve, push, then
`gh workflow run ci.yml --ref tools/pr-N`. `workflow_dispatch` is already on main's ci.yml (`ci.yml:24`, on
origin/main too), and the run uses the ci.yml at that ref (`ci.yml:19-23`). Push runs only on main, pre-main and
dev (`ci.yml:13`), so the branch does not trigger CI by itself. Precedent: the `revert/asl-*` and
`failfirst/asl-a3` dispatch runs of 2026-10-29 used exactly this route.
- Conflict risk: every PR is resolved by hand against its own head; the four hunks above recur until someone
  carries the resolution forward.
- What keeps it in step: nothing automatic. Option B's branch is what would.
- Trace on main: none. No file on pre-main or main changes.

### Option B: a maintained "pre-dev" branch (pre-main + the tools commit only, no experiments)

Rebased onto pre-main after merges; a tools build is the PR merged onto pre-dev, built either by Option A's
trigger (no workflow change) or by a new ci.yml job on `pull_request` (a workflow change, so a new path per
CLAUDE.md: a separate job, not a conditional in `build`).
- Conflict risk: concentrated in pre-dev's rebase, which today means the four hunks once, then whatever later
  merges touch in the 31 files. The PR then only conflicts where it touches tools lines.
- What keeps it in step: a rebase per pre-main merge (8 to 11 a day lately), or lazily before each tools build.
  It is a second dev-shaped branch alongside dev (main + tools + experiments, -1087).
- Trace on main: none with Option A's trigger. A new ci.yml job would reach main at the next promotion and has to
  name pre-dev; -931 allowed `dev` into ci.yml as "one line with no description of the dev tools".

### Option C: a build flavour or source set on pre-main

Tools in a `tools` flavour, with no-op record seams in `main` (as the release twins do today), so every PR
compiles the tools and nothing conflicts.
- **This reverses -930**: "main loses the walk logger, diagnostics, crash capture ... in every build type", and
  the owner's "Absolutely zero trace. None." The tools would be in main's source tree, though not in the release
  APK. Brought to the owner, not recommended by me.

### Release builds never carry the tools: how it is guaranteed today, and the gap

- Under A and B the tools never land on pre-main or main, so the release variant cannot see them.
- Releases run only from main: `release.yml:19-20` (dispatch only), `:55-62` (refuses any ref but
  `refs/heads/main`), behind the `release` environment's branch rule (`:52`, the rule itself not visible from the
  repo).
- A tools build is the debug variant, signed with the committed debug key (`build.gradle.kts:283-305`); the release
  job checks the upload certificate's fingerprints (`release.yml` env `EXPECTED_UPLOAD_CERT_SHA*`).
- **Gap:** nothing on pre-main fails if the tools land there by mistake (a tools branch merged as a PR, say).
  -930 chose a name grep "reported, not committed". A neutral check, that `app/src/debug` and `app/src/release`
  do not exist on pre-main and main, would name no tool; whether even that is acceptable is the owner's call.
  Branch protection against merging a `tools/*` branch: not checked (not visible from the repo).

## 3. Telling the builds apart on the phone

- **Same app.** One `applicationId` (`build.gradle.kts:260`), no `applicationIdSuffix` and no
  `versionNameSuffix` anywhere (`grep`); the tools commit changes no app name or icon (its only `debug` resource
  is `res/xml/file_paths.xml`). **A tools build replaces the plain build and keeps its data**: same debug key, and
  the tools commit touches no database file, so the schema is the PR's own (22 on pre-main, main and dev,
  `ForagerDatabase.kt:206`).
- **The version tells them apart only by number and sha.** `versionName` is `1.0.<commit count>+g<sha>`
  (`build.gradle.kts:74-110`, `:267-268`), shown in the Settings footer (`AvailabilitySettingsUi.kt:77`). A
  cherry-picked tools build of #440 would be 4176 (4175 + one commit; inferred from the rule, not built). The
  visible tell is the Settings page: "Crash Logs" and "Diagnostics" rows appear only in a tools build
  (`SettingsDebugRows.kt`).
- **Going back is a downgrade.** After a tools build (4176), reinstalling plain #440 (4175) or pre-main (4165) is
  refused without `-d`, and with `-d` debug builds wipe the database (`ForagerDatabase.kt:230-239`,
  `fallbackToDestructiveMigration(true)` in debug; the S22 lost its data this way, RECORD -1526). dev (3763) is
  far below pre-main, so dev over any pre-main build is a downgrade too.
- **Side by side** (a `.tools` suffix) is possible but costs: a second app with empty data (no tracks, no offline
  areas such as the S22's Region 1), its own permissions, and a changed user agent (`AppUserAgent.kt:26` sends
  the application id). Whether anything server-side keys on the package name: not checked. It also needs a
  `build.gradle.kts` edit, in the tools commit (one more conflict file) or on pre-main (a trace).
- **The S26.** The rule stands: read the installed build's schema before any install. The S26 runs 4153
  (-1803); its schema was not read here. Pre-main and #440 are 22.

## 4. Cost

From run 40858869615 (#440, 2026-10-29): wall 17 min 50 s. Jobs: build-and-test 17:45 (debug build 3:11, unit
tests 13:23), lint 7:08, release compile 3:26, Tile Worker 0:30, route cutter 0:28: about 29 runner minutes.
The repository is public, and the run's timing API reports 0 billable ms, so minutes cost queue time, not money.

A tools build through Option A's trigger runs the whole ci.yml (all five jobs). The `tests` input
(`ci.yml:26-32`) can narrow the unit tests to a few classes; lint and release compile still run. A tools-only
job (build only, about 4 min) would need a workflow change. dev's full suite ran out of memory on three map
tests (-1615), so a full test run on a tools tree may be red for reasons unrelated to the PR.

Every push vs on request: a phone check is done on one chosen head, and each push means re-resolving the
conflicts (Option A) or re-merging (B). On request, when a phone check is planned, costs one build per check.

## 5. First use: PR #440 today

1. Branch `tools/pr-440` from `d1770d5b5`; cherry-pick `efa52f57d`; resolve the four files above. The
   `ReturnWatch.kt` hunks are inside the off-track alert path: the resolution must keep pre-main's way-home
   trails and add only the record writes. Separately, which copy of the tools commit is the source needs settling:
   dev's `7c9cdb0d1` (5 conflicts) or the parked `efa52f57d` (4).
2. Push it and run `gh workflow run ci.yml --ref tools/pr-440`: about 18 min.
3. On the S22 (has #440, 4175): 4176 installs in place, keeping Region 1. Grant ACTIVITY_RECOGNITION and
   READ_BASIC_PHONE_STATE by adb; turn the walk logger on in Diagnostics.
4. On the S26 (4153): read its schema first.

What the log would carry, read from the code, not run: fixes with their altitude, raw GNSS, pressure at the
platform rate (`AndroidWalkLogPlatform.kt` class doc). It carries **not** #440's computed sea-level height (no
sea-level or MSL code in the tools' debug files, by grep), so the eye-level-to-ground question would be read from
the raw fixes and the pressure stream, or from #440's own export.

## Confirmed / inferred / not determined

- Confirmed (git, files, gh): the bases above; the tools commit and its file list; the conflict files and hunk
  counts; ci.yml, release.yml and build.gradle.kts lines cited; commit counts; CI timings.
- Inferred: the 4176 version code; that the tools build keeps the PR's schema; that a tools build compiles after
  resolution.
- Not determined: whether a tools build of #440 compiles or passes tests; the S26's schema; branch protection and
  the release environment's rule; any server keying on package name.

## Premises that were wrong or need correcting

- "dev is main plus experiments" (-1087) is not the current state: dev is on main `381515c6`, not `177215135`, and
  the rebased copy is parked (-1615).
- -1087 says small tasks on pre-main reach a phone "through the plain pre-main build ... not through dev". -1803
  adds a third build beside that; it does not move dev's base. Noted, not ruled on.

## Owner's questions, in plain behaviour

1. **May the tools touch the code that becomes the store app?** (a) No, never: each tools build is a throwaway copy
   made for one PR, and nothing changes on pre-main or main (recommended; keeps "zero trace"). (b) Yes, as a
   built-in variant: no conflicts ever, but the tools' code would sit in the public app's code, which reverses your
   "Absolutely zero trace".
2. **One app or two on the phone?** (a) The tools build replaces the test build and keeps your tracks and offline
   areas; going back to a plain build later means losing the data or waiting for a newer plain build
   (recommended). (b) A second "tools" app beside the normal one: starts empty, needs its own offline areas.
3. **Which tools?** (a) All of them, as on dev (logger, Diagnostics, Crash Logs, alert logs, test forecast). (b)
   Only the walk logger, Diagnostics and Crash Logs: fewer clashes, because the alert logs are threaded through
   the off-track, sundown and Back by alert code. #440 needs only the logger.
4. **When is a tools build made?** (a) Only when a phone check is planned for that PR (recommended: about 18
   minutes each). (b) Automatically on every PR change.
5. **A safety check on pre-main** that fails if tools files ever appear there, naming no tool (just "no debug
   folder"): yes or no?

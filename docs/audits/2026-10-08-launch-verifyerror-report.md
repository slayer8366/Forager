# launch-verifyerror: main crashed on launch (ART VerifyError in CompactMapTab)

RECORD -739 (dispatch `prompts/preserved/2026-10-08-04.md`), -740, -741, -743, -744. Branch `launch-verifyerror`.
Written 2026-10-08 by the coder that took the branch over at 516180f6 after the first coder stopped (-744). The
diagnosis and the fix commits (4907ad0a, 63daddf9, 36e4d071, 516180f6) are the first coder's; this report
records them from their commit messages, the RECORD entries and the evidence files they left, and states what
this session re-ran itself. Evidence (logcat, screenshots, dumps, build logs) is in
`~/Zynergy/device-evidence/2026-10-08-verifyerror/`, outside git; file names below are relative to it.

## What happened (first coder's work, read from the record and evidence, not re-run here)

- Main at 0e766d94 (PR #203 merged) crashed at start on the S22: `01-main-0e766d94-crash.txt`, 11:47:47,
  `java.lang.VerifyError: Verifier rejected class com.zynergylabs.forager.app.ui.availability.AvailabilityCompactMapUiKt:
  void ...CompactMapTab-ztWZzuM(...)`. CI's APK for f8739856 (before #203) launched with an empty crash buffer
  (`02-pre203-f8739856-crash.txt`, 0 bytes). Main was reverted by PR #204 (-742).
- The first fix (4907ad0a: CompactMapTab's 67 parameters grouped to 37 in `CompactMapTabInputs.kt`, 249 registers)
  still crashed, with a different message (`05-fix-attempt1-crash.txt`, 11:53:25, `CompactMapTab-v2tPam0`;
  "[0x1E20] copy-reference v12<-v197 type=BooleanConstant" as -741 records it). So the 256-register explanation of
  -740 was incomplete: D8 9.3.16, bundled with AGP 9.3.1, miscompiled this one very large method (-741).
- 63daddf9 pins R8 9.3.31 on the buildscript classpath (`build.gradle.kts:1-15`); that build verified and launched
  on the S22 (-743, `06-r8-9331-*`). 36e4d071 splits CompactMapTab's body into private composables (255 to 173
  registers). 516180f6 merges main (with #204's revert) back in, keeping #203's join.

## Done in this session

| Commit | What |
|---|---|
| dc4b6c2e | `scripts/s22-launch-check.sh` (new) and `scripts/verify-dex-register-budget.sh` made report-only, both left uncommitted by the first coder; reviewed and finished here |
| 6ccb7b9e | the register report's baseline, recorded from the S22-verified build |
| 622ef316 | this report's draft, pushed before the suite finished |
| (next) | this report finished, and its index row |

Changes made to the first coder's uncommitted script: two pipelines that ended in `grep -q` under
`set -o pipefail` (the attached-device check and the dexopt status check) now capture output before searching
it, since `grep -q` closing the pipe early can fail the producer with SIGPIPE and turn a match into a false FAIL;
the dexopt status found is printed. Otherwise as written, which matches -741's ruling: `adb -s R5CT321008R
install -r`, `cmd package compile -m verify -f com.zynergylabs.forager.app`, a cold launch, then FAIL on a
non-empty crash buffer, a VerifyError or "Verifier rejected class" line for the app in logcat, or no process after
8 s; exit 1 on any. Not wired into CI. The register script always exits 0 once it has read the APK (report only,
-741); its baseline lists the nine app methods over 256 registers at dc4b6c2e.

## 1. Did the merge keep #203's join? Yes, by file comparison and by construct

- `git diff --stat 0e766d94 516180f6` touches only `AvailabilityCompactMapUi.kt`, `AvailabilityCompactScaffold.kt`,
  `CompactMapTabInputs.kt` (new), `build.gradle.kts` and the two scripts files. #203 (`git diff f8739856 0e766d94`)
  touched 12 files; the other ten are therefore byte-identical to #203's merge, among them
  `AvailabilityMapControlsUi.kt`, `AvailabilitySearchUi.kt`, `LandscapeChromeWidth.kt`, `AvailabilityMapIconCluster.kt`,
  `LandscapeBarStripJoinTest.kt`, `LandscapeStripPortraitHeightTest.kt` (the -736 strip-height tests),
  `LandscapeHeadingIconFitTest.kt`, `AvailabilityScreenLandscapeB2Test.kt`, `docs/audits/README.md`'s rows and
  `docs/ui/2026-10-08-landscape-bar-strip-report.md` (present).
- For the two refactored files, every construct #203's diff adds is present at 516180f6: in
  `AvailabilityCompactMapUi.kt`, `landscapeStripFit(...)` with `rememberLandscapeStripNeed` and
  `rememberLandscapeBarFloorPx` (:1286-1291), the strip's `fillMaxWidth().onSizeChanged` writing both measured sizes
  (:1324), `contentWidth = false` and `shortHeadingStatus`, the reset on dispose and on navigating (:1344, :1348),
  the two `LaunchedEffect`s reporting height and width (:1351-1352), and the 1 dp line with
  `LANDSCAPE_BAR_STRIP_LINE_TAG` (:1357-1370); in `AvailabilityCompactScaffold.kt`, `barMeetsStrip` and
  `mapSearchBarHeight` (:437-440) used at all five places #203 changed (:461, :1207, :1315, :1519, :1539), the
  rest-of-the-room width (:1048) and the padding branch (:1276). The scaffold's diff against 0e766d94 is only the
  regrouping of arguments into `CompactMapLandscape`, `CompactMapRecording`, `CompactMapNavigation` and
  `CompactMapSearchLocationPick`. `besideLandscapeSearchBar` keeps #203's count (one use, the navigation display).
- Whether the split itself changes behaviour was not reviewed line by line here; the full suite, which includes
  #203's 22 join tests and 10 strip-height tests, is the check on that (below).

## 2. Build

`assembleDebug` at dc4b6c2e: BUILD SUCCESSFUL, no `e:` lines (`10-assembleDebug-dc4b6c2e.log`). Every dex marker in
the APK reads `"version":"9.3.31"` (16 markers across 20 dex files). CompactMapTab: 173 registers.

## 3. S22 device check (dc4b6c2e, versionName 1.0.3093+gdc4b6c2e)

`scripts/s22-launch-check.sh` (`11-s22-launch-check-dc4b6c2e.txt`), exit 0:

```
Install: Success
Installed: versionName=1.0.3093+gdc4b6c2e
compile -m verify -f: Success
dexopt: status=verify
Status: ok
LaunchState: COLD
PASS: verified (status=verify), launched, process 23519 alive after 8 s, crash buffer empty
```

From `11-logcat-all-dc4b6c2e.txt`: `12:43:49.744 ... ActivityTaskManager: Displayed com.zynergylabs.forager.app/.MainActivity
for user 0: +810ms`. Crash buffer 0 bytes (`11-crash-dc4b6c2e.txt`).

Then on the phone, same process (pid 23519 throughout):

| Step | Result | Evidence |
|---|---|---|
| Maps tab, portrait | Map, bar ("October · Search a location"), strip ("279° W · Alt 381 ft · 10T ER 28689 19261"), icon cluster, bottom nav. Strip [0,271][1440,406]: 135 px = 36 dp at density 3.75, -736's portrait figure | `12-portrait-launch.png/.xml` |
| Journal tab and back to Maps | Journal shows Records/Entries; back on Maps the strip and bar are there again | `13-journal-tab.*`, `14-maps-after-switch.*` |
| Landscape, ROTATION_90 (`user_rotation 1`) | Bar [100..1287], strip [1287..2608], both y 113 to 248 (135 px, the portrait height). Line visible at the meeting edge. Heading "266° W" whole, then "381 ft" and the coordinates whole | `15-landscape-rot1.png/.xml`, `15b-rot1-join-crop.png` |
| Landscape, ROTATION_270 (`user_rotation 3`) | Mirrored: strip [480..1801], bar's node [1824..2988], both 135 px. Heading whole. The line sits at the strip's end, and the bar's fill and divider run to it in the pixels; see the note below | `16-landscape-rot3.png/.xml`, `16b-rot3-join-crop.png` |
| Back to portrait | Restored; crash buffer 0 bytes, no VerifyError or "Verifier rejected" line in the whole logcat | `17-portrait-restored.png`, `18-*` |

Note on ROTATION_270: the bar's accessibility node starts 23 px (about 6 dp) after the strip ends (1824 against 1801),
where at ROTATION_90 the two nodes meet exactly (1287). The screenshot crop shows no gap: the bar's divider starts at
the line, so the 23 px is inside the bar's own surface, outside the node uiautomator reports. Not investigated further;
noted because the two rotations differ, and real insets are what Robolectric cannot show (CLAUDE.md).

Observation, not a gate: the landscape strip shows no "Alt" label, portrait does. That is the strip's fit rule, labels
dropping first when the row runs short (RECORD -713, `AvailabilityMapControlsUi.kt:680-705`), unchanged by this branch.

Phone settings: rotation is locked (`accelerometer_rotation 0`); to reach both landscapes `user_rotation` was set
to 1, then 3, then back to 0. Read at the end: `accelerometer_rotation 0`, `user_rotation 0`, `font_scale 1.0`, as at
the start. Nothing else was changed; no uninstall or clear-data; the S26 was not touched.

## 4. Revert check: bundled D8 without the pin. It did not fail

With the buildscript block removed from `build.gradle.kts` (copy saved first), one build
(`20-revert-r8-assembleDebug.log`, BUILD SUCCESSFUL, no `e:` lines; APK newer than the forward one, 12:49:51). Every
dex marker read `"version":"9.3.16"`, so the reverted build is the one that ran. CompactMapTab: 174 registers.
`s22-launch-check.sh` on it (`21-revert-r8-s22-launch-check.txt`): **PASS**, `compile -m verify -f: Success`,
`status=verify`, process alive, crash buffer 0 bytes, no verifier line in logcat; `Displayed ... +809ms`.

What this means: with CompactMapTab split, bundled D8 9.3.16 also produces code ART accepts, so on this build the
split alone fixes the crash and the R8 pin is not what makes it launch. The pin did make the unsplit, grouped build
launch (-743). Whether to keep the pin (insurance against the same miscompile elsewhere, at the cost of running a
non-bundled R8) or drop it is a decision for the owner; nothing was changed here. Restored from the saved copy, not
from git; `git status` clean and the `classpath("com.android.tools:r8:9.3.31")` line present afterwards.

What it also means: `s22-launch-check.sh` has still not been seen failing on a crashing build. The first coder's
negative control against 0e766d94 (`08-guard-negative-control-main-0e766d94.txt`) stopped at "install -r did not
succeed", because that build's version code is lower than the installed one, so it never reached the launch. With
`install -r` only, an older crashing build cannot be installed over a newer one; the script's failing path is shown
only for install failure. Its crash detection is the same `logcat -b crash` read used for the crash captures in
`01-` and `05-`, which did see the VerifyError, but the script's own path has not been run against one.

## 5. Full suite

`testDebugUnitTest` at 6ccb7b9e, read from the JUnit XML of a run whose results directory was emptied first:
**576 classes, 4,454 tests, 0 failures, 0 errors, 24 skipped** (`31-suite-6ccb7b9e-tests-only.log`, BUILD SUCCESSFUL in
7m 48s). The same total as #203's suite at 8904d8ff (4,454, 24 skipped, 0 failures, its index row), which is what a
refactor with no new tests should give. Among them, #203's join and strip tests all pass: LandscapeBarStripJoin780Test
and 823Test 13 + 13, the six StripHeight classes (-736) 10, LandscapeHeadingIconFit Font1 and Font2 1 + 1. No existing
test broke, so none was touched.

The first attempt (`30-suite-6ccb7b9e.log`, `assembleDebug testDebugUnitTest` in one scope) was killed by the
5G cap: journalctl, "run-p1219344-i1219216.scope: The kernel OOM killer killed some processes in this unit" and
"Failed with result 'oom-kill'", 5G peak, during `testDebugUnitTest`, after `assembleDebug` had finished. The Kotlin
daemon (2g) from compiling stays up beside Gradle (1.5g) and the Robolectric worker. Running the tests in a scope of
their own passed under the same cap, though that run recompiled main and test Kotlin too (the version stamp changed with
the report's commit), so the split is not what made it fit; the earlier run's leftover Kotlin daemon is the likelier
difference, not confirmed. The forward APK that run built (12:53:13, D8 9.3.31, 1.0.3094+g6ccb7b9e) was installed and
checked after the revert check, so the phone is back on the pinned build: `22-forward-6ccb7b9e-s22-launch-check.txt`,
PASS, `compile -m verify -f: Success`, `status=verify`, crash buffer 0 bytes, `12:56:42.787 ... Displayed
com.zynergylabs.forager.app/.MainActivity for user 0: +807ms`. Gradle stopped at the end (`./gradlew --stop`: "No
Gradle daemons are running.").

## Not done, or left for others

- No PR (the dispatch); the planner opens it.
- `docs/ui/2026-10-08-landscape-bar-strip-report.md`'s device steps (123 onward) were not run as a whole; only the
  join, the strip height and the heading above.
- No check at font scale 2.0 or with decimal degrees: both need a phone setting or an app preference changed.
- The s22 script's crash path, above.

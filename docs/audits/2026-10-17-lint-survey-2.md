# Android lint survey 2 on CI: counts only, nothing fixed (dispatch 2026-10-17-03, Phase 1, RECORD -1192)

Hazel Cairnwright. Production release T1 again, at pre-main, so the owner can choose what gets fixed under D4 ("Everything reasonable", -926). **Nothing was fixed.** No baseline was added, and no check was disabled or downgraded. Lint ran only on CI. No Gradle ran on the laptop, and no phone was used. Phase 2 waits for the owner's go.

## Facts

- **Base:** origin/pre-main `b221e85b` (PR #244 merged). PR #245 (main into pre-main) was still **open** when I started. `git merge-tree --write-tree origin/pre-main origin/main` gives `2cc55a92`, which is pre-main's own tree, so the merge changes no file (checked, as the dispatch said).
- **Scratch branch:** `scratch-lint-survey-2`, cut from `b221e85b`.
  - `aec256d7`: the honest run's commit. It adds a separate `lint` job at the end of `.github/workflows/ci.yml` (additions only, after the unchanged `build` job) and the October survey's output-only `lint {}` block to `app/build.gradle.kts`.
  - `e1b38ac2`: the opt-in positive control (one line removed; see "The camera opt-in").
  - `98d2d9f5`: restores that file from a copy saved before the edit. It now equals pre-main's (`git diff origin/pre-main` on it is empty).
  - The branch is not for merging. Its net diff from pre-main is ci.yml (+72) and app/build.gradle.kts (+17).
- **Two changes from October's job, both mine:**
  - The job is a two-entry matrix (debug, release). Each variant has its own job, result and artifacts, so a release failure could not hide the debug count.
  - The text report is renamed `lint-results-text.txt`, because the file no longer names one variant. AGP also wrote `lint-results-<variant>.txt` itself, and the two are byte-identical (`cmp`).
- **Counter and comparison:** this directory's `count_lint_issues.py` is copied unchanged from October. `compare_lint_runs.py` is new. Both scripts, both positive controls and the mechanism prediction were committed in `78bff807` on `lint-survey-2`, before the run was started (12:14:21 UTC commit, run created 12:14:43 UTC).
- **CI run:** [39031757730](https://github.com/slayer8366/Forager/actions/runs/39031757730), `gh workflow run ci.yml --ref scratch-lint-survey-2`, `workflow_dispatch`, headSha `aec256d7`. Conclusion **success**.
  - lint debug job 117393455837: success, 12:14:46 to 12:19:03 UTC.
  - lint release job 117393455765: success, 12:14:46 to 12:19:18 UTC.
  - The `build` job (117393455668) also ran, unchanged, and passed. It is not part of this survey.
- **Lint version:** `lint 9.3.1` (XML root `<issues format="6" by="lint 9.3.1">`), the same as October, with AGP 9.3.1 (`gradle/libs.versions.toml:2`).
- **Artifacts:** `lint-reports-debug`, `lint-reports-release`, `lint-model-debug` and `lint-model-release`. They hold HTML, XML, SARIF, text and the Gradle log, and expire **2027-01-15T12:14:44Z**. The HTML reports are left there. The XML and SARIF for both variants are committed here. Their SHA-256 matches the downloaded artifacts:
  - debug XML `aa80ac4d…`;
  - release XML `bd868211…`;
  - debug SARIF `3e287e9c…`;
  - release SARIF `509e62c5…`.

### What the run covered

- **Debug:** `:app`, variant debug. The Gradle log shows `lintAnalyzeDebug`, `lintAnalyzeDebugUnitTest` and `lintAnalyzeDebugAndroidTest`. So main code, unit tests and instrumented tests were all analysed, tests with only the checks written for tests (`checkTestSources` is at its default, as in October).
- **Release:** variant release. The log shows `lintAnalyzeRelease` only, with **no test-source analysis task**. That is why release has 12 fewer findings, all of them in `app/src/test` (see "Release against debug").
- **No signing secret was needed for release lint.** No secret was set in the job, and `lintRelease` ran to the end. The signing guard `verifyReleaseNeverSignsWithDebugKeystore` hangs only off `assembleRelease` and `bundleRelease` (`app/build.gradle.kts` near the end of the file, `tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }`), and lint neither packages nor signs.
- **There are no release-only sources on pre-main.** `app/src` holds `androidTest`, `main`, `test` and `testDebug`, and no `src/release`. So October's "release-only sources" gap is empty in practice. Release lint saw the same main code as debug and found nothing debug did not find.
- **`app/src/debug` no longer exists** (it left main in PR #220, -939). Everything October found there is gone by removal, not by fixing.
- **Lint-internal errors:** none. No Lint-category issue is in either XML, and the Gradle logs have no exception lines. The logs carry only AGP and Kotlin deprecation warnings, including "Lint reports are now always generated" for the report switches (harmless, as in October).

## Counts

### Three-way cross-check of the total

| Source | Debug | Release |
|---|---|---|
| `count_lint_issues.py` on the XML | **349** | **337** |
| Plain `<issue>` element count (`grep -oE '<issue([[:space:]]\|>\|$)' … \| wc -l`) | **349** | **337** |
| Lint's own summary (text report's last line; HTML header) | "14 errors, 326 warnings, 9 hints" = **349** | "7 errors, 321 warnings, 9 hints" = **337** |

All three agree for each variant. The debug XML has 350 locations, because `ClickableViewAccessibility` carries two on one issue, as in October.

Both positive controls were re-run on this machine before the CI run:
- `run_control.sh`: three of three checks pass, unchanged from October.
- `run_compare_control.sh`: checks 1 and 2 pass. The second check is a one-id mutation, and it is detected.

### By severity, with October

| Severity | Oct (debug, `921aa9c7`) | Now debug | Now release |
|---|---|---|---|
| Error | 29 | 14 | 7 |
| Warning | 338 | 326 | 321 |
| Hint | 9 | 9 | 9 |
| **Total** | **376** | **349** | **337** |

### By issue id, debug, against October

From `compare-october-vs-debug.txt`. The "Sites" column compares (id, file) with line numbers ignored, because lines moved.

| Id | Severity | Oct | Now | Status | Note |
|---|---|---|---|---|---|
| LogNotTimber | Warning | 272 | 259 | still | The 41 in `src/debug` are gone with the folder. 28 new in main: 21 in `photo/CameraXCaptureSession.kt`, the rest in camera, compass, navigation and credits files |
| UseKtx | Warning | 13 | 15 | still | +1 `CameraXCapabilityReader.kt:129`, +1 `OpenMeteoCredit.kt:59` |
| ModifierParameter | Warning | 14 | 14 | still | |
| AutoboxingStateCreation | Hint | 9 | 9 | still | |
| NewerVersionAvailable | Warning | 8 | 8 | still | Depends on what is published online that day (see T3) |
| GradleDependency | Warning | 7 | 7 | still | Same as above |
| UnrememberedMutableState | Error | 4 | 7 | still | +3 new test files; all in `app/src/test` |
| ComposableNaming | Warning | 5 | 5 | still | All in tests |
| LocalContextResourcesRead | Warning | 5 | 5 | still | All `ui/map/SightingsMap.kt` |
| NonObservableLocale | Error | 5 | 5 | still | Same five files as October |
| ConfigurationScreenWidthHeight | Warning | 4 | 4 | still | |
| AndroidGradlePluginVersion | Warning | 2 | 2 | still | |
| ClickableViewAccessibility | Warning | 2 | 2 | still | |
| InlinedApi | Warning | 2 | 1 | still | The `src/debug` one is gone |
| DataExtractionRules | Warning | 1 | 1 | still | |
| ObsoleteSdkInt | Warning | 1 | 1 | still | |
| Range | Error | 1 | 1 | still | |
| SuspiciousIndentation | Error | 1 | 1 | still | |
| UsableSpace | Warning | 1 | 1 | still (moved) | October's was in debug-only `WalkLogger.kt`. Now **`photo/RawShotSaver.kt:117`**, the RAW free-space check: new code, same id |
| NewApi | Error | 18 | 0 | **gone** | All were in `src/debug` |
| StaticFieldLeak | Warning | 1 | 0 | **gone** | Was in `src/debug` |
| MonochromeLauncherIcon | Warning | 0 | 1 | **new** | `res/mipmap-anydpi-v26/ic_launcher.xml:2` |

**Summary of the change:** 62 issues gone (all 62 were in `src/debug`), 35 new, and 314 still at the same (id, file). Net 376 to 349.

### Every error, with file and line (debug run; paths under `app/src/`)

| Id | Where | Ships? |
|---|---|---|
| NonObservableLocale | `main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityResultsUi.kt:189` | yes |
| NonObservableLocale | `main/java/…/ui/availability/AvailabilitySearchUi.kt:934` | yes |
| NonObservableLocale | `main/java/…/ui/availability/AvailabilitySearchUi.kt:1133` | yes |
| NonObservableLocale | `main/java/…/ui/availability/AvailabilitySearchUi.kt:1163` | yes |
| NonObservableLocale | `main/java/…/ui/log/PhotoViewerDialog.kt:378` | yes |
| Range | `main/java/…/ui/map/NavigationView.kt:539`: "Value must be ≤ 25.5 (was 1.7976931348623157E308)" | yes |
| SuspiciousIndentation | `main/java/…/ui/availability/AvailabilityMapControlsUi.kt:726` | yes |
| UnrememberedMutableState | `test/java/…/ui/map/JournalEntriesChipTest.kt:100` | no (test) |
| UnrememberedMutableState | `test/java/…/ui/availability/NavigationCardArrowsSideBySideTest.kt:110` | no (test) |
| UnrememberedMutableState | `test/java/…/ui/availability/NavigationCardFitAcrossWidthsTest.kt:162` | no (test) |
| UnrememberedMutableState | `test/java/…/ui/availability/NavigationHudButtonPlacementTest.kt:83` | no (test) |
| UnrememberedMutableState | `test/java/…/ui/availability/NavigationHudKindInStatusTest.kt:164` | no (test) |
| UnrememberedMutableState | `test/java/…/ui/availability/NavigationHudQuickSettingsWidthTest.kt:159` | no (test) |
| UnrememberedMutableState | `test/java/…/ui/availability/NavigationInfoLayoutTest.kt:76` | no (test) |

That is 7 errors in shipped code, the same seven as October by id and file, and 7 in tests. The release run's 7 errors are exactly the 7 shipped-code ones.

**Corrections to the tidying inventory (section 2), from lint's own output:**
- NonObservableLocale's five sites are `AvailabilityResultsUi.kt:189`, `AvailabilitySearchUi.kt:934, 1133, 1163` and **`PhotoViewerDialog.kt:378`**. The inventory listed `AvailabilitySearchUi.kt:684` instead of the dialog. Line 684 is `Locale.getDefault()` inside `activeSearchSummary`, a plain function, not a composable, so lint does not flag it.
- The second indentation pattern at `AvailabilityMapControlsUi.kt:848` (the inventory marked it UNVERIFIED) is **not** flagged. Only `:726` is.
- The inventory's "about 403" `Log.*` calls in `src/main` come from a pattern without a word boundary. It also matches the app's own loggers, whose names end in `Log`. A word-bounded `\bLog\.(v|d|i|w|e|wtf)\(` gives 258, and lint's LogNotTimber gives 259.

### Release against debug

`compare-debug-vs-release.txt`: 337 issues at the same (id, file) sites. **None is new in release.** The 12 gone are all of debug's test findings: UnrememberedMutableState ×7 and ComposableNaming ×5. The cause is that release ran no test-source analysis (task list above). It is not a code difference.

## The camera opt-in (`ExperimentalCamera2Interop`)

- **The honest run reports nothing** about the opt-in. No `UnsafeOptInUsageError` or other opt-in id appears in either XML. The RAW code carries `@OptIn(markerClass = [ExperimentalCamera2Interop::class])` at `photo/CameraXRawCapture.kt:62, :143` and `photo/CameraXCapabilityReader.kt:83, :88`.
- **Lint does enforce it (positive control).** On commit `e1b38ac2` I removed the one opt-in line at `CameraXRawCapture.kt:62` and re-ran the same job. The control was [run 39032713545](https://github.com/slayer8366/Forager/actions/runs/39032713545): lint jobs 117396271673 (debug) and 117396271763 (release), both success.
  - The compile ran (`compileDebugKotlin` in the log) with no error lines, and the log ends `BUILD SUCCESSFUL`. So the Kotlin compiler does not enforce this marker, and lint is the only guard.
  - Lint reported exactly **3 new issues in each variant**, all `UnsafeOptInUsageError`, severity **Error**, at `CameraXRawCapture.kt:67, 68, 69`. Those are the original lines 68 to 70: `Camera2Interop.Extender(builder)`, `.setSessionCaptureCallback`, `.setCaptureRequestOption`. Lint's message: "This declaration is opt-in and its usage should be marked with `@androidx.camera.camera2.interop.ExperimentalCamera2Interop` or `@OptIn(markerClass = androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)`".
  - Nothing else changed: 349 to 352 in debug, 337 to 340 in release, no site gone (`compare-optin-control-debug-run-39032713545.txt`).
  - The findings are specific to that edit: they are in the edited function and nowhere else.
  - The file was then restored from a copy saved before the edit (`98d2d9f5`), and it equals pre-main's.
- **So:** the RAW code's opt-ins are present and satisfy lint today. Lint is the only thing that would catch a missing one. That is a reason to put lint on CI (T3).

## Mechanism prediction against the result

The prediction is `mechanism-prediction.md`, committed before the run.

| Prediction | Result |
|---|---|
| The `src/debug` removal takes out about 61 issues, by removal | All 62 gone are in `src/debug` (I missed its one InlinedApi). **Held** |
| LogNotTimber about 259, all in main, still the largest | 259, all in `src/main`. **Held** |
| Debug total 340 to 360, a little below 376 | 349. **Held** |
| Errors about 11 | 14: UnrememberedMutableState grew from 4 to 7 with three new test files. **Missed by 3** |
| The 7 shipped-code errors and DataExtractionRules still there | **Held** |
| Release lint needs no secret | Ran to the end with none. **Held** |
| Release about the same as debug, or slightly smaller | 337, smaller only by test findings. **Held**, but for a reason I did not predict: release skips test analysis entirely, not just `src/testDebug` |
| Opt-in: nothing in the honest run; the control shows UnsafeOptInUsageError at the Extender call | **Held** (three sites, the Extender and its two chained calls) |
| A few new ids from the RAW code | One new id overall, MonochromeLauncherIcon, which is not RAW. RAW added new *sites* for existing ids: UsableSpace, UseKtx and LogNotTimber. **Partly missed** |

## For the owner: a proposed "everything reasonable" list for D4

This section is plain language. The suggestions are mine, and you decide. "Users see" is my reading of lint's message and the code, not something observed on a phone.

**The headline:** 349 findings. 259 of them are one style rule that came bundled with a logging library inside the map component, and it asks the app to log through that library. Leave that rule aside and 90 remain. Of the 14 "errors", 7 are in the app people install. They are the same 7 as in October, and none was fixed since. The other 7 are in the automated tests. The 18 developer-only errors from October are gone, because that code left the app.

| What lint found | How many | Users see? | Proposal |
|---|---|---|---|
| **Logging-library style rule** (LogNotTimber) | 259 | Nothing. These are messages only developers read | **Leave.** The app logs through Android's own logger on purpose. Switching this rule off for CI is your call (see T3) |
| **Month and zoom words that don't follow a language change** while the app is open (NonObservableLocale) | 5 | Rarely: after changing the phone's language with the app open, a month name or the photo zoom label could stay in the old language until the screen redraws | **Fix.** Small |
| **A zoom limit with an impossible default** (Range) | 1 | Nothing today: the only caller passes a real value | **Fix.** One line, so this error can't hide a real one later |
| **Code laid out so it reads as something else** (SuspiciousIndentation) | 1 | Nothing. It does what it means | **Fix.** Layout only |
| **Sizes and resources that can go stale** after rotating, folding or resizing (LocalContextResourcesRead 5 on the map; ConfigurationScreenWidthHeight 4 in the report screens and the short-window and width checks) | 9 | Possibly a mis-sized layout until the screen rebuilds. Not seen; this is lint's reasoning | **Fix, with a rotation check on the phone.** Behaviour can change, so tests first |
| **Phone-to-phone transfer not fully switched off** (DataExtractionRules) | 1 | Invisible but real for privacy. Your standing ruling (comment in `AndroidManifest.xml` above `:135`) is that backup *and* device-to-device transfer are off. Lint says that on Android 12 and later, the "no backup" switch alone does not stop device-to-device transfer, so finds could move to a new phone during setup | **Fix.** This carries out your existing ruling rather than making a new one. It's a small rules file, and needs a phone check to confirm |
| **The map's touch handling may not be announced to screen readers** (ClickableViewAccessibility) | 2 (one place, `SightingsMap.kt:1398`) | Could matter to someone using a screen reader on the map | **Your call.** A fix touches the map's touch handling, which has broken before. I'd look with a screen reader first and fix only if there is a problem |
| **Newer library and tool versions exist** (NewerVersionAvailable 8, GradleDependency 7, AndroidGradlePluginVersion 2) | 17 | Nothing until something is updated | **Leave for this release.** Updating changes what the app is built from. It's a separate decision from tidying |
| **Test-only findings** (UnrememberedMutableState 7 errors, ComposableNaming 5) | 12 | Nothing; these are in the automated tests | **Fix.** Small, and the tests must stay green |
| **Shorter ways to write the same code** (UseKtx) | 15 | Nothing | **Fix.** Mechanical swaps for the same behaviour; the build and tests confirm them |
| **How screen pieces take their settings** (ModifierParameter) | 14 | Nothing | **Fix.** A naming and ordering convention, checked by the compiler |
| **Tiny memory savings in on-screen numbers** (AutoboxingStateCreation, hints) | 9 | Nothing measurable | **Fix.** Small |
| **A check for "is this phone new enough?" that lint can't see** (InlinedApi, `PhotoExporter.kt:72`) | 1 | Nothing. The code already refuses on older phones (`check(Build.VERSION.SDK_INT >= Q)` just above) | **Your call:** leave it, or mark the line as checked. Marking it is a one-line note, but it is a way of telling lint to be quiet there, so I'm not choosing it |
| **The RAW free-space check** (UsableSpace, `RawShotSaver.kt:117`) | 1 | Rarely: RAW saving might refuse when the phone could have freed cached space to make room | **Your call.** Changing it changes when a RAW save is refused. I'd leave it: refusing a little early is the safe side |
| **Leftover icon folder name** (ObsoleteSdkInt) | 1 | Nothing | **Fix.** Rename one folder |
| **The app icon has no single-colour version** for Android's themed icons (MonochromeLauncherIcon, new) | 1 | Yes: with themed icons on, the app's icon stays in colour while the others turn single-colour | **Your call.** It needs a drawn icon, which is a design decision |

**What users would notice if all the "Fix" rows are done:** words that follow a language change, layouts that keep up after rotating, and (invisibly) finds no longer moving to a new phone. Everything else is invisible to users.

## Proposal for keeping lint on CI (T3)

These are proposals only. Switching a check off, or adding a baseline, is your decision.

1. **A lint job in `ci.yml`** shaped like this scratch job: separate from `build`, debug variant (it covers main code and tests), uploading the reports. Release lint adds nothing today, because there are no release-only sources and its main-code findings are identical. A second variant could be added if release-only code ever appears.
2. **Fail the job on errors.** With the 7 shipped-code and 7 test errors fixed, the job could fail on any error with **no baseline at all**. Lint's opt-in check showed it catches a missing camera opt-in as an Error, which nothing else catches.
3. **What a baseline would hold, if you prefer to switch lint on before fixing:** the 349 findings in this run, as a snapshot. New findings fail and old ones are tolerated. The risk is that a baseline quietly keeps the 7 shipped-code errors forever. I'd baseline only what you rule "leave", not the errors.
4. **Suggested to switch off on CI, with reasons:**
   - **LogNotTimber.** It is a rule a library brought in for its own logger, and the app deliberately doesn't use that logger. 259 findings that will never be fixed would bury real ones.
   - **NewerVersionAvailable and GradleDependency** (perhaps also AndroidGradlePluginVersion). Their answers depend on what was published online that day, not on the code. The same commit could pass today and fail tomorrow, which makes CI non-reproducible (CLAUDE.md, "Pin dependency versions … so a build is reproducible"). A version check, if you want one, is better done as a separate, scheduled report.
5. **Keep everything else on**, at lint's default severities.

## Could not determine

- **Whether any finding misbehaves on a phone.** Lint reads code, and every "users see" above is my reading.
- **Why MonochromeLauncherIcon is new.** Lint is the same version (9.3.1), the icon file and SDK levels did not change since `921aa9c7` (`git log` on `res/mipmap-anydpi-v26` is empty; compileSdk and targetSdk are 37 in both), and I did not trace further.
- **Whether lint's DataExtractionRules reasoning holds on a phone** (that device-to-device transfer still happens with `allowBackup="false"` on Android 12 and later). It is lint's explanation, not verified.
- **Release lint on test sources.** Release ran no test analysis, so test code was checked only under debug. The Gradle task list shows this; I did not look for the AGP setting that decides it.
- **The build job of control run 39032713545** was still running when this report was written. It builds the opt-in-removed commit, and it is not part of the survey.

## Files in `docs/audits/2026-10-17-lint-survey-2/`

| File | What it is |
|---|---|
| `count_lint_issues.py`, `control-report.xml`, `control-expected.json`, `run_control.sh` | The counter and its positive control, unchanged from October |
| `compare_lint_runs.py`, `compare-control-before.xml`, `compare-control-after.xml`, `compare-control-expected.json`, `run_compare_control.sh` | The comparison script and its positive control (new) |
| `mechanism-prediction.md` | Written and committed before the run |
| `lint-results-debug.xml`, `.sarif`; `lint-results-release.xml`, `.sarif` | Lint's output, as is |
| `counts-debug-run-39031757730.txt`, `counts-release-run-39031757730.txt` | The counter's tables |
| `compare-october-vs-debug.txt`, `compare-debug-vs-release.txt`, `compare-optin-control-debug-run-39032713545.txt` | The comparisons |

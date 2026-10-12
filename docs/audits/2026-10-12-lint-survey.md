# Android lint survey on CI: counts only, nothing fixed (dispatch 2026-10-12-01, RECORD -918)

Production release task T1 (`docs/plans/2026-10-11-production-release-tasks.md` on records-after-173). This survey counts lint's findings so the owner can choose what gets fixed (decision D4). **Nothing was fixed.** No baseline was added, and no check was disabled or downgraded. Lint ran only on CI. No Gradle ran on the laptop, and no phone was used.

## Facts

- **Base:** origin/main `921aa9c7` (PR #219 merged), fetched 2026-10-12 and unchanged since the dispatch was written.
- **Scratch branch:** `scratch-lint-survey` at `2fb9b012`, cut from `921aa9c7`. It adds a separate `lint` job to `.github/workflows/ci.yml` (additions only, after the unchanged `build` job). It also adds an output-only `lint {}` block to `app/build.gradle.kts`: report formats, the text report's path, and `abortOnError = false`. It is not for merging.
- **Counter:** this directory's `count_lint_issues.py`, committed with its positive control in `d9160d5a` on this branch, before the CI run started.
- **CI run:** [38207222367](https://github.com/slayer8366/Forager/actions/runs/38207222367), started with `gh workflow run ci.yml --ref scratch-lint-survey`, `workflow_dispatch`, headSha `2fb9b012`.
  - Conclusion: success.
  - Lint job (id 114673273358): success. It ran 05:16:28 to 05:21:11 UTC (4 min 43 s). The Gradle step took 3 min 52 s, including compiling the app.
  - The `build` job also ran, unchanged, and passed. Its results are not part of this survey.
- **Lint version:** `lint 9.3.1`, from the XML report's root element (`<issues format="6" by="lint 9.3.1">`), with AGP 9.3.1 (`agpVersion="9.3.1"` in lint's module model; `gradle/libs.versions.toml:2`).
- **Artifacts:** `lint-reports` (HTML, XML, SARIF, text, Gradle log) and `lint-model`. Both expire **2027-01-10T05:16:26Z**. The HTML report is left there, as the dispatch asks.

### What the run covered

- **Module and variant:** one module, `:app` (`settings.gradle.kts:18`, the only `include`), variant `debug`. Sources: `src/main` and `src/debug` (lint's variant model, `sourceProvider`). `src/release`, `src/testRelease` and release-only code were **not** analysed.
- **Test sources:** analysed. The Gradle log shows `lintAnalyzeDebugUnitTest` and `lintAnalyzeDebugAndroidTest` running, and nine issues are in `app/src/test`. `checkTestSources` was left at lint's default (off). Under that default, only checks written to look at tests run on them, so test code got a partial check, not the full one.
- **Generated code:** `checkGeneratedSources` was left at its default (off). No issue location is under `build/generated` (0 matches in the XML). Lint's model lists the generated folders (KSP, BuildConfig) but did not report on them.
- **Dependencies:** `checkDependencies` was left at its default. There are no library modules in this project, so there is no other source module it could have checked. Libraries' own **lint checks** did run, though. Lint's model lists 97 library entries carrying a `lintJar` (I did not check each jar exists). This is where the Compose checks and `LogNotTimber` come from.
- **Options lint was given:** `absolutePaths="true" checkReleaseBuilds="true" explainIssues="true"` (lint's report model, `lintOptions`). No other flags, so every other option is at lint's default. The output-only settings on the scratch branch don't show up in the model.
- **In-source suppressions already in the code:** there are 10 `@SuppressLint("MissingPermission")` annotations, in `AndroidWalkLogPlatform.kt` (debug: 156, 171, 195, 312), `AndroidLastKnownLocationSource.kt:24, 33`, `AndroidLocationProvider.kt:20, 57`, `AndroidLocationTracker.kt:63` and `SightingsMap.kt:2017`. Lint honours them, so those sites are not counted. They were already on main; this survey added none.
- **Lint-internal errors:** none found. No `LintError` (or other `Lint`-category) issue is in the XML, and the Gradle log has no exception or crash line. The log's only warnings are the build's own Kotlin and AGP deprecation messages. Among them, AGP says the report-format switches I set are deprecated because "Lint reports are now always generated". The setting was redundant but harmless.

## Counts

### Three-way cross-check of the total

| Source | Total |
|---|---|
| `count_lint_issues.py` on the XML | **376** |
| Plain count of `<issue>` elements in the XML (`grep -oE '<issue([[:space:]]|>|$)' … \| wc -l`) | **376** |
| Lint's own summary: text report's last line "29 errors, 338 warnings, 9 hints"; HTML header "Lint Report: 29 errors, 338 warnings and 9 hints" | **376** |

All three agree. The Gradle log has no count line of its own, because with `abortOnError = false` lint has nothing to abort on, so lint's own figure is taken from its text and HTML reports. The XML has 377 locations, because one issue (`ClickableViewAccessibility`) carries two.

### By severity

| Severity (lint's value) | Issues |
|---|---|
| Error | 29 |
| Warning | 338 |
| Hint | 9 |
| **Total** | **376** |

"Hint" is the value lint wrote in the XML. Its own summary calls these "hints". No Fatal or Information issue appears.

### By category (lint's own `category`, not regrouped)

| Category | Total | Error | Warning | Hint |
|---|---|---|---|---|
| Correctness:Messages | 272 | 0 | 272 | 0 |
| Correctness | 76 | 29 | 47 | 0 |
| Productivity | 13 | 0 | 13 | 0 |
| Performance | 12 | 0 | 3 | 9 |
| Accessibility | 2 | 0 | 2 | 0 |
| Security | 1 | 0 | 1 | 0 |
| **Total** | **376** | **29** | **338** | **9** |

### Every error-severity id

Paths are repo-relative. Line numbers are as lint reported them at `2fb9b012`, which has the same source as `921aa9c7`; the scratch commit touched only ci.yml and the build file.

| Id | Count | Where (source set) | Examples |
|---|---|---|---|
| NewApi | 18 | all `src/debug` | `app/src/debug/java/com/zynergylabs/forager/app/diagnostics/walklog/AndroidWalkLogPlatform.kt:104`, `:105`, `:106` |
| NonObservableLocale | 5 | `src/main` | `app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityResultsUi.kt:189`; `…/ui/availability/AvailabilitySearchUi.kt:885`, `:1084` |
| UnrememberedMutableState | 4 | all `src/test` | `app/src/test/java/com/zynergylabs/forager/app/ui/map/JournalEntriesChipTest.kt:100`; `…/ui/availability/NavigationHudButtonPlacementTest.kt:73`; `…/ui/availability/NavigationHudKindInStatusTest.kt:158` |
| Range | 1 | `src/main` | `app/src/main/java/com/zynergylabs/forager/app/ui/map/NavigationView.kt:539` |
| SuspiciousIndentation | 1 | `src/main` | `app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityMapControlsUi.kt:627` |

That is 7 errors in code that ships, 18 in debug-only diagnostics and 4 in tests.

What I read at each site (read, not tested):
- **NewApi:** the calls at `AndroidWalkLogPlatform.kt:104-110` sit inside a `put(name, minSdk)` helper. The helper runs them only when `Build.VERSION.SDK_INT >= minSdk` (line 102). Lint cannot see a guard passed through a lambda, so these look like false alarms in debug-only code.
- **Range:** `leaveNavigation(map, maxZoom: Double = Double.MAX_VALUE)` (`NavigationView.kt:518`) passes `maxZoom` to `setMaxZoomPreference` (line 539). The only production caller passes a real value (`SightingsMap.kt:1226`, `navigationMaxZoom(...)`), so the out-of-range default is never used in production as far as `grep` shows.
- **SuspiciousIndentation:** an `if (...) X else` with the else branch starting on the next line at the same indent (`AvailabilityMapControlsUi.kt:626-627`). The code does what it appears to mean. Lint is flagging how it reads, not what it does.
- **NonObservableLocale:** `Locale.getDefault()` read inside a composable, e.g. month names (`AvailabilityResultsUi.kt:189`).

### The 15 largest warning ids

There are exactly 15 warning ids, so this list is all of them.

| # | Id | Count | Category | Example |
|---|---|---|---|---|
| 1 | LogNotTimber | 272 | Correctness:Messages | `app/src/main/java/com/zynergylabs/forager/app/alert/AndroidAlertAudibility.kt:34` (231 in main, 41 in debug) |
| 2 | ModifierParameter | 14 | Correctness | `app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityCompactMapUi.kt:436` |
| 3 | UseKtx | 13 | Productivity | `app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityPureFunctions.kt:153` |
| 4 | NewerVersionAvailable | 8 | Correctness | `gradle/libs.versions.toml:7` |
| 5 | GradleDependency | 7 | Correctness | `gradle/libs.versions.toml:8` |
| 6 | ComposableNaming | 5 | Correctness | `app/src/test/java/com/zynergylabs/forager/app/ui/availability/AvailabilityScreenGpxImportTest.kt:112` (all 5 in tests) |
| 7 | LocalContextResourcesRead | 5 | Correctness | `app/src/main/java/com/zynergylabs/forager/app/ui/map/SightingsMap.kt:647` |
| 8 | ConfigurationScreenWidthHeight | 4 | Correctness | `app/src/main/java/com/zynergylabs/forager/app/ui/log/CartographyEntryReportScreen.kt:397` |
| 9 | AndroidGradlePluginVersion | 2 | Correctness | `gradle/libs.versions.toml:2` |
| 10 | ClickableViewAccessibility | 2 | Accessibility | `app/src/main/java/com/zynergylabs/forager/app/ui/map/SightingsMap.kt:1380` |
| 11 | InlinedApi | 2 | Correctness | `app/src/main/java/com/zynergylabs/forager/app/photo/PhotoExporter.kt:72` |
| 12 | DataExtractionRules | 1 | Security | `app/src/main/AndroidManifest.xml:134` |
| 13 | ObsoleteSdkInt | 1 | Performance | `app/src/main/res/mipmap-anydpi-v26` |
| 14 | StaticFieldLeak | 1 | Performance | `app/src/debug/java/com/zynergylabs/forager/app/diagnostics/WalkLogger.kt:197` (debug only) |
| 15 | UsableSpace | 1 | Performance | `app/src/debug/java/com/zynergylabs/forager/app/diagnostics/WalkLogger.kt:120` (debug only) |

The 9 hints are all `AutoboxingStateCreation` (Performance), e.g. `app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityCompactMapUi.kt:208`. The full per-id list with up to three examples each is in `2026-10-12-lint-survey/counts-run-38207222367.txt`.

## For the owner

This section is in plain language. The grouping and the suggestions are mine. You decide.

**The headline:** 376 findings, but one kind makes up 272 of them. It's a style rule that came bundled with a logging library the map component brings in, and it asks the app to log messages through that library. The app logs through Android's own logger on purpose, and nobody ever sees these messages. Leave that one aside and 104 findings remain. Of the 29 "errors", only 7 are in code that ships to users. Each of those 7 looks, on reading, like something that doesn't misbehave today.

By lint's own categories:

- **Correctness: Messages (272 warnings).** All the logging-library rule above. **Users see nothing.** *Suggestion: leave, and switch that one rule off when lint goes onto CI (T3), with the reason written down.*
- **Correctness (29 errors, 47 warnings).** This is a mix, so here it is in parts:
  - *Calls to newer phone features (NewApi 18 errors, InlinedApi 2):* lint can't see the app's own "is this phone new enough?" checks. The 18 errors are all in the developer-only walk log, which isn't in the app users install. **Users see nothing.** *Suggestion: fix later, or mark them as checked.*
  - *Month names that don't follow a language change while the app is open (NonObservableLocale, 5 errors):* if someone changes their phone's language with the app open, a few words such as month names could stay in the old language until the screen redraws. **Users could see this, rarely.** *Suggestion: fix now; small.*
  - *A zoom value out of range (Range, 1 error):* a default value that is never used by the app today. **Users see nothing.** *Suggestion: fix now; one line, so the warning can't hide a real one later.*
  - *Code that reads as if it does something else (SuspiciousIndentation, 1 error):* layout of the code, not behaviour. **Users see nothing.** *Suggestion: fix now; it costs nothing and avoids a misreading.*
  - *Test-only findings (UnrememberedMutableState 4 errors, ComposableNaming 5):* in the automated tests, not the app. **Users see nothing.** *Suggestion: fix later.*
  - *Screen size and resources read in a way that can go stale (LocalContextResourcesRead 5, ConfigurationScreenWidthHeight 4):* after rotating, folding or resizing the window, these spots could use the old size or old values until the screen is rebuilt. **Users could see a mis-sized layout in those moments** (not observed; lint's reasoning). *Suggestion: fix now, with a device check on rotation.*
  - *Newer versions of libraries and tools exist (NewerVersionAvailable 8, GradleDependency 7, AndroidGradlePluginVersion 2):* updating them changes what the app is built from, which can change behaviour. **Users see nothing until something is updated.** *Suggestion: leave for this release; updating is a separate decision from tidying.*
  - *How code is arranged (ModifierParameter 14):* a convention for how screen pieces take their settings. **Users see nothing.** *Suggestion: fix later.*
- **Productivity (13 warnings, UseKtx).** Shorter ways to write the same code. **Users see nothing.** *Suggestion: leave.*
- **Performance (3 warnings, 9 hints).** The 9 hints are tiny memory savings in on-screen numbers. Two warnings are in the developer-only walk log. One is a leftover folder name for the app icon. **Users see nothing measurable.** *Suggestion: fix the hints later; leave the rest.*
- **Accessibility (2 warnings, ClickableViewAccessibility).** The map listens for touches in a way that screen readers and other assistive tools may not be told about. **Could matter to people using a screen reader on the map.** I haven't checked this with one. *Suggestion: look at it now; fix only if a screen-reader check shows a problem.*
- **Security (1 warning, DataExtractionRules).** The app already says "don't back me up" (`AndroidManifest.xml:134`). Lint's explanation says that on Android 12 and later, that setting no longer covers everything, such as moving data to a new phone. If so, the private records (find locations) could travel in a device-to-device transfer the app meant to exclude. **Affects users' privacy, invisibly.** I have not verified this on a phone. *Suggestion: fix now. It's an added rules file, and a device check would confirm it.*

What users would notice if fixed, in short: the language-change words, the rotate/resize staleness, possibly screen-reader behaviour on the map, and (invisibly) what moves to a new phone. Everything else is invisible to users.

## Could not determine

- **Whether any finding misbehaves on a phone.** Lint reads code. Each "users see/don't see" above is my reading of lint's message and the code at the cited line, not an observation.
- **Release-only code** (`src/release`, `src/testRelease`) was not analysed, because the survey ran the debug variant as dispatched. A release-variant lint might add findings there.
- **Full checks on test code.** Tests got only the checks written for them (`checkTestSources` off by default), so these counts understate what a full check of tests would report.
- **Which library brings in the logging library.** It is in the build as a transitive dependency (listed in lint's dependency model, `debug-artifact-dependencies.xml` in the `lint-model` artifact), and the app does not declare it (`gradle/libs.versions.toml` and `app/build.gradle.kts` have no match). I did not trace which library pulls it in.
- **Whether every one of the 97 `lintJar` entries exists and loaded.** I relied on lint's own model, plus the absence of lint-internal errors.
- **The lint model for test sources.** My upload path `app/build/intermediates/lint_model/` matched nothing (the job warned and carried on). Only the report model arrived, so test-source coverage is read from the Gradle log's task list and the issue paths, not from a model file.

## Files

In `docs/audits/2026-10-12-lint-survey/`:

- `count_lint_issues.py`: the counter.
- `control-report.xml`, `control-expected.json`, `run_control.sh`: the positive control. Run on this machine before the CI run (committed in `d9160d5a`), all three checks passed:
  - check 1: the counts equal the hand-written expected ones (5 issues; 2 Error and 3 Warning; Correctness 3 and Performance 2; 7 locations; one issue with three locations);
  - check 2: the plain `<issue>` count is 5, where a naive `grep -o '<issue'` gives 7 on the same file;
  - check 3: a copy with one Warning changed to Error is detected as different, so the comparison can fail.
- `lint-results-debug.xml` (366 KB) and `lint-results-debug.sarif` (1.8 MB): lint's output, as is (both under 5 MB). SHA-256 matches the downloaded artifact.
- `counts-run-38207222367.txt`: the counter's tables on that XML.

The HTML report stays in the CI artifact `lint-reports` until 2027-01-10.

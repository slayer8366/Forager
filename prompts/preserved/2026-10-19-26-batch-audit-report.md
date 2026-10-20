# Elder Ford's audit: main 19aa1058 → pre-main 1ce100c4 (dispatch 2026-10-19-26, RECORD -1433)

Filed by the planner as received (Forager RECORD -1440). Elder Ford, dispatched on Fable, read-only.

Dispatched on Fable; I have not read the model I ran on. Read-only throughout: no file written to any repository, nothing pushed, no Gradle, no emulator. Scratch under `/mnt/work/scratch/elder-batch/`, now empty.

## Verdict: PASS WITH ITEMS

Nothing found that a user would meet. Every item below is a record or documentation item for the planner; none risks records, safety or trust. The planner's prediction in -1433 ("pass with items, no block") held.

## Mechanism prediction (written before reading the diff)

A range of pure file moves plus small deletions; if it failed, it would fail in (a) a #270 "warning fix" that quietly changed a null path, (b) "dead" code with a live reader, (c) a moved top-level `val` whose initialiser reads another file's `val` (init-order), or (d) a `private`→`internal` widening colliding with a same-package name. All four were checked; none bit.

## Baseline, as I confirmed it (run)

- `git ls-remote`: main `19aa1058`, pre-main `1ce100c4`, records-after-173 `216ed1cd`.
- `git log --first-parent origin/main..origin/pre-main`: 24 merge commits, #269 to #292 (#271, #272, #275–#291 = 19 split PRs; #269, #270, #273, #274, #292 non-split). `git diff --stat`: 108 files, +10,909/−9,960. `.github/`, `AndroidManifest.xml`, `res/`, `build.gradle.kts`, `gradle/`, `scripts/`: no change.
- `git rev-list --count 19aa1058` = 3500, matching -1367 (3500 is 1.0.3500+g19aa1058).
- CLAUDE.md on pre-main is byte-identical to the copy I was given (empty diff).
- **One premise off:** the dispatch and -1433 say "Tests: one file changed, +7". Two test files changed: `TabletJournalLayoutTest.kt` (+7, #273) and `testDebug/.../AvailabilityScreenSettingsPanelTest.kt` (one header-comment line, #274, disclosed in #274's body). No test code or assertion changed in the second. Item 6 below.

## 1. Behaviour unchanged: the split PRs (run, my own method)

**Method.** For each of the 24 merges, I took every `-`/`+` line of `git diff M^1 M -- app/src/main`, dropped blank, `package` and `import` lines, mapped `private `→`internal ` on both sides, sorted (LC_ALL=C) and compared the two multisets with `comm`. This is independent of the coders' zebra-diff/cmp proofs and stricter on whitespace (no indentation allowance).

**Result, every one of the 19 split PRs:** the only lines in one multiset and not the other are the new files' header comments (`// Moved unchanged from X.kt (dispatch …)`) and, in #286/#289/#283, a one-line note in the kept file's header. Zero code lines differ. Per PR: #271 432/436, #272 399/399, #275 448/452, #276 766/766, #277 331/334, #278 373/373, #279 763/769, #280 439/439, #281 405/409, #282 504/508, #283 324/328, #284 786/791, #285 237/240, #286 785/792, #287 316/318, #288 558/558, #289 186/189, #290 355/361, #291 248/255 (removed/added after filtering; the surplus is header lines, listed and read).

**Order (run).** For every kept (modified, not new) file in every split PR, I counted `+` lines that are not blank/import/package/comment. All zero except the widening lines the PR bodies declare: #279 `CompassElevationStripContent`, `stripHeadingText` (kept in `AvailabilityMapControlsUi.kt`); #272 `SIGHTINGS_MAP_TAG`. So the kept files — which hold the Maps tab body (`CompactMapTab`), the map body (`SightingsMap`), the scaffold and the camera dialog — had only deletions: the composition order of what remains over the map is untouched. Order *within* each moved range is covered by the coders' `cmp` proofs (read, not re-run); my multiset check would not see a permutation inside a range, and the identical 5,160-case suite is the other guard.

**Visibility (run).** Whole range, lines starting with a modifier: 102 changed-modifier pairs, every one `private`→`internal`; zero narrowings; zero new declarations; the only removed declarations with no twin are the seven dead ones (#274: `MapModePicker`, `MapFloatingIconButton`, `MAP_MODE_PICKER_TAG`, `navigationChromeTransitions`, `ReportItem`, `ReportItemsSection`; #292: `LANDSCAPE_HUD_MAX_WIDTH`).

Specific checks asked for:
- **9a/9b/9c (#281, #282, #287), 8 (#279), 10b (#285):** covered above; kept `AvailabilityCompactMapUi.kt`, `AvailabilityMapControlsUi.kt`, `AvailabilityMapIconCluster.kt` deletions-only.
- **7 (#284, MapChrome):** `MAP_CHROME_OVER_MAP_ALPHA = 0.8f` stays at `ui/map/MapChrome.kt:69` on pre-main, so CLAUDE.md's pointer is still right (run).
- **10f (#289, camera):** orientation/rotation lines net zero in the PR diff; `InAppCameraDialog.kt` holds the same single `requestedOrientation`/`SCREEN_ORIENTATION` reference at main and pre-main (run). The camera's fixed layout is not flagged (owner's exception).
- **10e (#290, Journal):** `BackHandler`/`onBack`/`popBackStack` lines net zero; `BackHandler` counts on pre-main: JournalTab.kt 9, CartographyScreen.kt 9, CartographyEntryReportScreen.kt 3, all in the kept files (run). Back order not walked on a phone.

## 2. What the suite can't see

- **Insets (run):** across the whole range, 90 non-import lines mentioning `inset` (any case) removed and 90 added, multisets identical; the narrower `WindowInsets|safeDrawing|systemBars|navigationBars|statusBars|displayCutout` set is 1/1 identical. No inset code changed, so Robolectric's zero insets hide nothing new. Not seen on a phone by me.
- **Start-up order across new files (run, read):** top-level non-literal initialisers in the new files that read another file: `MapIconBarHandles.kt:169,189` → `MapIconBarSizes.kt` (`MIN_TOUCH_TARGET`, `MAP_ICON_BAR_EDGE_INSET`); `AddActionTile.kt:186,192` → `MapIconBarSizes.kt` (`mapIconBarRowAnchorOffset`, `MAP_ICON_BAR_LANDSCAPE_ROW_SPACING`); `OfflineMapsSideBySide.kt:131` → `CartographyEntryReportLayout.kt:66`. `MapIconBarSizes.kt`'s own top-level values read only literals, `Spacing` (theme) and themselves; `CartographyEntryReportLayout.kt:66` is a literal. No cycle; each facade initialises on first touch. The `compositionLocalOf`/`staticCompositionLocalOf`/`Saver`/`SemanticsPropertyKey` values take lambdas or strings. -1407's "no init-order cycle" holds.
- **Touch routing:** no coordinate-touch test changed (no test files in the split PRs, run); the plan's 19 coordinate-touch guards ran identically per the PR bodies (read). Pure moves keep call order (shown above). Not seen on a phone by me.
- **The phone's loader (A1):** the record carries an S22 launch-check PASS line in every split PR's terminal/merge entry (22 of 25 terminal/merge entries in -1375…-1432 mention S22; the three without are -1382, -1383 (#270), -1384, all before the first split), plus pre-main `014fc599`'s own build after the batch (-1430: run 39364408315, 1.0.3585+g014fc599) and run 39374475066 for `a71a6c58` (-1432). **`a71a6c58`'s parent is `263c21f4` and `git diff a71a6c58 1ce100c4` is empty (run)**, so that last S22 check was on the exact tree being promoted. All S22 results are **read from the record, not seen by me.**

## 3. The non-split PRs

- **#269 (privacy policy, docs, `[skip ci]`), A7 (run):** `git grep` over `app/src/main` on pre-main finds no `nationalmap`, `demotiles` or `glyphs` URL (only comments; `BasemapStyles.kt:18-26` records "No style declares `glyphs`"); no URLs in `assets/` or `res/` beyond licence texts. Every request host in the sources (`api.inaturalist.org`, `api.open-meteo.com`, `archive-api.open-meteo.com`, `tile.openstreetmap.org`, `a.tile.opentopomap.org`, `forager-pmtiles.brandonlee1-894.workers.dev`) is in the policy's table. The other hosts in the sources are browser links (credits, `www.inaturalist.org/observations/…` at `AvailabilityPureFunctions.kt:214`), a GPX namespace and the User-Agent contact, not requests the app makes. The website copy (zynergy-site `origin/main` df15222, `privacy/index.html:48`) carries the same header and no stale hosts (run). Recorded in -1364/-1365, not in -1375+ (the dispatch's "entries -1375 to -1432" starts after it; fine).
- **#270 (release clean-up) (run, read):** nine code lines changed, all read against their types at `0d4bae4e^1`: `onAddEntry!!`→`onAddEntry` is inside `showNewFind = onAddEntry != null && …` (`FindsGalleryScreen.kt:153,216`); `cameraRequest!!.frame`/`cameraRequest?.id` are under `frameWanted = cameraRequest != null && …` (`SightingsMap.kt:986-990`); `current?.id` is under `closingUnsavedEdit = current != null && …` (`CartographyViewModel.kt:294-296`); `if (glides && from != null)`→`if (glides)` where `glides = … && from != null` (`AvailabilityMapIconCluster.kt:33,43` of the hunk); `queue.poll()!!` after `isNotEmpty()` on a local queue (`TrackSelfJoin.kt:125-126`). All compile only because the condition already guarantees non-null, so behaviour is identical. `contentPadding`→`contentPaddingWithoutLabel` and `LocalLifecycleOwner`'s import: the coder's bytecode reading is **inferred** by me (not re-read). The planner's -1375/-1376 left the six `launch(Job)`, two `MenuBook` and two sheet-state warnings alone, as the diff shows. The completion report's revert checks R1–R3 each name a failure only their own edit could produce (read; runner slip disclosed in the report).
- **#273 (List content check) (run, read):** `LIST_CONTENT_TAG` first in `ListTab`'s modifier chain (`AvailabilityResultsUi.kt:90-92`); the test reads List's bounds before the panel opens and asserts last (`TabletJournalLayoutTest.kt:457, 490-493`). Revert check: the first attempt (run 39326494053) is honestly reported as showing nothing; the second (39328396841) fails only the two List tests with the predicted message. Item 2: `pendingAction` read once into a local; the `when` over the enum is exhaustive without `null`; same value on the main thread. Behaviour identical.
- **#274 (dead UI code) (run):** at main, `git grep -w` finds each removed name only at its declaration, in imports, and in comments; at pre-main only in comments. No `::Name`, no `@Preview`, no resource or test-tag string. Two comment mentions kept the bare name without the "(removed …)" note (`Theme.kt:168`, `AvailabilityMapOverlaysUi.kt:332`); cosmetic, item 5.
- **#292 (`LANDSCAPE_HUD_MAX_WIDTH`) (run):** declaration and one KDoc link at main; only the dated note at pre-main (`LandscapeChromeWidth.kt:15`). No user change.

## 4. CI (run)

- **Push run 39376276205 at `1ce100c4`**, `event=push`, `headBranch=pre-main`, started 05:41:55Z, completed 05:57:31Z, **success**; jobs: "Build, test, publish APK" success, "Release compiles and packages (unsigned)" success, "Android lint (debug)" success. Build log: 0 `e:` compile lines; `testDebugUnitTest` ran ("BUILD SUCCESSFUL in 12m 6s"). **JUnit artifact `unit-test-report` (id 12244246495), counted by me from the XML: 691 suites, 5,160 tests, 0 failures, 0 errors, 24 skipped** — the same totals as the baseline the PRs cite. Downloaded alone to scratch, read, deleted.
- `.github/` unchanged in the range, so the skip allowlist did not grow.
- **Known-bugs row 2 sighting (run):** run 39372145004 (`workflow_dispatch`, `a71a6c58`) failed `MushroomLogViewModelWiringTest > a find deleted through the Journal's ViewModel leaves the Maps tab's records` with `AssertionError at MushroomLogViewModelWiringTest.kt:80`; "5160 tests completed, 1 failed, 24 skipped". The re-run 39374475066 at the same SHA is reported green in #292's body (read). The test was not touched in the range (run: no test files in #292).

## 5. The record (read)

- Every one of the 24 merge commits' hashes appears in RECORD -1364…-1433 on records-after-173; the ten "merged into pre-main at X" hashes my regex caught all match git.
- **Duplicate -1421 confirmed:** `uniq -d` on the ID lines returns exactly `2026-09-28-1421`; -1422 renumbers the 03:09:28Z decision to -1423 without editing either.
- **-1429 miscount confirmed:** -1430 corrects "seven files" to six.
- -1433's prediction, scope and baseline match what I found except the test-file count (item 6).

## Items (none a user meets)

1. **Known-bugs "Where" column is stale since 3500 shipped** (`docs/process/known-bugs.md`, unchanged in this range). Rows 4, 5, 6, 8, 9 read "New" (only on pre-main), but main `19aa1058` is 3500 (-1367) and -1369 says row 6's work "shipped in 3500". Those bugs are now Live. A6 reads this list at each promotion. Inferred from -1367/-1369 and the empty diff of the file; no code read for the rows. For the planner.
2. **No new known bug from this range.** Nothing in the range changes a user-visible behaviour, so the promotion ships with the list as it stands (rows 2–9), once item 1 is corrected. Read.
3. **The #270 completion report has no `docs/audits/README.md` row** (`docs/audits/2026-10-19-tablet-search-bar-and-columns-completion-report.md` added; README untouched by the dispatch's own instruction). CLAUDE.md names the README as the index every dispatch appends to. For the planner; documentation only.
4. **S22 launch checks are the record's word, not mine.** I could not run or witness any; the equality of `a71a6c58` and `1ce100c4` trees (run) is the one thing that makes the last recorded PASS speak for the promoted tree.
5. **Two comment mentions of removed names lack the "(removed …)" note** (`ui/theme/Theme.kt:168`, `ui/availability/AvailabilityMapOverlaysUi.kt:332`). Cosmetic; read.
6. **Dispatch/-1433 premise: "one test file changed, +7"; it is two** (the second a one-line comment in `AvailabilityScreenSettingsPanelTest.kt:76`, from #274). Run; no effect on the verdict.
7. **Bytecode-level claims in #270 taken on the coder's reading** (`contentPaddingWithoutLabel` identical to the deprecated `contentPadding`; `LocalLifecycleOwner` the same object). Inferred, not re-read.

## Not checked

- Nothing was seen on a phone, a tablet or an emulator by me: launch, insets against real system bars, a finger reaching the map through the moved chrome, the camera's fixed layout, Back on the Journal screens, legibility, screen reader, large font (Appendix B), interruption (A4). No screen changed, so Appendix B had no new surface to measure, but nothing here is observed.
- The dex register budget script's output (coders' claim per PR): not re-run (no Gradle).
- Order of lines *inside* each moved range: relies on the coders' `cmp` proofs plus the identical suite, not on my multiset check.
- The upgrade check (A2): not applicable (no storage change in the range; `ForagerDatabase.kt` not in the diff).
- Battery (A8): no change that keeps a sensor or screen on longer; not measured.
- The RECORD entries' internal consistency beyond the hashes, the duplicate and the miscount (timestamps against the clock, every Continues/Closes link): not audited.
- The site repo's other privacy-policy differences from the repo copy: out of scope (owner deferred them, -1365).
